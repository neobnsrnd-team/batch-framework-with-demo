package com.hanabank.nbc.mis.batch.demo;

import com.hanabank.nbc.mis.batch.framework.core.AbstractBatchJob;
import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * [데모 #6] 부분 성공/실패 시나리오 — 일부 레코드 실패, 전체 중단 없이 계속 진행.
 *
 * <p>시나리오:
 * <ul>
 *   <li>init() : 1,000건 처리 대상 조회</li>
 *   <li>executeBatch() : 1,000건 처리 중 약 10% 무작위 실패 (단건 오류는 계속 진행)</li>
 *   <li>실패율 20% 초과 시 BatchExecutionException으로 중단</li>
 *   <li>결과 : SUCCESS 또는 FAILED_EXEC (실패율에 따라)</li>
 *   <li>이력 : 실행=1000, 성공=~900, 실패=~100</li>
 * </ul>
 *
 * <p>패턴: fail-fast=false 부분 실패 허용 배치
 * <ul>
 *   <li>단건 오류 시 context.countFail() 후 continue</li>
 *   <li>누적 실패율 임계치 초과 시만 BatchExecutionException 발생</li>
 *   <li>최종 성공/실패 건수가 FWK_BATCH_HIS에 정확히 기록됨</li>
 * </ul>
 *
 * <p>DB 등록 예시:
 * <pre>
 *   BATCH_APP_ID     = BATCH_PARTIAL_FAIL
 *   BATCH_APP_FILE_NAME = PartialFailBatchJob
 *   CRON_TEXT        = 0 0 5 * * ?
 *   PROPERTIES       = {"totalRecords":"1000","failRatePercent":"10","maxFailRatePercent":"20"}
 * </pre>
 */
@Component
public class PartialFailBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID = "BATCH_PARTIAL_FAIL";

    private int totalRecords;
    private int failRatePercent;      // 개별 레코드 실패 확률 (%)
    private int maxFailRatePercent;   // 전체 실패율 임계치 (이 초과 시 배치 중단)
    private final Random random = new Random(42);  // 재현 가능한 랜덤 (seed 고정)

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 부분 실패 배치 초기화", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        log.info("[{}][INIT]   params        : {}", BATCH_APP_ID, context.getParams());

        totalRecords       = Integer.parseInt(context.getParam("totalRecords",       "1000"));
        failRatePercent    = Integer.parseInt(context.getParam("failRatePercent",    "10"));
        maxFailRatePercent = Integer.parseInt(context.getParam("maxFailRatePercent", "20"));

        log.info("[{}][INIT]   totalRecords       : {}건", BATCH_APP_ID, totalRecords);
        log.info("[{}][INIT]   failRatePercent    : {}% (단건 실패 확률)", BATCH_APP_ID, failRatePercent);
        log.info("[{}][INIT]   maxFailRatePercent : {}% (이 초과 시 배치 중단)", BATCH_APP_ID, maxFailRatePercent);

        context.setRecordCount(totalRecords);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 부분 실패 배치 시작 | 총={}건, 예상 실패율={}%",
                BATCH_APP_ID, totalRecords, failRatePercent);

        List<Integer> failedIds = new ArrayList<>();

        for (int i = 1; i <= totalRecords; i++) {
            boolean shouldFail = random.nextInt(100) < failRatePercent;

            try {
                processRecord(i, shouldFail);
                context.countSuccess();
            } catch (Exception e) {
                // 단건 실패 — 계속 진행 (fail-fast: false)
                failedIds.add(i);
                context.countFail();
                log.warn("[{}][EXEC] 레코드 처리 실패: id={}, 사유={}", BATCH_APP_ID, i, e.getMessage());
            }

            // 100건마다 진행률 + 실패율 체크
            if (i % 100 == 0 || i == totalRecords) {
                double currentFailRate = context.getExecuteCount() > 0
                        ? (double) context.getFailCount() / context.getExecuteCount() * 100
                        : 0;

                log.info("[{}][EXEC] 진행 [{}/{}] | 성공={}, 실패={}, 현재실패율={}%",
                        BATCH_APP_ID, i, totalRecords,
                        context.getSuccessCount(), context.getFailCount(),
                        String.format("%.1f", currentFailRate));

                // 실패율 임계치 초과 체크
                if (i >= 100 && currentFailRate > maxFailRatePercent) {
                    log.error("[{}][EXEC] ✗ 실패율 임계치 초과! {}% > {}% — 배치 중단",
                            BATCH_APP_ID, String.format("%.1f", currentFailRate), maxFailRatePercent);
                    log.error("[{}][EXEC]   실패 레코드 ID (최근 10개): {}",
                            BATCH_APP_ID, failedIds.subList(Math.max(0, failedIds.size() - 10), failedIds.size()));

                    throw new BatchExecutionException("EXCEED_MAX_FAIL_RATE",
                            String.format("실패율 임계치 초과 (%.1f%% > %d%%) — 배치 중단 | 처리위치=%d/%d, 성공=%d, 실패=%d",
                                    currentFailRate, maxFailRatePercent,
                                    i, totalRecords,
                                    context.getSuccessCount(), context.getFailCount()));
                }
            }
        }

        // 최종 결과 요약
        double finalFailRate = totalRecords > 0
                ? (double) context.getFailCount() / totalRecords * 100
                : 0;

        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 처리 완료 | 대상={}, 성공={}, 실패={}, 최종실패율={}%",
                BATCH_APP_ID, totalRecords, context.getSuccessCount(), context.getFailCount(),
                String.format("%.1f", finalFailRate));

        if (!failedIds.isEmpty()) {
            log.warn("[{}][EXEC] 실패 레코드 ID ({}건): {}",
                    BATCH_APP_ID, failedIds.size(),
                    failedIds.size() <= 20 ? failedIds : failedIds.subList(0, 20) + "...");
        }

        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    private void processRecord(int id, boolean shouldFail) {
        simulateDelay(2);
        if (shouldFail) {
            throw new RuntimeException("레코드 처리 실패: id=" + id + " (데이터 정합성 오류)");
        }
    }

    private void simulateDelay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
