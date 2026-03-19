package com.hanabank.nbc.mis.batch.framework.scheduler;

import com.hanabank.nbc.mis.batch.framework.core.BatchJob;
import com.hanabank.nbc.mis.batch.framework.core.BatchJobExecutor;
import com.hanabank.nbc.mis.batch.framework.core.BatchJobResult;
import com.hanabank.nbc.mis.batch.framework.mapper.BatchScheduleMapper;
import com.hanabank.nbc.mis.batch.framework.vo.BatchScheduleVo;
import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchAppVo;
import com.hanabank.nbc.mis.batch.framework.vo.WasInstanceIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * WAS 기동 시 배치 스케줄을 DB에서 로드하여 동적으로 등록하는 관리자.
 *
 * <p>실행 순서 (ApplicationRunner.run()):
 * <ol>
 *   <li>DB에서 현재 WAS 인스턴스 담당 배치 목록 조회 (JOIN 쿼리)</li>
 *   <li>각 배치에 대해 Spring Bean(BatchJob 구현체) 조회</li>
 *   <li>Cron 표현식으로 ThreadPoolTaskScheduler에 동적 등록</li>
 * </ol>
 *
 * <p>Bean 매핑 규칙:
 * FWK_BATCH_APP.BATCH_APP_FILE_NAME = Spring Bean 클래스 단순명(simple name).
 * 예: "DailyReportBatchJob" → Spring Container에서 해당 빈을 찾음.
 */
@Component
public class BatchSchedulerManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerManager.class);

    private final BatchScheduleMapper scheduleMapper;
    private final BatchJobExecutor executor;
    private final WasInstanceIdentity wasIdentity;
    private final ThreadPoolTaskScheduler taskScheduler;

    /** Spring Context의 모든 BatchJob 빈 (Key: 클래스 단순명, Value: BatchJob 인스턴스) */
    private final Map<String, BatchJob> jobMap;

    public BatchSchedulerManager(BatchScheduleMapper scheduleMapper,
                                 BatchJobExecutor executor,
                                 WasInstanceIdentity wasIdentity,
                                 ThreadPoolTaskScheduler taskScheduler,
                                 Map<String, BatchJob> jobMap) {
        this.scheduleMapper = scheduleMapper;
        this.executor       = executor;
        this.wasIdentity    = wasIdentity;
        this.taskScheduler  = taskScheduler;
        this.jobMap         = jobMap;
    }

    @Override
    public void run(ApplicationArguments args) {
        String instanceId = wasIdentity.getInstanceId();

        log.info("┌═════════════════════════════════════════════════════════════");
        log.info("║ [BatchSchedulerManager] 배치 스케줄 로딩 시작");
        log.info("║  WAS 인스턴스: {}", instanceId);
        log.info("╠═════════════════════════════════════════════════════════════");

        // ── DB에서 담당 배치 목록 조회 ─────────────────────────────────
        List<BatchScheduleVo> schedules;
        try {
            schedules = scheduleMapper.selectSchedulesForInstance(instanceId);
        } catch (Exception e) {
            log.error("║ [BatchSchedulerManager] DB 조회 실패! 배치 스케줄 등록 불가", e);
            log.error("╚═════════════════════════════════════════════════════════════");
            return;
        }

        if (schedules.isEmpty()) {
            log.warn("║ [BatchSchedulerManager] 등록된 배치 없음 (instanceId={})", instanceId);
            log.info("╚═════════════════════════════════════════════════════════════");
            return;
        }

        log.info("║ DB 조회 완료 — 총 {}건", schedules.size());
        log.info("╠─────────────────────────────────────────────────────────────");

        // ── 배치 목록 출력 및 스케줄 등록 ─────────────────────────────
        int registered = 0;
        int skipped    = 0;

        for (int i = 0; i < schedules.size(); i++) {
            BatchScheduleVo schedule = schedules.get(i);
            String no = String.format("[%d/%d]", i + 1, schedules.size());

            log.info("║ {} BATCH_APP_ID    : {}", no, schedule.getBatchAppId());
            log.info("║    배치명          : {}", schedule.getBatchAppName());
            log.info("║    클래스명         : {}", schedule.getBatchAppFileName());
            log.info("║    Cron           : {}", schedule.getCronText());
            log.info("║    선행배치        : {}",
                    schedule.getPreBatchAppId() != null ? schedule.getPreBatchAppId() : "(없음)");
            log.info("║    중요도          : {}", schedule.getImportantType());
            log.info("║    재시도허용       : {}", schedule.getRetryableYn());

            // BatchJob Spring Bean 조회 (클래스 단순명으로 매핑)
            BatchJob job = findBatchJob(schedule.getBatchAppFileName());

            if (job == null) {
                log.warn("║    ✗ Spring Bean을 찾을 수 없어 스케줄 등록 SKIP " +
                        "(BATCH_APP_FILE_NAME='{}' 에 해당하는 Bean 없음)",
                        schedule.getBatchAppFileName());
                log.info("║─────────────────────────────────────────────────────────────");
                skipped++;
                continue;
            }

            // Cron 유효성 검사 + 등록
            if (registerSchedule(job, schedule)) {
                log.info("║    ✓ 스케줄 등록 완료 (Bean: {})", job.getClass().getSimpleName());
                registered++;
            } else {
                log.warn("║    ✗ 스케줄 등록 실패 (Cron 오류 또는 등록 예외)");
                skipped++;
            }
            log.info("║─────────────────────────────────────────────────────────────");
        }

        log.info("╠═════════════════════════════════════════════════════════════");
        log.info("║ [BatchSchedulerManager] 스케줄 등록 완료");
        log.info("║  등록 성공: {}건 | 등록 실패/스킵: {}건", registered, skipped);
        log.info("╚═════════════════════════════════════════════════════════════");
    }

    // =========================================================
    // 내부 헬퍼
    // =========================================================

    /**
     * BATCH_APP_FILE_NAME으로 Spring Bean 조회.
     * jobMap의 Key는 Spring이 관리하는 Bean 이름 (camelCase 단순명).
     */
    private BatchJob findBatchJob(String batchAppFileName) {
        if (batchAppFileName == null) return null;

        // 직접 클래스명 매핑 시도
        BatchJob job = jobMap.get(batchAppFileName);

        // camelCase 첫 글자 소문자 변환으로 재시도 (Spring 기본 Bean 이름 규칙)
        if (job == null && batchAppFileName.length() > 0) {
            String camelKey = Character.toLowerCase(batchAppFileName.charAt(0))
                    + batchAppFileName.substring(1);
            job = jobMap.get(camelKey);
        }

        return job;
    }

    /**
     * 배치 태스크를 Cron 트리거로 TaskScheduler에 등록한다.
     */
    private boolean registerSchedule(BatchJob job, BatchScheduleVo schedule) {
        try {
            CronTrigger trigger = new CronTrigger(schedule.getCronText());
            FwkBatchAppVo appVo = schedule.toAppVo();

            Runnable task = createBatchTask(job, appVo, schedule.getBatchAppId());
            taskScheduler.schedule(task, trigger);
            return true;
        } catch (IllegalArgumentException e) {
            log.error("║    Cron 표현식 오류: '{}' — {}", schedule.getCronText(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("║    스케줄 등록 중 예외: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 배치 실행 Runnable 생성.
     * Layer 3: executor 외부에서 발생하는 Throwable도 포착하여 스레드 보호.
     */
    private Runnable createBatchTask(BatchJob job, FwkBatchAppVo appVo, String batchAppId) {
        return () -> {
            log.info("[{}][SCHEDULER] Cron 트리거 발동 — 배치 실행 시작", batchAppId);
            try {
                BatchJobResult result = executor.execute(job, appVo, Collections.emptyMap());
                log.info("[{}][SCHEDULER] 배치 실행 완료 — 결과: {}", batchAppId, result.getResRtCode());
            } catch (Throwable t) {
                // Layer 3: executor 외부 Throwable 포착 (극히 이례적)
                log.error("[{}][SCHEDULER][Layer3] executor 외부 예외 발생! {}",
                        batchAppId, t.getMessage(), t);
                // Layer 4 (ErrorHandler)는 이 예외를 잡아 스케줄러 스레드를 보호
                throw t;
            }
        };
    }
}
