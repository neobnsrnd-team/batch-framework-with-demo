package com.hanabank.nbc.mis.batch.framework.web;

/**
 * 배치 수동 실행 REST API 응답 DTO.
 */
public class BatchRunResponse {

    private String batchAppId;
    private String baseBatchDate;
    private String resRtCode;
    private long recordCount;
    private long executeCount;
    private long successCount;
    private long failCount;
    private long elapsedMs;
    private String errorCode;
    private String errorReason;

    /** forceRerun=true로 실행되었는지 여부 */
    private boolean forceRerun;

    /** forceRerun 시 취소된 이전 SUCCESS 이력 건수 */
    private int canceledCount;

    // =========================================================
    // 팩토리 메서드
    // =========================================================

    public static BatchRunResponse of(String batchAppId, String baseBatchDate,
                                      com.hanabank.nbc.mis.batch.framework.core.BatchJobResult result,
                                      boolean forceRerun, int canceledCount) {
        BatchRunResponse res = new BatchRunResponse();
        res.batchAppId   = batchAppId;
        res.baseBatchDate = baseBatchDate;
        res.resRtCode    = result.getResRtCode();
        res.recordCount  = result.getRecordCount();
        res.executeCount = result.getExecuteCount();
        res.successCount = result.getSuccessCount();
        res.failCount    = result.getFailCount();
        res.elapsedMs    = result.getElapsedMs();
        res.errorCode    = result.getErrorCode();
        res.errorReason  = result.getErrorReason();
        res.forceRerun   = forceRerun;
        res.canceledCount = canceledCount;
        return res;
    }

    // =========================================================
    // Getter / Setter
    // =========================================================

    public String getBatchAppId()    { return batchAppId; }
    public String getBaseBatchDate() { return baseBatchDate; }
    public String getResRtCode()     { return resRtCode; }
    public long getRecordCount()     { return recordCount; }
    public long getExecuteCount()    { return executeCount; }
    public long getSuccessCount()    { return successCount; }
    public long getFailCount()       { return failCount; }
    public long getElapsedMs()       { return elapsedMs; }
    public String getErrorCode()     { return errorCode; }
    public String getErrorReason()   { return errorReason; }
    public boolean isForceRerun()    { return forceRerun; }
    public int getCanceledCount()    { return canceledCount; }

    public void setBatchAppId(String batchAppId)       { this.batchAppId = batchAppId; }
    public void setBaseBatchDate(String baseBatchDate) { this.baseBatchDate = baseBatchDate; }
    public void setResRtCode(String resRtCode)         { this.resRtCode = resRtCode; }
    public void setRecordCount(long recordCount)       { this.recordCount = recordCount; }
    public void setExecuteCount(long executeCount)     { this.executeCount = executeCount; }
    public void setSuccessCount(long successCount)     { this.successCount = successCount; }
    public void setFailCount(long failCount)           { this.failCount = failCount; }
    public void setElapsedMs(long elapsedMs)           { this.elapsedMs = elapsedMs; }
    public void setErrorCode(String errorCode)         { this.errorCode = errorCode; }
    public void setErrorReason(String errorReason)     { this.errorReason = errorReason; }
    public void setForceRerun(boolean forceRerun)      { this.forceRerun = forceRerun; }
    public void setCanceledCount(int canceledCount)    { this.canceledCount = canceledCount; }
}
