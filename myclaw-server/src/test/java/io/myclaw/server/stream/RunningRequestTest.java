package io.myclaw.server.stream;

import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunningRequestTest {
    @Test
    void cancelClosesTransportAndCancelsFuture() {
        RunningRequest request = new RunningRequest();
        AtomicBoolean transportClosed = new AtomicBoolean();
        FutureTask<Void> future = new FutureTask<>(() -> null);
        request.attachTransportCancel(() -> transportClosed.set(true));
        request.attachFuture(future);

        assertTrue(request.cancel());
        assertTrue(request.isCancelled());
        assertTrue(transportClosed.get());
        assertTrue(future.isCancelled());
        assertFalse(request.cancel());
        assertThrows(java.util.concurrent.CancellationException.class, request::checkCancelled);
    }

    @Test
    void resourcesAttachedAfterCancellationAreCancelledImmediately() {
        RunningRequest request = new RunningRequest();
        AtomicBoolean transportClosed = new AtomicBoolean();
        FutureTask<Void> future = new FutureTask<>(() -> null);
        request.cancel();
        request.attachTransportCancel(() -> transportClosed.set(true));
        request.attachFuture(future);

        assertTrue(transportClosed.get());
        assertTrue(future.isCancelled());
    }
}