package com.hanabank.nbc.mis.batch.framework.core;

import com.hanabank.nbc.mis.batch.framework.context.BatchExecutionContext;

/**
 * 배치 실행 결과 객체.
 * BatchJobExecutor가 생성하여 스케줄러 및 REST 컨트롤러에 반환한다.
 */
public class BatchJobResult {

    // =========================================================
    // 결과 코드 상수
    // =========================================================

    /** 정상 완료 */
    public static final String CODE_SUCCESS     = "SUCCESS";

    /** init() 단계 실패 */
    public static final String CODE_FAILED_INIT = "FAILED_INIT";

    /** executeBatch() 단계 실패 */
    public static final String CODE_FAILED_EXEC = "FAILED_EXEC";

    /** 선행 배치 미완료로 스킵 */
    public static final String CODE_SKIPPED     = "SKIPPED";

    /** 프레임워크 내부 오류 (컨텍스트 설정 실패 등) */
    public static final String CODE_ERROR       = "ERROR";

    // =========================================================
    // 필드
    // =========================================================

    private final String batchAppId;
    private final String resRtCode;
    private final long recordCount;
    private final long executeCount;
    private final long successCount;
    private final long failCount;
    private final String errorCode;
    private final String errorReason;
    private final long elapsedMs;

    // =========================================================
    // 생성자 (private - 팩토리 메서드 사용)
    // =========================================================

    private BatchJobResult(String batchAppId, String resRtCode,
                           long recordCount, long executeCount,
                           long successCount, long failCount,
                           String errorCode, String errorReason,
                           long elapsedMs) {
        this.batchAppId   = batchAppId;
        this.resRtCode    = resRtCode;
        this.recordCount  = recordCount;
        this.executeCount = executeCount;
        this.successCount = successCount;
        this.failCount    = failCount;
        this.errorCode    = errorCode;
        this.errorReason  = errorReason;
        this.elapsedMs    = elapsedMs;
    }

    // =========================================================
    // 팩토리 메서드
    // =========================================================

    /** 정상 완료 결과 생성 */
    public static BatchJobResult success(BatchExecutionContext ctx, long elapsedMs) {
        return new BatchJobResult(
                ctx.getBatchAppId(), CODE_SUCCESS,
                ctx.getRecordCount(), ctx.getExecuteCount(),
                ctx.getSuccessCount(), ctx.getFailCount(),
                null, null, elapsedMs);
    }

    /** init() 실패 결과 생성 */
    public static BatchJobResult failedInit(BatchExecutionContext ctx, Throwable cause, long elapsedMs) {
        String errorCode   = extractErrorCode(cause);
        String errorReason = truncate(cause.toString() + " | " + cause.getMessage(), 4000);
        return new BatchJobResult(
                ctx.getBatchAppId(), CODE_FAILED_INIT,
                ctx.getRecordCount(), ctx.getExecuteCount(),
                ctx.getSuccessCount(), ctx.getFailCount(),
                errorCode, errorReason, elapsedMs);
    }

    /** executeBatch() 실패 결과 생성 */
    public static BatchJobResult failedExec(BatchExecutionContext ctx, Throwable cause, long elapsedMs) {
        String errorCode   = extractErrorCode(cause);
        String errorReason = truncate(cause.toString() + " | " + cause.getMessage(), 4000);
        return new BatchJobResult(
                ctx.getBatchAppId(), CODE_FAILED_EXEC,
                ctx.getRecordCount(), ctx.getExecuteCount(),
                ctx.getSuccessCount(), ctx.getFailCount(),
                errorCode, errorReason, elapsedMs);
    }

    /** 선행 배치 미완료 스킵 결과 생성 */
    public static BatchJobResult skipped(String batchAppId, String preBatchAppId) {
        return new BatchJobResult(
                batchAppId, CODE_SKIPPED,
                0, 0, 0, 0,
                "PRE_BATCH_NOT_DONE",
                "선행 배치 미완료로 실행 스킵: preBatchAppId=" + preBatchAppId,
                0L);
    }

    /** 프레임워크 내부 오류 결과 생성 */
    public static BatchJobResult error(String batchAppId, Throwable cause) {
        return new BatchJobResult(
                batchAppId, CODE_ERROR,
                0, 0, 0, 0,
                "FRAMEWORK_ERROR",
                truncate(cause.toString(), 4000),
                0L);
    }

    // =========================================================
    // 헬퍼
    // =========================================================

    private static String extractErrorCode(Throwable t) {
        if (t instanceof com.hanabank.nbc.mis.batch.framework.exception.BatchException be) {
            return be.getErrorCode();
        }
        return t.getClass().getSimpleName();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    // =========================================================
    // 상태 확인 메서드
    // =========================================================

    public boolean isSuccess()     { return CODE_SUCCESS.equals(resRtCode); }
    public boolean isFailedInit()  { return CODE_FAILED_INIT.equals(resRtCode); }
    public boolean isFailedExec()  { return CODE_FAILED_EXEC.equals(resRtCode); }
    public boolean isSkipped()     { return CODE_SKIPPED.equals(resRtCode); }
    public boolean isFailed()      { return isFailedInit() || isFailedExec() || CODE_ERROR.equals(resRtCode); }

    // =========================================================
    // Getter
    // =========================================================

    public String getBatchAppId()   { return batchAppId; }
    public String getResRtCode()    { return resRtCode; }
    public long getRecordCount()    { return recordCount; }
    public long getExecuteCount()   { return executeCount; }
    public long getSuccessCount()   { return successCount; }
    public long getFailCount()      { return failCount; }
    public String getErrorCode()    { return errorCode; }
    public String getErrorReason()  { return errorReason; }
    public long getElapsedMs()      { return elapsedMs; }

    @Override
    public String toString() {
        return String.format("BatchJobResult{batchAppId='%s', resRtCode='%s', record=%d, exec=%d, success=%d, fail=%d, elapsed=%dms}",
                batchAppId, resRtCode, recordCount, executeCount, successCount, failCount, elapsedMs);
    }
}
