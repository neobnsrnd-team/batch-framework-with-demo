package com.hanabank.nbc.mis.batch.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * ThreadPoolTaskScheduler 빈 설정.
 *
 * <p>스케줄러 예외 처리 전략 (Layer 4):
 * ErrorHandler에서 Runnable(배치 태스크) 외부로 전파된 Throwable을 포착한다.
 * 스케줄러 스레드가 종료되지 않도록 예외를 삼키고 로그만 기록한다.
 */
@Configuration
public class BatchSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerConfig.class);

    private final BatchProperties batchProperties;

    public BatchSchedulerConfig(BatchProperties batchProperties) {
        this.batchProperties = batchProperties;
    }

    @Bean(name = "batchTaskScheduler")
    public ThreadPoolTaskScheduler batchTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(batchProperties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix("batch-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);

        // Layer 4: 스케줄러 ErrorHandler — Runnable 외부 Throwable 최종 방어선
        scheduler.setErrorHandler(throwable -> {
            log.error("┌─────────────────────────────────────────────────────────────");
            log.error("│ [Layer4-ErrorHandler] 스케줄러 스레드에서 처리되지 않은 예외 발생!");
            log.error("│ 원인: {} — {}", throwable.getClass().getName(), throwable.getMessage());
            log.error("│ 스케줄러 스레드는 정상 유지됩니다 (예외 삼킴)");
            log.error("└─────────────────────────────────────────────────────────────", throwable);
        });

        log.info("[BatchSchedulerConfig] ThreadPoolTaskScheduler 초기화 완료 | poolSize={}",
                batchProperties.getSchedulerPoolSize());

        return scheduler;
    }
}
