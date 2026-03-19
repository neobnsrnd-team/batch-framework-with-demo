package com.hanabank.nbc.mis.batch.demo;

import com.hanabank.nbc.mis.batch.framework.core.AbstractBatchJob;
import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.springframework.stereotype.Component;

/**
 * [데모 #5] executeBatch()에서 비즈니스 오류로 BatchExecutionException 발생 시나리오.
 *
 * <p>시나리오:
 * <ul>
 *   <li>init() : 정상 완료, 200건 조회</li>
 *   <li>executeBatch() : 150건 처리 후 외부 API 오류로 BatchExecutionException 발생</li>
 *   <li>결과 : FAILED_EXEC | 오류코드=EXTERNAL_API_ERROR</li>
 *   <li>이력 : 실행=150, 성공=150, 실패=0 (예외 시점 카운터 기록)</li>
 * </ul>
 *
 * <p>DailyReportBatchJob과의 차이:
 * <ul>
 *   <li>DailyReport: 단건 실패는 계속 진행 (fail-fast: false)</li>
 *   <li>이 배치: 외부 시스템 전체 오류 시 즉시 중단 (BatchExecutionException 던짐)</li>
 * </ul>
 *
 * <p>DB 등록 예시:
 * <pre>
 *   BATCH_APP_ID     = BATCH_EXEC_BIZ_FAIL
 *   BATCH_APP_FILE_NAME = ExecBusinessFailBatchJob
 *   CRON_TEXT        = 0 30 3 * * ?
 *   PROPERTIES       = {"failAtRecord":"150"}
 * </pre>
 */
@Component
public class ExecBusinessFailBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID  = "BATCH_EXEC_BIZ_FAIL";
    private static final int    TOTAL_RECORDS = 200;

    private int failAtRecord;  // N번째 레코드에서 의도적 실패

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 비즈니스 실패 시나리오 배치 초기화", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        log.info("[{}][INIT]   params        : {}", BATCH_APP_ID, context.getParams());

        // 몇 번째 레코드에서 실패시킬지 설정
        failAtRecord = Integer.parseInt(context.getParam("failAtRecord", "150"));
        log.info("[{}][INIT]   failAtRecord  : {} ({}번째 레코드에서 의도적 실패 발생)",
                BATCH_APP_ID, failAtRecord, failAtRecord);

        context.setRecordCount(TOTAL_RECORDS);
        log.info("[{}][INIT]   처리 대상     : {}건", BATCH_APP_ID, TOTAL_RECORDS);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 처리 시작 | 총 {}건, {}번째에서 오류 발생 예정",
                BATCH_APP_ID, TOTAL_RECORDS, failAtRecord);

        for (int i = 1; i <= TOTAL_RECORDS; i++) {

            // N번째 레코드에서 외부 API 오류 시뮬레이션
            if (i == failAtRecord) {
                log.error("[{}][EXEC] ✗ {}번째 레코드 처리 중 외부 API 응답 오류 발생!", BATCH_APP_ID, i);
                log.error("[{}][EXEC]   오류 내용: 외부 정산 시스템 타임아웃 (30s 초과)", BATCH_APP_ID);
                log.error("[{}][EXEC]   현재까지 처리: {}건 성공 / 0건 실패", BATCH_APP_ID, context.getSuccessCount());
                log.error("[{}][EXEC]   배치 중단 — FAILED_EXEC으로 기록됩니다", BATCH_APP_ID);

                throw new BatchExecutionException("EXTERNAL_API_ERROR",
                        String.format("외부 정산 시스템 API 오류 (처리 중단) | 처리위치=%d/%d, 성공=%d건",
                                i, TOTAL_RECORDS, context.getSuccessCount()));
            }

            // 정상 처리
            simulateDelay(5);
            context.countSuccess();

            if (i % 50 == 0) {
                log.info("[{}][EXEC] 진행 중: {}/{} | 성공={}", BATCH_APP_ID, i, TOTAL_RECORDS,
                        context.getSuccessCount());
            }
        }

        log.info("[{}][EXEC] 완료 (정상 종료 - failAtRecord={}를 넘어선 경우)", BATCH_APP_ID, failAtRecord);
    }

    private void simulateDelay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
