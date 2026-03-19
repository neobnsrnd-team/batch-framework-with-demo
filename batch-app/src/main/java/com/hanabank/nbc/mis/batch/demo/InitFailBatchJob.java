package com.hanabank.nbc.mis.batch.demo;

import com.hanabank.nbc.mis.batch.framework.core.AbstractBatchJob;
import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.springframework.stereotype.Component;

/**
 * [데모 #3] init() 단계 실패 시나리오.
 *
 * <p>시나리오:
 * <ul>
 *   <li>init() : 필수 파라미터 누락으로 BatchInitException 발생</li>
 *   <li>executeBatch() : init() 실패로 호출되지 않음</li>
 *   <li>결과 : FAILED_INIT | 오류코드=MISSING_REQUIRED_PARAM</li>
 *   <li>이력 : FWK_BATCH_HIS에 FAILED_INIT으로 기록됨 (finally 보장)</li>
 * </ul>
 *
 * <p>검증 포인트:
 * <ul>
 *   <li>executeBatch()가 호출되지 않음을 로그로 확인</li>
 *   <li>FWK_BATCH_HIS에 RUNNING → FAILED_INIT UPDATE 확인</li>
 *   <li>프레임워크 finally 블록에서 writeFinalLog 호출 확인</li>
 * </ul>
 *
 * <p>DB 등록 예시:
 * <pre>
 *   BATCH_APP_ID     = BATCH_INIT_FAIL
 *   BATCH_APP_FILE_NAME = InitFailBatchJob
 *   CRON_TEXT        = 0 0 3 * * ?
 *   PROPERTIES       = {}   (← apiKey 미설정으로 init 실패)
 * </pre>
 */
@Component
public class InitFailBatchJob extends AbstractBatchJob {

    private static final String BATCH_APP_ID = "BATCH_INIT_FAIL";

    @Override
    public String getBatchAppId() {
        return BATCH_APP_ID;
    }

    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
        log.info("[{}][INIT] 초기화 시작", BATCH_APP_ID);
        log.info("[{}][INIT]   baseBatchDate : {}", BATCH_APP_ID, context.getBaseBatchDate());
        log.info("[{}][INIT]   params        : {}", BATCH_APP_ID, context.getParams());

        // 필수 파라미터 검증: apiKey
        String apiKey = context.getParam("apiKey");
        log.info("[{}][INIT]   apiKey 확인 중...", BATCH_APP_ID);

        if (apiKey == null || apiKey.isBlank()) {
            log.error("[{}][INIT] ✗ 필수 파라미터 'apiKey'가 없습니다!", BATCH_APP_ID);
            log.error("[{}][INIT]   → FWK_BATCH_APP.PROPERTIES 또는 REST params에 apiKey를 설정하세요", BATCH_APP_ID);
            throw new BatchInitException("MISSING_REQUIRED_PARAM",
                    "필수 파라미터 'apiKey'가 설정되지 않았습니다. DB PROPERTIES 또는 요청 파라미터를 확인하세요.");
        }

        // 이 아래 코드는 apiKey가 없으면 실행되지 않음
        log.info("[{}][INIT]   apiKey 확인 완료: {}***", BATCH_APP_ID,
                apiKey.substring(0, Math.min(3, apiKey.length())));

        // 외부 API 연결 시도 (apiKey 있을 때)
        log.info("[{}][INIT] 외부 API 연결 테스트 중...", BATCH_APP_ID);
        simulateDelay(200);

        // apiKey가 'INVALID' 이면 연결 실패 시뮬레이션
        if ("INVALID".equalsIgnoreCase(apiKey)) {
            log.error("[{}][INIT] ✗ 외부 API 인증 실패 (apiKey 무효)", BATCH_APP_ID);
            throw new BatchInitException("API_AUTH_FAILED",
                    "외부 API 인증에 실패했습니다. apiKey를 확인하세요. (apiKey=" + apiKey + ")");
        }

        log.info("[{}][INIT] 초기화 완료", BATCH_APP_ID);
        log.info("[{}][INIT] ─────────────────────────────────────────────────────", BATCH_APP_ID);
    }

    @Override
    public void executeBatch(BatchExecutionContext context) throws BatchExecutionException {
        // init()이 실패하면 이 메서드는 절대 호출되지 않음
        // 만약 이 로그가 보이면 프레임워크 버그
        log.info("[{}][EXEC] 실행 (init 성공 시에만 도달)", BATCH_APP_ID);
        log.info("[{}][EXEC] 실제 비즈니스 로직 처리 중...", BATCH_APP_ID);
        simulateDelay(100);
        context.setRecordCount(10);
        context.countSuccess(10);
        log.info("[{}][EXEC] 완료", BATCH_APP_ID);
    }

    private void simulateDelay(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
