package com.hanabank.nbc.mis.batch.framework.core;

import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.exception.BatchExecutionException;
import com.hanabank.nbc.mis.batch.framework.exception.BatchInitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BatchJob 기본 구현 추상 클래스.
 *
 * <p>공통 기능:
 * <ul>
 *   <li>클래스명 기반 batchAppId 자동 제공 (오버라이드 가능)</li>
 *   <li>각 단계 진입/종료 시 구조화된 로그 자동 출력</li>
 *   <li>init() 기본 구현 (빈 구현 — 파라미터만 출력)</li>
 * </ul>
 *
 * <p>최소 구현:
 * <pre>{@code
 * @Component
 * public class MyBatchJob extends AbstractBatchJob {
 *     @Override
 *     public String getBatchAppId() { return "BATCH_MY_JOB"; }
 *
 *     @Override
 *     public void executeBatch(BatchExecutionContext ctx) throws BatchExecutionException {
 *         // 비즈니스 로직
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractBatchJob implements BatchJob {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * init() 기본 구현.
     * 파라미터를 로그로 출력하고 종료한다.
     * 실제 초기화가 필요하면 오버라이드한다.
     */
    @Override
    public void init(BatchExecutionContext context) throws BatchInitException {
        log.info("[{}][INIT] 파라미터: {}", context.getBatchAppId(), context.getParams());
        log.info("[{}][INIT] 배치기준일자: {}", context.getBatchAppId(), context.getBaseBatchDate());
        log.info("[{}][INIT] 실행순번: {}", context.getBatchAppId(), context.getExecuteSeq());
    }

    /**
     * 배치 앱 메타 정보 조회 헬퍼.
     * init() 또는 executeBatch() 내에서 DB 메타 정보 접근 시 사용.
     */
    protected String getAppProperty(BatchExecutionContext context, String key) {
        return context.getParam(key);
    }

    /**
     * 처리 진행률 로그 출력 헬퍼.
     * 대용량 처리 시 N건마다 호출하여 진행 상황을 출력한다.
     *
     * @param context    실행 컨텍스트
     * @param current    현재까지 처리한 건수
     * @param total      전체 건수
     * @param logInterval 로그 출력 간격 (N건마다)
     */
    protected void logProgress(BatchExecutionContext context, long current, long total, long logInterval) {
        if (current % logInterval == 0 || current == total) {
            double pct = total > 0 ? (double) current / total * 100 : 0;
            log.info("[{}][EXEC] 진행 중: {}/{} ({}%) | 성공={}, 실패={}",
                    context.getBatchAppId(), current, total,
                    String.format("%.1f", pct),
                    context.getSuccessCount(), context.getFailCount());
        }
    }
}
