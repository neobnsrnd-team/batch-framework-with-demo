package com.hanabank.nbc.mis.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * HanaBank NBC MIS 배치 데모 애플리케이션.
 *
 * <p>기동 방법 (WAS 인스턴스 ID 필수):
 * <pre>
 *   java -Dbatch.instance.id=WAS1 -jar batch-app.jar
 *   java -Dbatch.instance.id=WAS2 -jar batch-app.jar
 * </pre>
 *
 * <p>REST 수동 실행 (Basic Auth):
 * <pre>
 *   POST http://localhost:8080/api/batch/BATCH_DAILY_REPORT/run
 *   Authorization: Basic batchadmin:batchpass
 *   Content-Type: application/json
 *   {
 *     "baseBatchDate": "20240114",
 *     "forceRerun": false,
 *     "params": {}
 *   }
 * </pre>
 */
@SpringBootApplication(scanBasePackages = {
        "com.hanabank.nbc.mis.batch"        // framework + demo 패키지 모두 포함
})
public class BatchDemoApplication {

    private static final Logger log = LoggerFactory.getLogger(BatchDemoApplication.class);

    public static void main(String[] args) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║        HanaBank NBC MIS 배치 데모 애플리케이션 기동 중        ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");

        ConfigurableApplicationContext ctx = SpringApplication.run(BatchDemoApplication.class, args);

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║        배치 애플리케이션 기동 완료 — 스케줄러 실행 중         ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }
}
