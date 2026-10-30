package org.agmas.noellesroles.game.halloween.online;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BridgeProtocolTest {
    @TempDir Path temp;
    static final String SECRET = "0123456789abcdef0123456789abcdef";
    static final String VECTOR_BODY = "{\"serverId\":\"11111111-1111-4111-8111-111111111111\",\"batchId\":\"22222222-2222-4222-8222-222222222222\",\"entries\":[{\"id\":\"33333333-3333-4333-8333-333333333333\",\"minecraftUuid\":\"44444444-4444-4444-8444-444444444444\",\"amount\":7,\"earnedAt\":1792368000000}]}";

    @Test void fixedSignatureVectorMatchesIndependentPythonHmac() {
        byte[] body = BridgeProtocol.body(UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                List.of(new Earning(UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        UUID.fromString("44444444-4444-4444-8444-444444444444"), 7, 1792368000000L)));
        assertEquals(VECTOR_BODY, new String(body, StandardCharsets.UTF_8));
        assertEquals("27f8a63d7fb651d8ffab960fc0d1bca9d828e7b591fda36cbb1c516827301db3",
                BridgeProtocol.signature(SECRET, 1792368030000L, body));
        assertNotEquals(BridgeProtocol.signature(SECRET, 1792368030000L, body),
                BridgeProtocol.signature(SECRET, 1792368030000L, (VECTOR_BODY + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    @Test void onlyHttpsOriginsAndLiteralDevelopmentLoopbackAreAccepted() {
        OnlineConfig config = new OnlineConfig(); config.bridgeSecret = SECRET;
        for (String allowed : List.of("https://example.com", "https://example.com/", "https://example.com:8443",
                "http://localhost:3000", "http://127.0.0.1:3000/", "http://[::1]:3000")) {
            config.baseUrl = allowed;
            assertEquals(BridgeProtocol.PATH, config.endpoint().getRawPath());
        }
        for (String denied : List.of("", "http://example.com", "http://127.1", "http://localhost.example.com",
                "https://user:secret@example.com", "https://example.com?token=x", "https://example.com/#x",
                "https://example.com/subpath", "https://example.com/%2e%2e", "ftp://example.com", "//example.com",
                "https://example.com:0", "https://example.com:65536")) {
            config.baseUrl = denied; assertThrows(IllegalArgumentException.class, config::endpoint, denied);
        }
    }

    @Test void placeholderIsDisabledAndLimitsAndSecretsAreValidated() throws Exception {
        OnlineConfig config = OnlineConfig.load(temp.resolve("config/halloween-online.json"));
        assertFalse(config.enabled); assertEquals("", config.baseUrl); assertEquals("", config.bridgeSecret);
        assertEquals(30, config.flushIntervalSeconds); assertEquals(128, config.batchSize); assertEquals(10, config.requestTimeoutSeconds);
        config.baseUrl = "https://example.com";
        assertThrows(IllegalArgumentException.class, config::endpoint);
        config.bridgeSecret = SECRET;
        for (int invalid : new int[]{0, 129}) { config.batchSize = invalid; assertThrows(IllegalArgumentException.class, config::endpoint); }
        config.batchSize = 128; config.requestTimeoutSeconds = 0; assertThrows(IllegalArgumentException.class, config::endpoint);
        Files.writeString(temp.resolve("config/halloween-online.json"), "{secret leak");
        Exception error = assertThrows(java.io.IOException.class, () -> OnlineConfig.load(temp.resolve("config/halloween-online.json")));
        assertFalse(error.toString().contains("secret leak"));
    }

    @Test void actualHttpSendsExactBytesAndHeadersAndDoesNotRedirect() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>(), signature = new AtomicReference<>(), method = new AtomicReference<>();
        AtomicInteger redirected = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(BridgeProtocol.PATH, exchange -> {
            received.set(exchange.getRequestBody().readAllBytes());
            timestamp.set(exchange.getRequestHeaders().getFirst("X-SRE-Timestamp"));
            signature.set(exchange.getRequestHeaders().getFirst("X-SRE-Signature")); method.set(exchange.getRequestMethod());
            exchange.getResponseHeaders().set("Location", "/redirect-target");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/redirect-target", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        try {
            BridgeTransport transport = BridgeTransport.http(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + BridgeProtocol.PATH), 10);
            byte[] body = VECTOR_BODY.getBytes(StandardCharsets.UTF_8);
            String signed = BridgeProtocol.signature(SECRET, 1792368030000L, body);
            assertEquals(302, transport.post(body, 1792368030000L, signed).status());
            assertEquals("POST", method.get()); assertArrayEquals(body, received.get());
            assertEquals("1792368030000", timestamp.get()); assertEquals(signed, signature.get()); assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }

    @Test void malformedJsonExtensionsAndDuplicateFieldsCannotAcknowledge() {
        for (String bad : List.of("{'ok':true,'acknowledgedIds':[]}", "{ok:true,acknowledgedIds:[]}",
                "{\"ok\":true,/*comment*/\"acknowledgedIds\":[]}",
                "{\"ok\":true,\"ok\":true,\"acknowledgedIds\":[]}",
                "{\"ok\":true,\"acknowledgedIds\":[],\"acknowledgedIds\":[]}",
                "{\"ok\":true,\"acknowledgedIds\":[]} trailing"))
            assertThrows(java.io.IOException.class, () -> BridgeProtocol.acknowledgements(200, bad));
    }

    @Test void timeoutAlsoBoundsAResponseWithHeadersButStalledBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(BridgeProtocol.PATH, exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write(' '); exchange.getResponseBody().flush();
            try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            BridgeTransport transport = BridgeTransport.http(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + BridgeProtocol.PATH), 1);
            long start = System.nanoTime();
            assertThrows(java.io.IOException.class, () -> transport.post(VECTOR_BODY.getBytes(StandardCharsets.UTF_8), 1, "test"));
            assertTrue(System.nanoTime() - start < java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1800));
        } finally { server.stop(0); }
    }

    @Test void invalidAmountAndNoncanonicalUuidAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Earning(UUID.randomUUID(), UUID.randomUUID(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.canonicalUuid("1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.canonicalUuid("AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA"));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.body(UUID.randomUUID(), UUID.randomUUID(), List.of()));
    }
}
