package com.hanabank.nbc.mis.batch.demo;

import com.hanabank.nbc.mis.batch.framework.core.AbstractBatchJob;
import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * [데모 #4] executeBatch()에서 미처리 예외(NullPointerException) 발생 시나리오.
 *
 * <p>시나리오:
 * <ul>
 *   <li>init() : 정상 완료</li>
 *   <li>executeBatch() : 개발자 실수로 NPE 발생 (예외 처리 코드 누락)</li>
 *   <li>결과 : FAILED_EXEC | 오류코드=NullPointerException</li>
 * </ul>
 *
 * <p>검증 포인트:
 * <ul>
 *   <li>개발자가 try/catch를 작성하지 않아도 프레임워크(Layer 2)가 포착</li>
 *   <li>FWK_BATCH_HIS에 RUNNING → FAILED_EXEC UPDATE 확인</li>
 *   <li>오류 상세(NullPointerException + 메시지)가 ERROR_REASON에 기록됨</li>
 *   <li>스케줄러 스레드가 중단되지 않고 다음 Cron 트리거 정상 동작</li>
 * </ul>
 *
 * <p>DB 등록 예시:
 * <pre>
 *   BATCH_APP_ID     = BATCH_EXEC_NPE
 *   BATCH_APP_FILE_NAME = ExecNpeBatchJob
 *   CRON_TEXT        = 0 0 4 * * ?
 * </pre>
 */
@Component
public class ExecNpeBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID = "BATCH_EXEC_NPE";

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 초기화 정상 완료 (executeBatch에서 NPE 발생 예정)", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        context.setRecordCount(50);
        log.info("[{}][INIT]   처리 대상     : 50건", BATCH_APP_ID);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        log.info("[{}][EXEC] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][EXEC] 배치 실행 시작 (의도적 NPE 발생 예제)", BATCH_APP_ID);

        // 정상 처리 시뮬레이션 (20건)
        for (int i = 1; i <= 20; i++) {
            simulateDelay(10);
            context.countSuccess();
            if (i % 10 == 0) {
                log.info("[{}][EXEC] 진행 중: {}/50 | 성공={}", BATCH_APP_ID, i, context.getSuccessCount());
            }
        }

        log.info("[{}][EXEC] 21번째 레코드 처리 중...", BATCH_APP_ID);

        // 개발자 실수: null 객체 역참조 (BatchExecutionException 미처리)
        // 이 예외는 프레임워크 Layer 2에서 자동으로 포착됨
        Map<String, Object> data = fetchDataWithNull();
        String value = data.get("key").toString();  // ← NPE 발생!
        log.info("[{}][EXEC] 이 로그는 출력되지 않습니다", BATCH_APP_ID);  // 도달 불가
    }

    // =========================================================
    // 시뮬레이션 메서드
    // =========================================================

    private Map<String, Object> fetchDataWithNull() {
        log.info("[{}][EXEC]   DB 조회 결과: key=null (NPE 유발 데이터)", BATCH_APP_ID);
        return Map.of();   // 빈 Map → get("key") = null → .toString() = NPE
    }

    private void simulateDelay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
