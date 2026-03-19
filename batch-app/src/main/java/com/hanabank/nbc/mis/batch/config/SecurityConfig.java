package com.hanabank.nbc.mis.batch.config;

import com.hanabank.nbc.mis.batch.framework.config.BatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * REST API 보안 설정.
 *
 * <p>인증 방식: HTTP Basic Auth
 * <p>보호 경로: /api/batch/** (배치 수동 실행 API)
 * <p>계정 정보: application.yml의 batch.framework.api.username/password
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final BatchProperties batchProperties;

    public SecurityConfig(BatchProperties batchProperties) {
        this.batchProperties = batchProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())   // REST API는 CSRF 불필요
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))  // H2 Console iframe 허용
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/batch/**").authenticated()  // 배치 API 인증 필요
                .requestMatchers("/actuator/health").permitAll()   // 헬스체크 공개
                .requestMatchers("/h2-console/**").permitAll()     // H2 Console 공개
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> {});         // HTTP Basic Auth 활성화

        log.info("[SecurityConfig] REST API Basic Auth 설정 완료 | 보호경로: /api/batch/**");

        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        String username = batchProperties.getApi().getUsername();
        String password = batchProperties.getApi().getPassword();

        UserDetails user = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("BATCH_ADMIN")
                .build();

        log.info("[SecurityConfig] 배치 API 계정 생성 완료 | username={}", username);

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
