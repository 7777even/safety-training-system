package com.sts.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 满足你期望的 IoT 事件批量刷新工具类（保留失败无限重试逻辑）
 */
public class BatchFlusher<V> {

    private static final Logger log = LoggerFactory.getLogger(BatchFlusher.class);

    private final ConcurrentLinkedQueue<V> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final long delayMs;        // 连续攒批延迟
    private final long retryDelayMs;   // 异常重试延迟
    private final int batchSize;
    private final Consumer<List<V>> flushFn;
    private final String name;

    private volatile boolean isShutdown = false;

    public BatchFlusher(String name, long delayMs, long retryDelayMs, int batchSize, Consumer<List<V>> flushFn) {
        this.name = name;
        this.delayMs = delayMs;
        this.retryDelayMs = retryDelayMs;
        this.batchSize = batchSize;
        this.flushFn = flushFn;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-flusher-" + name);
            t.setDaemon(false);
            return t;
        });
    }

    public void onEvent(V value) {
        if (isShutdown) {
            log.warn("[{}] Flusher is shutting down. Rejected event: {}", name, value);
            return;
        }
        buffer.offer(value);
        trySchedule(true); // 放入立刻执行（0延迟启动）
    }

    public void onEvents(List<V> values) {
        if (isShutdown) {
            log.warn("[{}] Flusher is shutting down. Rejected {} events.", name, values.size());
            return;
        }
        values.forEach(buffer::offer);
        trySchedule(true); // 放入立刻执行（0延迟启动）
    }

    public void shutdown() {
        this.isShutdown = true;
        log.info("[{}] Triggering graceful shutdown...", name);

        int maxChecks = 10;
        while ((!buffer.isEmpty() || scheduled.get()) && maxChecks-- > 0) {
            try {
                Thread.sleep(delayMs > 0 ? delayMs : 100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        scheduler.shutdown();
        try {
            flush();
            if (!scheduler.awaitTermination(15, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void trySchedule(boolean immediate) {
        if (isShutdown || scheduled.get()) return;
        if (scheduled.compareAndSet(false, true)) {
            long executeDelay = immediate ? 0 : delayMs;
            scheduler.schedule(this::flush, executeDelay, TimeUnit.MILLISECONDS);
        }
    }

    private void flush() {
        boolean hasError = false;
        try {
            List<V> snapshot = new ArrayList<>();
            V item;
            while ((item = buffer.poll()) != null) {
                snapshot.add(item);
            }

            if (!snapshot.isEmpty()) {
                List<V> failed = new ArrayList<>();
                for (int i = 0; i < snapshot.size(); i += batchSize) {
                    // 核心修复：用 new ArrayList 包装 subList，切断对整个 snapshot 的强引用视图，避免异步场景下内存泄漏
                    List<V> batch = new ArrayList<>(snapshot.subList(i, Math.min(i + batchSize, snapshot.size())));
                    try {
                        flushFn.accept(batch);
                    } catch (Exception e) {
                        log.error("[{}] Flush batch failed, size: {}", name, batch.size(), e);
                        failed.addAll(batch);
                    }
                }

                if (!failed.isEmpty()) {
                    hasError = true;
                    failed.forEach(buffer::offer); // 失败原样回滚到 buffer，等待下一轮重试
                }
            }
        } finally {
            // 确保状态重置
            scheduled.set(false);

            // 检查是否需要连续调度
            if (!buffer.isEmpty() && !isShutdown) {
                if (scheduled.compareAndSet(false, true)) {
                    // 发生错误时走配置的 retryDelayMs，正常情况走连续攒批 delayMs
                    long nextDelay = hasError ? retryDelayMs : delayMs;
                    scheduler.schedule(this::flush, nextDelay, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
