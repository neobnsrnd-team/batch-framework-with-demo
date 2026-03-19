package com.hanabank.nbc.mis.batch.framework.vo;

/**
 * WAS 인스턴스 식별 정보.
 * System Property(-Dbatch.instance.id) 또는 환경변수(BATCH_INSTANCE_ID)에서 읽는다.
 */
public class WasInstanceIdentity {

    /** FWK_WAS_EXEC_BATCH.INSTANCE_ID 와 매핑 (최대 4자: WAS1, WAS2 ...) */
    private final String instanceId;

    /** 현재 서버 호스트명 (참고용 로그) */
    private final String hostName;

    public WasInstanceIdentity(String instanceId, String hostName) {
        this.instanceId = instanceId;
        this.hostName   = hostName;
    }

    public String getInstanceId() { return instanceId; }
    public String getHostName()   { return hostName; }

    @Override
    public String toString() {
        return "WasInstanceIdentity{instanceId='" + instanceId + "', hostName='" + hostName + "'}";
    }
}
