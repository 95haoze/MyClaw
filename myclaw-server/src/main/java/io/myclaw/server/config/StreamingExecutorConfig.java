package io.myclaw.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
public class StreamingExecutorConfig {

    @Bean(name = "streamingExecutor")
    public AsyncTaskExecutor streamingExecutor(
            @Value("${myclaw.server.streaming.core-pool-size:4}") int corePoolSize,
            @Value("${myclaw.server.streaming.max-pool-size:16}") int maxPoolSize,
            @Value("${myclaw.server.streaming.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("myclaw-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
