package com.hanabank.nbc.mis.batch.framework.vo;

/**
 * FWK_WAS_EXEC_BATCH JOIN FWK_BATCH_APP 결과 VO.
 * WAS 기동 시 이 WAS에서 실행할 배치 목록을 담는다.
 */
public class BatchScheduleVo {

    // FWK_BATCH_APP 컬럼
    private String batchAppId;
    private String batchAppName;
    private String batchAppFileName;   // Spring Bean 클래스 단순명
    private String batchAppDesc;
    private String preBatchAppId;
    private String batchCycle;
    private String cronText;
    private String retryableYn;
    private String perWasYn;
    private String importantType;
    private String properties;
    private String trxId;
    private String orgId;
    private String ioType;

    // FWK_WAS_EXEC_BATCH 컬럼
    private String instanceId;
    private String useYn;

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

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }

    /** FwkBatchAppVo로 변환 (BatchJobExecutor 호출 시 사용) */
    public FwkBatchAppVo toAppVo() {
        FwkBatchAppVo vo = new FwkBatchAppVo();
        vo.setBatchAppId(this.batchAppId);
        vo.setBatchAppName(this.batchAppName);
        vo.setBatchAppFileName(this.batchAppFileName);
        vo.setBatchAppDesc(this.batchAppDesc);
        vo.setPreBatchAppId(this.preBatchAppId);
        vo.setBatchCycle(this.batchCycle);
        vo.setCronText(this.cronText);
        vo.setRetryableYn(this.retryableYn);
        vo.setPerWasYn(this.perWasYn);
        vo.setImportantType(this.importantType);
        vo.setProperties(this.properties);
        vo.setTrxId(this.trxId);
        vo.setOrgId(this.orgId);
        vo.setIoType(this.ioType);
        return vo;
    }

    @Override
    public String toString() {
        return "BatchScheduleVo{batchAppId='" + batchAppId + "', batchAppName='" + batchAppName
                + "', cronText='" + cronText + "', instanceId='" + instanceId + "', useYn='" + useYn + "'}";
    }
}
