package com.boombapcompile.blckvox.config;

import com.boombapcompile.blckvox.config.properties.ThreadPoolProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Configuration for thread pools used in asynchronous processing.
 * Provides optimized executors for parallel STT engine transcription and event offload.
 *
 * <p>Pool sizes are configured via {@link ThreadPoolProperties} and can be tuned
 * in application.properties based on hardware and workload.
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final Logger LOG = LogManager.getLogger(ThreadPoolConfig.class);

    private final ThreadPoolProperties threadPoolProperties;
    private final MeterRegistry meterRegistry;

    public ThreadPoolConfig(ThreadPoolProperties threadPoolProperties, MeterRegistry meterRegistry) {
        this.threadPoolProperties = threadPoolProperties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Creates an optimized thread pool for parallel STT engine processing.
     *
     * <p>Pool sizing strategy configured via {@code threadpool.stt.*} properties:
     * <ul>
     *   <li>Core pool: default 4 - handles typical load</li>
     *   <li>Max pool: default 8 - handles burst traffic</li>
     *   <li>Queue: default 50 tasks - prevents unbounded memory growth</li>
     * </ul>
     *
     * <p>Rejection policy: Abort with logging.
     * When the pool and queue are full, the task is rejected with a
     * {@link java.util.concurrent.RejectedExecutionException} to prevent
     * blocking the calling thread (which may be an event executor thread).
     *
     * <p>Thread naming: configured via {@code threadpool.stt.thread-name-prefix}
     * for easy identification in logs and profilers.
     *
     * <p>MDC propagation: Copies Log4j2 ThreadContext (MDC) from the submitting thread to
     * the worker thread to preserve request/user correlation IDs in async logs.
     *
     * @return Configured executor for STT engine processing
     */
    @Bean(name = "sttExecutor")
    public Executor sttExecutor() {
        ThreadPoolProperties.SttPoolProperties sttProps = threadPoolProperties.getStt();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(sttProps.getCorePoolSize());
        executor.setMaxPoolSize(sttProps.getMaxPoolSize());
        executor.setQueueCapacity(sttProps.getQueueCapacity());
        executor.setThreadNamePrefix(sttProps.getThreadNamePrefix());
        executor.setKeepAliveSeconds(sttProps.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler((r, e) -> {
            LOG.warn("sttExecutor rejected task — pool ({}/{}) and queue ({}) are full",
                    e.getPoolSize(), e.getMaximumPoolSize(), e.getQueue().size());
            throw new java.util.concurrent.RejectedExecutionException(
                    "sttExecutor pool and queue are full");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Propagate MDC to worker threads
        executor.setTaskDecorator(createMdcPropagatingTaskDecorator());

        executor.initialize();
        return executor;
    }

    /**
     * Creates a bounded thread pool for event listener offload (transcription work).
     *
     * <p>This executor offloads CPU-intensive transcription work from Spring's event bus
     * to prevent blocking other event listeners. Pool sizing is conservative to avoid
     * resource contention with STT engine threads.
     *
     * <p>Pool sizing strategy:
     * <ul>
     *   <li>Core pool: 2 threads - handles typical hotkey press rate (1-2 concurrent events)</li>
     *   <li>Max pool: 4 threads - handles burst scenarios (multiple rapid hotkey presses)</li>
     *   <li>Queue: 10 tasks - bounded to prevent memory exhaustion from runaway events</li>
     * </ul>
     *
     * <p>Rejection policy: Discard-oldest with metrics.
     * When pool and queue are full, the oldest queued task is discarded and replaced
     * by the new task, preventing the event bus thread from blocking.
     *
     * <p>Thread naming: {@code event-pool-N} for easy identification in logs.
     *
     * <p>MDC propagation: Copies Log4j2 ThreadContext from event publisher to worker thread.
     *
     * @return Configured executor for event listener offload
     * @since 1.1
     */
    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolProperties.EventPoolProperties eventProps = threadPoolProperties.getEvent();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(eventProps.getCorePoolSize());
        executor.setMaxPoolSize(eventProps.getMaxPoolSize());
        executor.setQueueCapacity(eventProps.getQueueCapacity());
        executor.setThreadNamePrefix(eventProps.getThreadNamePrefix());
        executor.setKeepAliveSeconds(eventProps.getKeepAliveSeconds());
        // Use DiscardOldestPolicy for event executor to prevent blocking the Spring event bus.
        // CallerRunsPolicy would block all event delivery if the pool is saturated.
        Counter discardCounter = Counter.builder("blckvox.event.executor.discard")
                .description("Event executor task discards due to queue saturation")
                .register(meterRegistry);
        executor.setRejectedExecutionHandler((r, e) -> {
            if (!e.isShutdown()) {
                Runnable oldest = e.getQueue().poll();
                if (oldest != null) {
                    discardCounter.increment();
                    LOG.warn("Event executor saturated, discarding oldest queued task");
                }
                if (!e.isShutdown()) {
                    e.execute(r);
                }
            }
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Propagate MDC to worker threads (same decorator as sttExecutor)
        executor.setTaskDecorator(createMdcPropagatingTaskDecorator());

        executor.initialize();
        return executor;
    }

    /**
     * Creates a task decorator that propagates Log4j2 MDC (ThreadContext) from the
     * submitting thread to worker threads.
     *
     * <p>This ensures that correlation IDs and other contextual information stored in
     * the MDC are available in async tasks, enabling proper log correlation across
     * thread boundaries.
     *
     * <p><b>Implementation Details:</b>
     * <ul>
     *   <li>Captures the MDC context from the submitting thread</li>
     *   <li>Restores it in the worker thread before task execution</li>
     *   <li>Cleans up and restores the previous worker thread MDC after execution</li>
     * </ul>
     *
     * @return TaskDecorator that propagates MDC context
     */
    private TaskDecorator createMdcPropagatingTaskDecorator() {
        return runnable -> {
            Map<String, String> contextMap = ThreadContext.getImmutableContext();
            return () -> {
                Map<String, String> previous = ThreadContext.getImmutableContext();
                try {
                    // Clear existing worker context first to prevent stale entries leaking
                    ThreadContext.clearAll();
                    if (!contextMap.isEmpty()) {
                        ThreadContext.putAll(contextMap);
                    }
                    runnable.run();
                } finally {
                    ThreadContext.clearAll();
                    if (!previous.isEmpty()) {
                        ThreadContext.putAll(previous);
                    }
                }
            };
        };
    }
}
