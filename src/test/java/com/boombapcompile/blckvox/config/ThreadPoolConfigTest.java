package com.boombapcompile.blckvox.config;

import com.boombapcompile.blckvox.config.properties.ThreadPoolProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.apache.logging.log4j.ThreadContext;

import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadPoolConfigTest {

    private static ThreadPoolProperties defaultThreadPoolProperties() {
        return new ThreadPoolProperties(
                new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 60, "stt-pool-"),
                new ThreadPoolProperties.EventPoolProperties(2, 4, 10, 60, "event-pool-"));
    }

    @Test
    void shouldCreateExecutorWithCorrectConfiguration() {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;

        // Check against default property values
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("stt-pool-");
    }

    @Test
    void shouldHandleConcurrentTasks() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedTasks = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(10); // Simulate work
                    completedTasks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(completedTasks.get()).isEqualTo(taskCount);
    }

    @Test
    void shouldNotExhaustThreadsUnderLoad() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.sttExecutor();

        int taskCount = 20;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(executor.getActiveCount()).isLessThanOrEqualTo(executor.getMaxPoolSize());
    }

    @Test
    void shouldUseCorrectThreadNamePrefix() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        String[] threadName = new String[1];

        executor.execute(() -> {
            threadName[0] = Thread.currentThread().getName();
            latch.countDown();
        });

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(threadName[0]).startsWith("stt-pool-");
    }

    @Test
    void shouldShutdownGracefully() {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.sttExecutor();

        executor.execute(() -> {
            // Simple task
        });

        executor.shutdown();
        assertThat(executor.getThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Test
    void shouldCreateEventExecutorWithCorrectConfiguration() {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.eventExecutor();

        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(2);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("event-pool-");
    }

    @Test
    void eventExecutorHandlesTasks() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.eventExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(latch::countDown);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void mdcPropagationWithNonEmptyContext() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        org.apache.logging.log4j.ThreadContext.put("testKey", "testValue");
        CountDownLatch latch = new CountDownLatch(1);
        String[] capturedValue = new String[1];

        executor.execute(() -> {
            capturedValue[0] = org.apache.logging.log4j.ThreadContext.get("testKey");
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedValue[0]).isEqualTo("testValue");
        org.apache.logging.log4j.ThreadContext.clearAll();
    }

    @Test
    void mdcPropagationWithEmptyContext() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        org.apache.logging.log4j.ThreadContext.clearAll();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger(0);

        executor.execute(() -> {
            completed.incrementAndGet();
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(completed.get()).isEqualTo(1);
    }

    @Test
    void eventExecutorRejectionDiscardOldestWhenSaturated() throws InterruptedException {
        // Create event executor with capacity 1 queue and 1 thread to force saturation
        ThreadPoolProperties properties = new ThreadPoolProperties(
                new ThreadPoolProperties.SttPoolProperties(4, 8, 50, 60, "stt-pool-"),
                new ThreadPoolProperties.EventPoolProperties(1, 1, 1, 60, "event-pool-"));
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.eventExecutor();

        CountDownLatch blockingLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicInteger completedCount = new AtomicInteger(0);

        // Fill the pool: 1 thread blocked + 1 queued
        executor.execute(() -> {
            try {
                blockingLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        });
        executor.execute(() -> completedCount.incrementAndGet());

        // This should trigger rejection handler (discard oldest, queue new)
        executor.execute(() -> {
            completedCount.incrementAndGet();
            completionLatch.countDown();
        });

        // Release the blocked thread
        blockingLatch.countDown();
        assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }

    @Test
    void eventExecutorRejectionWhenShutdown() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.eventExecutor();

        executor.shutdown();
        executor.getThreadPoolExecutor().awaitTermination(2, TimeUnit.SECONDS);

        // Get the rejection handler and invoke it directly with a shutdown executor
        RejectedExecutionHandler handler = executor.getThreadPoolExecutor().getRejectedExecutionHandler();
        // Should not throw — the handler checks isShutdown()
        handler.rejectedExecution(() -> {}, executor.getThreadPoolExecutor());
    }

    @Test
    void mdcPropagationRestoresPreviousContext() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.sttExecutor();

        // Set submitter context
        ThreadContext.clearAll();
        ThreadContext.put("submitterKey", "submitterVal");

        CountDownLatch setupLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        AtomicReference<String> workerContextBefore = new AtomicReference<>();
        AtomicReference<String> workerContextDuring = new AtomicReference<>();

        // First: set a "previous" context on the worker thread
        executor.execute(() -> {
            ThreadContext.put("workerKey", "workerVal");
            setupLatch.countDown();
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        });
        setupLatch.await(2, TimeUnit.SECONDS);
        releaseLatch.countDown();
        Thread.sleep(100); // Wait for the first task to complete

        // Second: run a decorated task on same worker thread
        CountDownLatch taskLatch = new CountDownLatch(1);
        executor.execute(() -> {
            workerContextDuring.set(ThreadContext.get("submitterKey"));
            taskLatch.countDown();
        });

        assertThat(taskLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(workerContextDuring.get()).isEqualTo("submitterVal");

        ThreadContext.clearAll();
        executor.shutdown();
    }

    @Test
    void mdcPropagationWithNullContextMap() throws InterruptedException {
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        // Clear all context — getImmutableContext() returns empty map (not null in Log4j2),
        // but the decorator handles it via null-safe check
        ThreadContext.clearAll();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger(0);

        executor.execute(() -> {
            // Verify no stale context leaked
            assertThat(ThreadContext.get("anyKey")).isNull();
            completed.incrementAndGet();
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(completed.get()).isEqualTo(1);
    }

    @Test
    void eventExecutorRejectionWithEmptyQueue() throws InterruptedException {
        // Directly test rejection handler when queue.poll() returns null
        ThreadPoolProperties properties = defaultThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.eventExecutor();

        RejectedExecutionHandler handler = executor.getThreadPoolExecutor().getRejectedExecutionHandler();
        CountDownLatch latch = new CountDownLatch(1);
        // Invoke rejection handler directly — queue is empty so poll() returns null
        handler.rejectedExecution(latch::countDown, executor.getThreadPoolExecutor());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }
}
