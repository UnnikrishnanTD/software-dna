package com.softwaredna.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * The pool that runs analyses in the background.
 *
 * <p>Deliberately bounded, and deliberately not virtual threads. An analysis is
 * CPU-bound — parsing, walking trees, diffing commits — so more concurrency
 * than cores buys nothing and costs memory, since each run holds a whole
 * repository's metrics. The queue is bounded too: rejecting a submission with
 * a clear error is better than accepting work the service cannot get to.
 */
@Configuration
public class AnalysisExecutorConfig {

    @Bean(name = "analysisExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor analysisExecutor(AnalysisExecutionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("analysis-");
        // Surfaces back-pressure as a rejection the controller can translate
        // into a 503, rather than silently running the work on the caller.
        RejectedExecutionHandler abort = new ThreadPoolExecutor.AbortPolicy();
        executor.setRejectedExecutionHandler(abort);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
