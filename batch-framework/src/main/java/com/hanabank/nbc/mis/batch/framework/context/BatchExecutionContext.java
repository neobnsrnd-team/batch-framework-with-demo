package com.hanabank.nbc.mis.batch.framework.context;

import com.hanabank.nbc.mis.batch.framework.vo.FwkBatchAppVo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 배치 실행 컨텍스트.
 * 하나의 배치 실행 생명주기(init → executeBatch → writeFinalLog) 동안 공유되는 상태 객체.
 *
 * <p>BatchJob 구현체는 이 컨텍스트를 통해:
 * <ul>
 *   <li>실행 파라미터(params) 조회</li>
 *   <li>배치 기준 날짜(baseBatchDate) 조회</li>
 *   <li>처리 건수 카운터 업데이트</li>
 *   <li>커스텀 속성 저장/조회</li>
 * </ul>
 */
public class BatchExecutionContext {

    // =========================================================
    // 식별 정보 (불변)
    // =========================================================

    private final String batchAppId;
    private final String instanceId;

    /** 배치 기준 날짜 (YYYYMMDD) - 비즈니스 날짜, 실제 실행일과 다를 수 있음 */
    private final String baseBatchDate;

    /** 당일 실행 순번 */
    private final int executeSeq;

    /** DB에서 로드한 배치 앱 메타 정보 */
    private final FwkBatchAppVo appMeta;

    /** 실행 파라미터 (DB PROPERTIES + REST 요청 파라미터 병합, REST 우선) */
    private final Map<String, String> params;

    // =========================================================
    // 처리 카운터 (가변 - BatchJob 구현체가 업데이트)
    // =========================================================

    /** 총 처리 대상 건수 */
    private long recordCount;

    /** 실행 시도 건수 */
    private long executeCount;

    /** 성공 건수 */
    private long successCount;

    /** 실패 건수 */
    private long failCount;

    // =========================================================
    // 오류 정보 (가변 - 프레임워크가 예외 포착 시 설정)
    // =========================================================

    private String errorCode;
    private String errorReason;

    // =========================================================
    // 커스텀 속성 (BatchJob 구현체 간 데이터 공유용)
    // =========================================================

    private final Map<String, Object> attributes = new HashMap<>();

    // =========================================================
    // 생성자
    // =========================================================

    public BatchExecutionContext(FwkBatchAppVo appMeta,
                                 String instanceId,
                                 String baseBatchDate,
                                 int executeSeq,
                                 Map<String, String> params) {
        this.batchAppId    = appMeta.getBatchAppId();
        this.instanceId    = instanceId;
        this.baseBatchDate = baseBatchDate;
        this.executeSeq    = executeSeq;
        this.appMeta       = appMeta;
        this.params        = Collections.unmodifiableMap(new HashMap<>(params));
    }

    // =========================================================
    // 파라미터 조회 헬퍼
    // =========================================================

    /** 파라미터 값 조회 (없으면 null) */
    public String getParam(String key) {
        return params.get(key);
    }

    /** 파라미터 값 조회 (없으면 defaultValue) */
    public String getParam(String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    /** 파라미터 존재 여부 */
    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    // =========================================================
    // 카운터 업데이트 메서드 (BatchJob 구현체에서 호출)
    // =========================================================

    /** 처리 대상 건수 설정 */
    public void setRecordCount(long count) { this.recordCount = count; }

    /** 처리 대상 건수 증가 */
    public void addRecordCount(long count) { this.recordCount += count; }

    /** 성공 처리 1건 카운트 (executeCount, successCount 동시 증가) */
    public void countSuccess() {
        this.executeCount++;
        this.successCount++;
    }

    /** 성공 처리 N건 카운트 */
    public void countSuccess(long count) {
        this.executeCount += count;
        this.successCount += count;
    }

    /** 실패 처리 1건 카운트 (executeCount, failCount 동시 증가) */
    public void countFail() {
        this.executeCount++;
        this.failCount++;
    }

    /** 실패 처리 N건 카운트 */
    public void countFail(long count) {
        this.executeCount += count;
        this.failCount += count;
    }

    // =========================================================
    // 커스텀 속성
    // =========================================================

    public void setAttribute(String key, Object value) { attributes.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) { return (T) attributes.get(key); }

    // =========================================================
    // Getter
    // =========================================================

    public String getBatchAppId()    { return batchAppId; }
    public String getInstanceId()    { return instanceId; }
    public String getBaseBatchDate() { return baseBatchDate; }
    public int getExecuteSeq()       { return executeSeq; }
    public FwkBatchAppVo getAppMeta(){ return appMeta; }
    public Map<String, String> getParams() { return params; }

    public long getRecordCount()  { return recordCount; }
    public long getExecuteCount() { return executeCount; }
    public long getSuccessCount() { return successCount; }
    public long getFailCount()    { return failCount; }

    public String getErrorCode()   { return errorCode; }
    public String getErrorReason() { return errorReason; }

    public void setErrorCode(String errorCode)     { this.errorCode = errorCode; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

    @Override
    public String toString() {
        return String.format("BatchExecutionContext{batchAppId='%s', instanceId='%s', baseBatchDate='%s', seq=%d}",
                batchAppId, instanceId, baseBatchDate, executeSeq);
    }
}
