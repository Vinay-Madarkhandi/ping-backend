package com.heartbeat.ping.config;

import com.heartbeat.ping.config.properties.WorkerPoolProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bulkhead isolation: two independent check-execution pools so a flood of slow/hanging
 * monitors cannot starve fast ones. Both use an abort policy, so a saturated pool never
 * blocks the polling thread — rejected checks keep their lease and are re-claimed later.
 */
@Configuration
@EnableAsync
public class CheckExecutorConfig {

    public static final String FAST_CHECK_EXECUTOR = "fastCheckExecutor";
    public static final String SLOW_CHECK_EXECUTOR = "slowCheckExecutor";

    @Bean
    @ConfigurationProperties("monitor.executor.fast")
    public WorkerPoolProperties fastPoolProperties() {
        return new WorkerPoolProperties();
    }

    @Bean
    @ConfigurationProperties("monitor.executor.slow")
    public WorkerPoolProperties slowPoolProperties() {
        WorkerPoolProperties props = new WorkerPoolProperties();
        props.setCorePoolSize(4);
        props.setMaxPoolSize(8);
        props.setQueueCapacity(100);
        return props;
    }

    @Bean(name = FAST_CHECK_EXECUTOR)
    public ThreadPoolTaskExecutor fastCheckExecutor() {
        return build(fastPoolProperties(), "monitor-fast-");
    }

    @Bean(name = SLOW_CHECK_EXECUTOR)
    public ThreadPoolTaskExecutor slowCheckExecutor() {
        return build(slowPoolProperties(), "monitor-slow-");
    }

    private ThreadPoolTaskExecutor build(WorkerPoolProperties props, String threadPrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix(threadPrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
