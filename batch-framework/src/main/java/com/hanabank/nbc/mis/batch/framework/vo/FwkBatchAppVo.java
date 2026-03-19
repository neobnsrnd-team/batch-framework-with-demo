package com.hanabank.nbc.mis.batch.framework.vo;

/**
 * FWK_BATCH_APP 테이블 매핑 VO.
 * 배치 앱 정의 정보를 담는다.
 */
public class FwkBatchAppVo {

    /** 배치 앱 ID (PK) */
    private String batchAppId;

    /** 배치 앱 명 */
    private String batchAppName;

    /** Spring Bean 클래스 단순명 (예: DailyReportBatchJob) */
    private String batchAppFileName;

    /** 배치 설명 */
    private String batchAppDesc;

    /** 선행 배치 앱 ID (null이면 선행 없음) */
    private String preBatchAppId;

    /** 실행 주기: D=일, W=주, M=월, H=시간, O=일회성 */
    private String batchCycle;

    /** Cron 표현식 (예: 0 0 1 * * ?) */
    private String cronText;

    /** 실패 시 재시도 허용 여부 (Y/N) */
    private String retryableYn;

    /** WAS별 실행 여부 (Y=특정 WAS, N=모든 WAS) */
    private String perWasYn;

    /** 중요도: A=높음, B=중간, C=낮음 */
    private String importantType;

    /** 배치 기본 파라미터 (JSON 형식) */
    private String properties;

    /** 거래 ID */
    private String trxId;

    /** 조직 ID */
    private String orgId;

    /** 입출력 유형: I=입력, O=출력, B=양방향 */
    private String ioType;

    /** 최종 수정 일시 */
    private String lastUpdateDtime;

    /** 최종 수정자 ID */
    private String lastUpdateUserId;

    // =========================================================
    // Getter / Setter
    // =========================================================

    public String getBatchAppId() { return batchAppId; }
    public void setBatchAppId(String batchAppId) { this.batchAppId = batchAppId; }

    public String getBatchAppName() { return batchAppName; }
    public void setBatchAppName(String batchAppName) { this.batchAppName = batchAppName; }

    public String getBatchAppFileName() { return batchAppFileName; }
    public void setBatchAppFileName(String batchAppFileName) { this.batchAppFileName = batchAppFileName; }

    public String getBatchAppDesc() { return batchAppDesc; }
    public void setBatchAppDesc(String batchAppDesc) { this.batchAppDesc = batchAppDesc; }

    public String getPreBatchAppId() { return preBatchAppId; }
    public void setPreBatchAppId(String preBatchAppId) { this.preBatchAppId = preBatchAppId; }

    public String getBatchCycle() { return batchCycle; }
    public void setBatchCycle(String batchCycle) { this.batchCycle = batchCycle; }

    public String getCronText() { return cronText; }
    public void setCronText(String cronText) { this.cronText = cronText; }

    public String getRetryableYn() { return retryableYn; }
    public void setRetryableYn(String retryableYn) { this.retryableYn = retryableYn; }

    public String getPerWasYn() { return perWasYn; }
    public void setPerWasYn(String perWasYn) { this.perWasYn = perWasYn; }

    public String getImportantType() { return importantType; }
    public void setImportantType(String importantType) { this.importantType = importantType; }

    public String getProperties() { return properties; }
    public void setProperties(String properties) { this.properties = properties; }

    public String getTrxId() { return trxId; }
    public void setTrxId(String trxId) { this.trxId = trxId; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getIoType() { return ioType; }
    public void setIoType(String ioType) { this.ioType = ioType; }

    public String getLastUpdateDtime() { return lastUpdateDtime; }
    public void setLastUpdateDtime(String lastUpdateDtime) { this.lastUpdateDtime = lastUpdateDtime; }

    public String getLastUpdateUserId() { return lastUpdateUserId; }
    public void setLastUpdateUserId(String lastUpdateUserId) { this.lastUpdateUserId = lastUpdateUserId; }

    @Override
    public String toString() {
        return "FwkBatchAppVo{batchAppId='" + batchAppId + "', batchAppName='" + batchAppName
                + "', cronText='" + cronText + "', preBatchAppId='" + preBatchAppId + "'}";
    }
}
