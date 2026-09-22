package dev.arcaneclient.additions.presence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Stops oversized or stalled responses without allocating an unbounded body. */
final class PresenceResponse implements HttpResponse.BodySubscriber<byte[]> {
    private final int maximum;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private Flow.Subscription subscription;
    private boolean cancelled;
    PresenceResponse(int maximum) { this.maximum = maximum; }
    @Override public CompletionStage<byte[]> getBody() { return body; }
    @Override public synchronized void onSubscribe(Flow.Subscription incoming) {
        if (cancelled || subscription != null) { incoming.cancel(); return; }
        subscription = incoming;
        incoming.request(1);
    }
    @Override public synchronized void onNext(List<ByteBuffer> buffers) {
        if (cancelled) return;
        for (ByteBuffer buffer : buffers) {
            int count = buffer.remaining();
            if (count > maximum - bytes.size()) {
                body.completeExceptionally(new IOException("Oversized presence response"));
                cancel();
                return;
            }
            byte[] data = new byte[count];
            buffer.get(data);
            bytes.writeBytes(data);
        }
        subscription.request(1);
    }
    @Override public void onError(Throwable error) { body.completeExceptionally(new IOException("Presence body unavailable")); }
    @Override public synchronized void onComplete() { body.complete(bytes.toByteArray()); }
    synchronized void cancel() {
        cancelled = true;
        if (subscription != null) subscription.cancel();
        if (!body.isDone()) body.completeExceptionally(new IOException("Presence read cancelled"));
    }
}
