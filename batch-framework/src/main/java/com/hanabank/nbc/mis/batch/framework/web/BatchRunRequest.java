package com.hanabank.nbc.mis.batch.framework.web;

import jakarta.validation.constraints.Pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * 배치 수동 실행 REST API 요청 DTO.
 *
 * <p>예시 요청:
 * <pre>
 * POST /api/batch/BATCH_DAILY_REPORT/run
 * {
 *   "baseBatchDate": "20240114",
 *   "forceRerun": false,
 *   "params": {
 *     "reportType": "DAILY",
 *     "threshold": "500"
 *   }
 * }
 * </pre>
 */
public class BatchRunRequest {

    /**
     * 배치 기준 날짜 (YYYYMMDD 형식).
     * null이면 오늘 날짜 사용.
     */
    @Pattern(regexp = "^\\d{8}$", message = "baseBatchDate 형식이 올바르지 않습니다 (YYYYMMDD)")
    private String baseBatchDate;

    /**
     * 강제 재실행 여부.
     * true이면 기존 SUCCESS 이력을 CANCELED로 변경 후 재실행한다.
     * false(기본값)이면 SUCCESS 이력이 있을 경우 409 Conflict 반환.
     */
    private boolean forceRerun = false;

    /**
     * 추가 실행 파라미터.
     * DB PROPERTIES 기본값을 오버라이드한다.
     */
    private Map<String, String> params = new HashMap<>();

    // =========================================================
    // Getter / Setter
    // =========================================================

    public String getBaseBatchDate() { return baseBatchDate; }
    public void setBaseBatchDate(String baseBatchDate) { this.baseBatchDate = baseBatchDate; }

    public boolean isForceRerun() { return forceRerun; }
    public void setForceRerun(boolean forceRerun) { this.forceRerun = forceRerun; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params != null ? params : new HashMap<>(); }
}
