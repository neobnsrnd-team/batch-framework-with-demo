package com.hanabank.nbc.mis.batch.framework.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 배치 프레임워크 자동 설정.
 *
 * <p>batch-app 모듈의 @SpringBootApplication이 component scan 범위에
 * 프레임워크 패키지를 포함하지 않을 경우 이 클래스를 @Import 하거나
 * spring.factories 에 등록하여 자동 활성화한다.
 *
 * <p>현재 구성:
 * <ul>
 *   <li>@ComponentScan — 프레임워크 패키지 전체 스캔</li>
 *   <li>@MapperScan — MyBatis Mapper 인터페이스 자동 등록</li>
 *   <li>@EnableConfigurationProperties — BatchProperties 활성화</li>
 * </ul>
 */
@Configuration
@ComponentScan(basePackages = "com.hanabank.nbc.mis.batch.framework")
@MapperScan(basePackages = "com.hanabank.nbc.mis.batch.framework.mapper")
@EnableConfigurationProperties(BatchProperties.class)
public class BatchFrameworkAutoConfiguration {
    // 별도 빈 설정 없음 — 각 @Configuration, @Component 클래스가 자동 처리
}
