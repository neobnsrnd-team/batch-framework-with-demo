package com.hanabank.nbc.mis.batch.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배치 프레임워크 설정 프로퍼티.
 *
 * <p>application.yml 예시:
 * <pre>
 * batch:
 *   framework:
 *     scheduler-pool-size: 5
 *     fail-fast: false
 *     log-interval: 1000
 *     api:
 *       username: batchadmin
 *       password: batchpass
 * </pre>
 */
@ConfigurationProperties(prefix = "batch.framework")
public class BatchProperties {

    /** ThreadPoolTaskScheduler 스레드 풀 크기 (기본값: 5) */
    private int schedulerPoolSize = 5;

    /** 실패 시 즉시 종료 여부 (기본값: false — 부분 실패 허용) */
    private boolean failFast = false;

    /** 처리 진행률 로그 출력 간격 건수 (기본값: 1000) */
    private long logInterval = 1000L;

    /** REST API 인증 설정 */
    private Api api = new Api();

    public static class Api {
        /** REST API Basic Auth 사용자명 */
        private String username = "batchadmin";
        /** REST API Basic Auth 패스워드 */
        private String password = "batchpass";

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    // =========================================================
    // Getter / Setter
    // =========================================================

    public int getSchedulerPoolSize() { return schedulerPoolSize; }
    public void setSchedulerPoolSize(int schedulerPoolSize) { this.schedulerPoolSize = schedulerPoolSize; }

    public boolean isFailFast() { return failFast; }
    public void setFailFast(boolean failFast) { this.failFast = failFast; }

    public long getLogInterval() { return logInterval; }
    public void setLogInterval(long logInterval) { this.logInterval = logInterval; }

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }
}
