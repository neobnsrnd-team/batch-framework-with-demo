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
 * [데모 #2] 데이터 동기화 배치 — 정상 수행 + 선행 배치 의존성 시나리오.
 *
 * <p>시나리오:
 * <ul>
 *   <li>선행 배치 : BATCH_DAILY_REPORT (완료 후에만 실행)</li>
 *   <li>init() : 외부 API 연결 확인, 동기화 대상 500건 조회</li>
 *   <li>executeBatch() : 500건을 50건 단위로 배치 처리 (Chunk 방식 시뮬레이션)</li>
 *   <li>결과 : SUCCESS | 대상=500, 실행=500, 성공=500, 실패=0</li>
 * </ul>
 *
 * <p>DB 등록 예시 (FWK_BATCH_APP):
 * <pre>
 *   BATCH_APP_ID     = BATCH_DATA_SYNC
 *   BATCH_APP_FILE_NAME = DataSyncBatchJob
 *   CRON_TEXT        = 0 30 2 * * ?   (매일 02:30)
 *   PRE_BATCH_APP_ID = BATCH_DAILY_REPORT
 *   PROPERTIES       = {"chunkSize":"50","targetSystem":"ERP"}
 * </pre>
 */
@Component
public class DataSyncBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID  = "BATCH_DATA_SYNC";
    private static final int    TOTAL_RECORDS = 500;

    private int chunkSize;
    private String targetSystem;

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 데이터 동기화 배치 초기화", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        log.info("[{}][INIT]   params        : {}", BATCH_APP_ID, context.getParams());

        // 파라미터 로딩
        chunkSize    = Integer.parseInt(context.getParam("chunkSize", "50"));
        targetSystem = context.getParam("targetSystem", "ERP");
        log.info("[{}][INIT]   chunkSize     : {}", BATCH_APP_ID, chunkSize);
        log.info("[{}][INIT]   targetSystem  : {}", BATCH_APP_ID, targetSystem);

        // 외부 시스템 연결 확인 (실제: HTTP 헬스체크 또는 DB 연결 테스트)
        log.info("[{}][INIT] 외부 시스템({}) 연결 확인 중...", BATCH_APP_ID, targetSystem);
        simulateExternalSystemCheck();
        log.info("[{}][INIT] 외부 시스템 연결 정상", BATCH_APP_ID);

        // 처리 대상 건수 조회
        log.info("[{}][INIT] 동기화 대상 데이터 건수 조회 중...", BATCH_APP_ID);
        int recordCount = simulateFetchCount(context.getBaseBatchDate());
        context.setRecordCount(recordCount);

        int totalChunks = (int) Math.ceil((double) recordCount / chunkSize);
        log.info("[{}][INIT] 동기화 대상: {}건 (청크: {}건/회 × {}회)",
                BATCH_APP_ID, recordCount, chunkSize, totalChunks);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 데이터 동기화 시작 | target={}, chunkSize={}",
                BATCH_APP_ID, targetSystem, chunkSize);

        long totalRecords = context.getRecordCount();
        int totalChunks   = (int) Math.ceil((double) totalRecords / chunkSize);

        for (int chunkNo = 1; chunkNo <= totalChunks; chunkNo++) {
            int offset = (chunkNo - 1) * chunkSize;
            int limit  = (int) Math.min(chunkSize, totalRecords - offset);

            log.info("[{}][EXEC] 청크 처리 [{}/{}] | offset={}, size={}",
                    BATCH_APP_ID, chunkNo, totalChunks, offset, limit);

            try {
                // 청크 데이터 조회 (실제: SELECT ... LIMIT #{limit} OFFSET #{offset})
                List<Map<String, Object>> chunk = simulateFetchChunk(offset, limit);

                // 외부 시스템으로 데이터 전송 (실제: REST API 호출 또는 파일 전송)
                int sent = simulateSendToExternalSystem(chunk, targetSystem);
                context.countSuccess(sent);

                log.info("[{}][EXEC] 청크 [{}/{}] 완료 | 전송={}건 | 누적 성공={}",
                        BATCH_APP_ID, chunkNo, totalChunks, sent, context.getSuccessCount());

            } catch (Exception e) {
                // 청크 단위 실패 처리
                log.error("[{}][EXEC] 청크 [{}/{}] 실패: {}", BATCH_APP_ID, chunkNo, totalChunks, e.getMessage());
                context.countFail(limit);
                throw new BatchExecutionException("CHUNK_SEND_FAILED",
                        String.format("청크 %d/%d 전송 실패: %s", chunkNo, totalChunks, e.getMessage()), e);
            }
        }

        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 동기화 완료 | 대상={}, 성공={}, 실패={}",
                BATCH_APP_ID, totalRecords, context.getSuccessCount(), context.getFailCount());
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    // =========================================================
    // 시뮬레이션 메서드
    // =========================================================

    private void simulateExternalSystemCheck() {
        simulateDelay(100);
        // 정상 연결 성공
    }

    private int simulateFetchCount(String baseBatchDate) {
        simulateDelay(50);
        return TOTAL_RECORDS;
    }

    private List<Map<String, Object>> simulateFetchChunk(int offset, int limit) {
        simulateDelay(30);
        List<Map<String, Object>> chunk = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            chunk.add(Map.of("id", offset + i + 1, "value", "DATA_" + (offset + i + 1)));
        }
        return chunk;
    }

    private int simulateSendToExternalSystem(List<Map<String, Object>> chunk, String targetSystem) {
        simulateDelay(80);   // 외부 API 호출 시뮬레이션
        return chunk.size(); // 전체 성공
    }

    private void simulateDelay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
