package com.hanabank.nbc.mis.batch.demo;

import com.hanabank.nbc.mis.batch.framework.core.AbstractBatchJob;
import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * [데모 #7] 처리 시간이 긴 배치 — 상세 진행 로그 + 소요 시간 모니터링.
 *
 * <p>시나리오:
 * <ul>
 *   <li>init() : 대용량 파일 파싱 시뮬레이션 (2초)</li>
 *   <li>executeBatch() : 10,000건을 1,000건 단위로 처리 (총 10초)</li>
 *   <li>1,000건마다 소요 시간 / 예상 잔여 시간 로그 출력</li>
 *   <li>결과 : SUCCESS | 대상=10,000, 실행=10,000, 성공=10,000, 실패=0</li>
 * </ul>
 *
 * <p>상세 로그 패턴:
 * <ul>
 *   <li>처리 진행률 (%)</li>
 *   <li>현재 처리 속도 (건/초)</li>
 *   <li>예상 잔여 시간</li>
 * </ul>
 *
 * <p>DB 등록 예시:
 * <pre>
 *   BATCH_APP_ID     = BATCH_SLOW_PROCESS
 *   BATCH_APP_FILE_NAME = SlowProcessBatchJob
 *   CRON_TEXT        = 0 0 0 * * ?   (매일 자정)
 *   PROPERTIES       = {"totalRecords":"10000","msPerRecord":"1"}
 * </pre>
 */
@Component
public class SlowProcessBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID = "BATCH_SLOW_PROCESS";
    private static final int    LOG_INTERVAL = 1000;

    private int totalRecords;
    private long msPerRecord;   // 레코드당 처리 시간(ms) — 시뮬레이션

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 대용량 처리 배치 초기화", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        log.info("[{}][INIT]   params        : {}", BATCH_APP_ID, context.getParams());

        totalRecords = Integer.parseInt(context.getParam("totalRecords", "10000"));
        msPerRecord  = Long.parseLong(context.getParam("msPerRecord", "1"));

        log.info("[{}][INIT]   totalRecords  : {}건", BATCH_APP_ID, totalRecords);
        log.info("[{}][INIT]   msPerRecord   : {}ms (레코드당 처리 시간)", BATCH_APP_ID, msPerRecord);
        log.info("[{}][INIT]   예상 소요 시간: {}초", BATCH_APP_ID, (totalRecords * msPerRecord) / 1000);

        // 대용량 파일 파싱 시뮬레이션
        log.info("[{}][INIT] 처리 대상 파일 파싱 중... (실제: CSV/Excel 파일 로딩)", BATCH_APP_ID);
        simulateDelay(500);
        log.info("[{}][INIT] 파일 파싱 완료 | 대상 건수: {}건", BATCH_APP_ID, totalRecords);

        context.setRecordCount(totalRecords);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 대용량 배치 처리 시작 | 총 {}건", BATCH_APP_ID, totalRecords);

        Instant execStart = Instant.now();
        Instant lastLogTime = execStart;

        for (int i = 1; i <= totalRecords; i++) {
            // 실제 처리 시뮬레이션
            simulateDelay(msPerRecord);
            context.countSuccess();

            // N건마다 상세 로그
            if (i % LOG_INTERVAL == 0 || i == totalRecords) {
                Instant now = Instant.now();
                long elapsedSec   = Duration.between(execStart, now).getSeconds();
                long intervalMs   = Duration.between(lastLogTime, now).toMillis();
                double throughput = intervalMs > 0 ? (double) LOG_INTERVAL / intervalMs * 1000 : 0;
                double pct        = (double) i / totalRecords * 100;

                long remainingRecords = totalRecords - i;
                long etaSec = throughput > 0 ? (long)(remainingRecords / throughput) : 0;

                log.info("[{}][EXEC] 진행: {}/{} ({}%) | 성공={} | 속도={}건/초 | 경과={}초 | 예상잔여={}초",
                        BATCH_APP_ID, i, totalRecords,
                        String.format("%.1f", pct), context.getSuccessCount(),
                        String.format("%.0f", throughput), elapsedSec, etaSec);

                lastLogTime = now;
            }
        }

        long totalSec = Duration.between(execStart, Instant.now()).getSeconds();

        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        double avgMs = totalRecords > 0 ? (double)(totalSec * 1000) / totalRecords : 0;
        log.info("[{}][EXEC] 처리 완료 | 대상={}, 성공={}, 실패={}, 총소요={}초 (평균{}ms/건)",
                BATCH_APP_ID, totalRecords, context.getSuccessCount(), context.getFailCount(),
                totalSec, String.format("%.1f", avgMs));
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    private void simulateDelay(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
