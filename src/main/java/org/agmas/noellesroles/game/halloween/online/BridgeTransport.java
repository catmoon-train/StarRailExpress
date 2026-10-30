package org.agmas.noellesroles.game.halloween.online;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 可替换的 HTTP 边界，生产实现只能由单一后台发送线程调用。 */
@FunctionalInterface
public interface BridgeTransport {
    record Reply(int status, String body) {}
    Reply post(byte[] body, long timestamp, String signature) throws IOException, InterruptedException;

    static BridgeTransport http(URI endpoint, int timeoutSeconds) {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
        return (body, timestamp, signature) -> {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-SRE-Timestamp", Long.toString(timestamp))
                    .header("X-SRE-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var future = client.sendAsync(request, info -> new LimitedBody());
            try {
                HttpResponse<String> response = future.get(timeoutSeconds, TimeUnit.SECONDS);
                return new Reply(response.statusCode(), response.body());
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new HttpTimeoutException("Bridge request timed out");
            } catch (InterruptedException ex) {
                future.cancel(true);
                throw ex;
            } catch (ExecutionException ex) {
                throw new IOException("Bridge transport failed");
            }
        };
    }

    /** 响应体上限；发送端 future.get 为头部及完整 body 提供统一的超时边界。 */
    final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<String> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 65536) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Oversized bridge response"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
        public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
    }
}
