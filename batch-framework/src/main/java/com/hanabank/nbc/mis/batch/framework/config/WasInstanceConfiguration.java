package com.hanabank.nbc.mis.batch.framework.config;

import com.hanabank.nbc.mis.batch.framework.vo.WasInstanceIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.InetAddress;

/**
 * WAS 인스턴스 식별 빈 설정.
 *
 * <p>INSTANCE_ID 우선순위:
 * <ol>
 *   <li>JVM System Property: {@code -Dbatch.instance.id=WAS1}</li>
 *   <li>환경변수: {@code BATCH_INSTANCE_ID=WAS1}</li>
 *   <li>기동 실패 (둘 다 없으면 IllegalStateException)</li>
 * </ol>
 *
 * <p>제약:
 * <ul>
 *   <li>최대 4자 (FWK_WAS_EXEC_BATCH.INSTANCE_ID VARCHAR2(4))</li>
 *   <li>영문 대문자 + 숫자만 허용 (예: WAS1, WAS2, WAS3)</li>
 * </ul>
 */
@Configuration
public class WasInstanceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WasInstanceConfiguration.class);

    private static final String SYS_PROP_KEY = "batch.instance.id";
    private static final String ENV_KEY      = "BATCH_INSTANCE_ID";
    private static final int    MAX_ID_LEN   = 4;

    @Bean
    public WasInstanceIdentity wasInstanceIdentity() {
        // 1차: System Property
        String instanceId = System.getProperty(SYS_PROP_KEY);
        String source = "SystemProperty(-D" + SYS_PROP_KEY + ")";

        // 2차: 환경변수
        if (!StringUtils.hasText(instanceId)) {
            instanceId = System.getenv(ENV_KEY);
            source = "EnvironmentVariable(" + ENV_KEY + ")";
        }

        // 둘 다 없으면 기동 실패
        if (!StringUtils.hasText(instanceId)) {
            throw new IllegalStateException(
                    "[BatchFramework] INSTANCE_ID가 설정되지 않았습니다. " +
                    "JVM 기동 옵션에 -D" + SYS_PROP_KEY + "=WAS1 을 추가하거나 " +
                    "환경변수 " + ENV_KEY + " 를 설정하세요.");
        }

        // 최대 4자 검증
        if (instanceId.length() > MAX_ID_LEN) {
            throw new IllegalStateException(
                    "[BatchFramework] INSTANCE_ID는 최대 " + MAX_ID_LEN + "자입니다. " +
                    "현재값: '" + instanceId + "' (" + instanceId.length() + "자)");
        }

        // 호스트명 조회 (실패 시 UNKNOWN으로 대체)
        String hostName = resolveHostName();

        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ [BatchFramework] WAS 인스턴스 식별 정보");
        log.info("│  INSTANCE_ID : {} (출처: {})", instanceId, source);
        log.info("│  HOST_NAME   : {}", hostName);
        log.info("└─────────────────────────────────────────────────────────────");

        return new WasInstanceIdentity(instanceId, hostName);
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("[BatchFramework] 호스트명 조회 실패 (UNKNOWN으로 대체): {}", e.getMessage());
            return "UNKNOWN";
        }
    }
}
