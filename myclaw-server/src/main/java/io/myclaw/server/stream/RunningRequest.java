package io.myclaw.server.stream;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 一次正在执行的流式请求，以及它关联的线程和网络流取消动作。 */
public final class RunningRequest {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Runnable> transportCancel = new AtomicReference<>();
    private volatile Future<?> future;

    public void attachFuture(Future<?> future) {
        this.future = future;
        if (cancelled.get()) {
            future.cancel(true);
        }
    }

    public void attachTransportCancel(Runnable cancelAction) {
        transportCancel.set(cancelAction);
        if (cancelled.get() && transportCancel.compareAndSet(cancelAction, null)) {
            runQuietly(cancelAction);
        }
    }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }

        Runnable cancelAction = transportCancel.getAndSet(null);
        if (cancelAction != null) {
            runQuietly(cancelAction);
        }

        Future<?> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
        return true;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("流式请求已取消");
        }
    }

    private static void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 取消清理不应覆盖原始执行结果。
        }
    }
}
