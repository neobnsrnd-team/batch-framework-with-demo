package com.hanabank.nbc.mis.batch.framework.logging;

import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;
import com.hanabank.nbc.mis.batch.framework.core.BatchJobResult;
import com.hanabank.nbc.mis.batch.framework.mapper.FwkBatchHisMapper;
import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchHisVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 실행 이력 DB 기록 서비스.
 *
 * <p>핵심 책임:
 * <ul>
 *   <li>{@link #writeInitLog} — init() 전 RUNNING 상태로 INSERT</li>
 *   <li>{@link #writeFinalLog} — executeBatch() 후 최종 결과로 UPDATE</li>
 *   <li>{@link #calculateNextSeq} — 당일 실행 순번(MAX+1) 계산</li>
 * </ul>
 *
 * <p>writeFinalLog는 REQUIRES_NEW 트랜잭션으로 실행하여
 * 배치 비즈니스 트랜잭션이 롤백되어도 이력은 반드시 커밋된다.
 */
@Service
public class BatchLogService {

    private static final Logger log = LoggerFactory.getLogger(BatchLogService.class);

    private final FwkBatchHisMapper hisMapper;

    public BatchLogService(FwkBatchHisMapper hisMapper) {
        this.hisMapper = hisMapper;
    }

    // =========================================================
    // 실행 순번 계산
    // =========================================================

    /**
     * 당일 최대 실행 순번 + 1 계산.
     * 이력이 없으면 1을 반환한다.
     */
    public int calculateNextSeq(String batchAppId, String instanceId, String batchDate) {
        int maxSeq = hisMapper.selectMaxExecuteSeq(batchAppId, instanceId, batchDate);
        int nextSeq = maxSeq + 1;
        log.debug("[{}] 실행 순번 계산: MAX({}) + 1 = {}", batchAppId, maxSeq, nextSeq);
        return nextSeq;
    }

    // =========================================================
    // 시작 이력 INSERT
    // =========================================================

    /**
     * 배치 시작 이력 INSERT (상태: RUNNING).
     * init() 호출 직전 프레임워크에서 호출한다.
     */
    @Transactional
    public void writeInitLog(BatchExecutionContext context) {
        FwkBatchHisVo vo = new FwkBatchHisVo();
        vo.setBatchAppId(context.getBatchAppId());
        vo.setInstanceId(context.getInstanceId());
        vo.setBatchDate(context.getBaseBatchDate());
        vo.setBatchExecuteSeq(context.getExecuteSeq());
        vo.setResRtCode("RUNNING");
        vo.setLastUpdateUserId(context.getBatchAppId());

        hisMapper.insertInitLog(vo);
        log.info("[{}][LOG] 시작 이력 INSERT 완료 | batchDate={}, seq={}, status=RUNNING",
                context.getBatchAppId(), context.getBaseBatchDate(), context.getExecuteSeq());
    }

    // =========================================================
    // 종료 이력 UPDATE (반드시 실행 보장)
    // =========================================================

    /**
     * 배치 종료 이력 UPDATE.
     *
     * <p>REQUIRES_NEW 트랜잭션으로 실행하여 배치 트랜잭션 롤백과 무관하게 이력이 커밋된다.
     * BatchJobExecutor의 finally 블록에서 호출하므로 실행이 반드시 보장된다.
     *
     * <p>이 메서드 자체에서 예외가 발생해도 상위에서 삼킨다
     * (safeWriteFinalLog 참조) — 이력 실패가 비즈니스 결과에 영향을 주지 않음.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeFinalLog(BatchExecutionContext context, BatchJobResult result) {
        FwkBatchHisVo vo = new FwkBatchHisVo();
        vo.setBatchAppId(context.getBatchAppId());
        vo.setInstanceId(context.getInstanceId());
        vo.setBatchDate(context.getBaseBatchDate());
        vo.setBatchExecuteSeq(context.getExecuteSeq());
        vo.setResRtCode(result.getResRtCode());
        vo.setErrorCode(result.getErrorCode());
        vo.setErrorReason(result.getErrorReason());
        vo.setRecordCount(result.getRecordCount());
        vo.setExecuteCount(result.getExecuteCount());
        vo.setSuccessCount(result.getSuccessCount());
        vo.setFailCount(result.getFailCount());
        vo.setLastUpdateUserId(context.getBatchAppId());

        hisMapper.updateFinalLog(vo);
        log.info("[{}][LOG] 종료 이력 UPDATE 완료 | batchDate={}, seq={}, status={}, record={}, exec={}, success={}, fail={}, elapsed={}ms",
                context.getBatchAppId(), context.getBaseBatchDate(), context.getExecuteSeq(),
                result.getResRtCode(), result.getRecordCount(), result.getExecuteCount(),
                result.getSuccessCount(), result.getFailCount(), result.getElapsedMs());
    }
}
