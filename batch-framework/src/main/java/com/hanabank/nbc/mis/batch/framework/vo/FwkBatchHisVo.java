package com.hanabank.nbc.mis.batch.framework.vo;

/**
 * FWK_BATCH_HIS 테이블 매핑 VO.
 * INSERT(init 시점)와 UPDATE(종료 시점) 겸용.
 *
 * PK: BATCH_APP_ID + INSTANCE_ID + BATCH_DATE + BATCH_EXECUTE_SEQ
 */
public class FwkBatchHisVo {

    // =========================================================
    // PK 컬럼
    // =========================================================

    /** 배치 앱 ID */
    private String batchAppId;

    /** 실행 WAS 인스턴스 ID */
    private String instanceId;

    /** 배치 기준 날짜 (YYYYMMDD) - 비즈니스 날짜 */
    private String batchDate;

    /** 당일 실행 순번 (1부터, 재시도 시 증가) */
    private int batchExecuteSeq;

    // =========================================================
    // 이력 컬럼
    // =========================================================

    /** 배치 시작 일시 (YYYYMMDDHH24MISSFF3) - init() 전 INSERT */
    private String logDtime;

    /** 배치 종료 일시 (YYYYMMDDHH24MISSFF3) - executeBatch() 완료 후 UPDATE */
    private String batchEndDtime;

    /**
     * 결과 코드.
     * <ul>
     *   <li>RUNNING    - 실행 중 (INSERT 초기값)</li>
     *   <li>SUCCESS    - 정상 완료</li>
     *   <li>FAILED_INIT - init() 단계 실패</li>
     *   <li>FAILED_EXEC - executeBatch() 단계 실패</li>
     *   <li>SKIPPED    - 선행 배치 미완료로 스킵</li>
     *   <li>CANCELED   - forceRerun으로 취소됨</li>
     * </ul>
     */
    private String resRtCode;

    /** 오류 코드 */
    private String errorCode;

    /** 오류 상세 메시지 (최대 4000자) */
    private String errorReason;

    /** 총 처리 대상 건수 */
    private long recordCount;

    /** 실행 건수 */
    private long executeCount;

    /** 성공 건수 */
    private long successCount;

    /** 실패 건수 */
    private long failCount;

    /** 최종 수정자 (시스템: 배치 앱 ID) */
    private String lastUpdateUserId;

    // =========================================================
    // Getter / Setter
    // =========================================================

    public String getBatchAppId() { return batchAppId; }
    public void setBatchAppId(String batchAppId) { this.batchAppId = batchAppId; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getBatchDate() { return batchDate; }
    public void setBatchDate(String batchDate) { this.batchDate = batchDate; }

    public int getBatchExecuteSeq() { return batchExecuteSeq; }
    public void setBatchExecuteSeq(int batchExecuteSeq) { this.batchExecuteSeq = batchExecuteSeq; }

    public String getLogDtime() { return logDtime; }
    public void setLogDtime(String logDtime) { this.logDtime = logDtime; }

    public String getBatchEndDtime() { return batchEndDtime; }
    public void setBatchEndDtime(String batchEndDtime) { this.batchEndDtime = batchEndDtime; }

    public String getResRtCode() { return resRtCode; }
    public void setResRtCode(String resRtCode) { this.resRtCode = resRtCode; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

    public long getRecordCount() { return recordCount; }
    public void setRecordCount(long recordCount) { this.recordCount = recordCount; }

    public long getExecuteCount() { return executeCount; }
    public void setExecuteCount(long executeCount) { this.executeCount = executeCount; }

    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }

    public long getFailCount() { return failCount; }
    public void setFailCount(long failCount) { this.failCount = failCount; }

    public String getLastUpdateUserId() { return lastUpdateUserId; }
    public void setLastUpdateUserId(String lastUpdateUserId) { this.lastUpdateUserId = lastUpdateUserId; }
}
