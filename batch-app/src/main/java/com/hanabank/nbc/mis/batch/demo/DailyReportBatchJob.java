package com.hanabank.nbc.mis.batch.demo;

import com.hanabank.nbc.mis.batch.framework.core.AbstractBatchJob;
import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [데모 #1] 일일 리포트 생성 배치 — 정상 수행 시나리오.
 *
 * <p>시나리오:
 * <ul>
 *   <li>init() : 파라미터 검증, 처리 대상 데이터 100건 시뮬레이션 조회</li>
 *   <li>executeBatch() : 100건 처리 (성공), 10건마다 진행률 로그 출력</li>
 *   <li>결과 : SUCCESS | 대상=100, 실행=100, 성공=100, 실패=0</li>
 * </ul>
 *
 * <p>DB 등록 예시 (FWK_BATCH_APP):
 * <pre>
 *   BATCH_APP_ID     = BATCH_DAILY_REPORT
 *   BATCH_APP_FILE_NAME = DailyReportBatchJob
 *   CRON_TEXT        = 0 0 1 * * ?   (매일 01:00)
 *   PROPERTIES       = {"reportType":"DAILY","threshold":"100"}
 * </pre>
 */
@Component
public class DailyReportBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID = "BATCH_DAILY_REPORT";
    private static final int TOTAL_RECORDS   = 100;
    private static final int LOG_INTERVAL    = 10;

    // init()에서 로드한 설정값 (executeBatch()에서 사용)
    private String reportType;
    private int threshold;

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 파라미터 로딩 시작", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        log.info("[{}][INIT]   instanceId    : {}", BATCH_APP_ID, context.getInstanceId());
        log.info("[{}][INIT]   executeSeq    : {}", BATCH_APP_ID, context.getExecuteSeq());
        log.info("[{}][INIT]   params        : {}", BATCH_APP_ID, context.getParams());

        // 필수 파라미터: reportType
        reportType = context.getParam("reportType", "DAILY");
        log.info("[{}][INIT]   reportType    : {} (기본값: DAILY)", BATCH_APP_ID, reportType);

        // 선택 파라미터: threshold (정수 변환 검증)
        String thresholdStr = context.getParam("threshold", "100");
        try {
            threshold = Integer.parseInt(thresholdStr);
        } catch (NumberFormatException e) {
            log.error("[{}][INIT]   threshold 값이 숫자가 아닙니다: '{}'", BATCH_APP_ID, thresholdStr);
            throw new BatchInitException("INVALID_PARAM",
                    "threshold 파라미터는 정수여야 합니다. 현재값: " + thresholdStr, e);
        }
        log.info("[{}][INIT]   threshold     : {}", BATCH_APP_ID, threshold);

        // 처리 대상 건수 조회 (실제: DB 조회, 데모: 고정값)
        log.info("[{}][INIT] 처리 대상 데이터 조회 중... (실제: SELECT COUNT FROM REPORT_TBL)", BATCH_APP_ID);
        int recordCount = simulateFetchRecordCount(context.getBaseBatchDate());
        context.setRecordCount(recordCount);
        log.info("[{}][INIT] 처리 대상: {}건", BATCH_APP_ID, recordCount);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 일일 리포트 생성 시작 | reportType={}, baseBatchDate={}",
                BATCH_APP_ID, reportType, context.getBaseBatchDate());

        // 처리 대상 데이터 시뮬레이션 (실제: DB SELECT)
        List<Map<String, Object>> records = simulateFetchRecords(context.getBaseBatchDate(), TOTAL_RECORDS);
        log.info("[{}][EXEC] 처리 대상 데이터 로드 완료: {}건", BATCH_APP_ID, records.size());

        // 건별 처리 루프
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            long current = i + 1;

            try {
                processRecord(record, context.getBaseBatchDate());
                context.countSuccess();
            } catch (Exception e) {
                // 단건 처리 실패 — 전체 중단하지 않고 계속 (fail-fast: false)
                log.warn("[{}][EXEC] 레코드 처리 실패 (계속 진행): seq={}, error={}",
                        BATCH_APP_ID, current, e.getMessage());
                context.countFail();
            }

            // 진행률 로그 (N건마다)
            if (current % LOG_INTERVAL == 0 || current == records.size()) {
                log.info("[{}][EXEC] 진행 중: {}/{} | 성공={}, 실패={}",
                        BATCH_APP_ID, current, records.size(),
                        context.getSuccessCount(), context.getFailCount());
            }
        }

        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 처리 완료 | 대상={}, 성공={}, 실패={}",
                BATCH_APP_ID, records.size(), context.getSuccessCount(), context.getFailCount());

        // 실패 건수가 임계치 초과 시 예외 발생
        if (context.getFailCount() > threshold) {
            throw new BatchExecutionException("EXCEED_FAIL_THRESHOLD",
                    String.format("실패 건수(%d)가 임계치(%d)를 초과했습니다",
                            context.getFailCount(), threshold));
        }

        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    // =========================================================
    // 시뮬레이션 메서드 (실제 구현에서는 DB/외부 시스템 호출)
    // =========================================================

    private int simulateFetchRecordCount(String baseBatchDate) {
        // 실제: SELECT COUNT(*) FROM DAILY_REPORT_SRC WHERE REPORT_DATE = #{baseBatchDate}
        simulateDelay(50);
        return TOTAL_RECORDS;
    }

    private List<Map<String, Object>> simulateFetchRecords(String baseBatchDate, int count) {
        // 실제: SELECT * FROM DAILY_REPORT_SRC WHERE REPORT_DATE = #{baseBatchDate}
        simulateDelay(100);
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            records.add(Map.of(
                "id", i,
                "reportDate", baseBatchDate,
                "amount", (long)(Math.random() * 1_000_000),
                "status", "PENDING"
            ));
        }
        return records;
    }

    private void processRecord(Map<String, Object> record, String baseBatchDate) {
        // 실제: INSERT INTO DAILY_REPORT_RESULT VALUES (...)
        simulateDelay(5);
        // 모든 레코드 정상 처리 (성공 시나리오)
    }

    private void simulateDelay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
