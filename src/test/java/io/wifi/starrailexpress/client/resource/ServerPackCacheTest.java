package io.wifi.starrailexpress.client.resource;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.HttpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ServerPackCacheTest {
    @TempDir
    Path root;
    HttpServer server;
    URL url;
    final AtomicInteger requests = new AtomicInteger();
    final AtomicReference<byte[]> content = new AtomicReference<>("server resource pack v1".getBytes(StandardCharsets.UTF_8));
    final HttpUtil.DownloadProgressListener listener = new HttpUtil.DownloadProgressListener() {
        public void requestStart() { }
        public void downloadStart(OptionalLong size) { }
        public void downloadedBytes(long bytes) { }
        public void requestFinished(boolean success) { }
    };

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pack", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = content.get();
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/pack");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    Path uuidDirectory() {
        return root.resolve(UUID.randomUUID().toString());
    }

    HashCode hash() {
        return Hashing.sha1().hashBytes(content.get());
    }

    Path download(Path uuidDirectory, HashCode hash, int limit) throws Exception {
        Path directory = ServerPackCache.prepareDirectory(uuidDirectory, hash == null ? null : hash.toString());
        return HttpUtil.downloadFile(directory, url, Map.of(), Hashing.sha1(), hash, limit, Proxy.NO_PROXY, listener);
    }

    @Test
    void sameContentAcrossNewIdsWorksEvenWhenServerIsOffline() throws Exception {
        HashCode hash = hash();
        Path first = download(uuidDirectory(), hash, 1024);
        server.stop(0);
        Path second = download(uuidDirectory(), hash, 1024);
        assertEquals(first, second);
        assertEquals(1, requests.get(), "第二个 UUID 不应再次访问 HTTP 服务");
        assertArrayEquals(content.get(), Files.readAllBytes(second));
    }

    @Test
    void migratesExistingUuidCacheWithoutDeletingItOrDownloading() throws Exception {
        Path oldDirectory = Files.createDirectory(uuidDirectory());
        Path oldPack = oldDirectory.resolve(hash().toString());
        Files.write(oldPack, content.get());
        Path selected = download(uuidDirectory(), hash(), 1024);
        assertEquals(root.resolve("sre-shared").resolve(hash().toString()), selected);
        assertArrayEquals(content.get(), Files.readAllBytes(oldPack));
        assertEquals(0, requests.get());
        try (var files = Files.list(selected.getParent())) {
            assertEquals(1, files.count(), "迁移不能遗留临时文件");
        }
    }

    @Test
    void changedContentAtSameUrlGetsNewFile() throws Exception {
        Path first = download(uuidDirectory(), hash(), 1024);
        content.set("updated server resource pack".getBytes(StandardCharsets.UTF_8));
        Path second = download(uuidDirectory(), hash(), 1024);
        assertNotEquals(first, second);
        assertEquals(2, requests.get());
        assertArrayEquals(content.get(), Files.readAllBytes(second));
    }

    @Test
    void corruptedCacheIsValidatedAndDownloadedAgain() throws Exception {
        HashCode hash = hash();
        Path file = download(uuidDirectory(), hash, 1024);
        Files.writeString(file, "corrupted", StandardCharsets.UTF_8);
        assertEquals(file, download(uuidDirectory(), hash, 1024));
        assertArrayEquals(content.get(), Files.readAllBytes(file));
        assertEquals(2, requests.get());
    }

    @Test
    void missingHashRetainsVanillaDirectoryAndRefetchesMutableUrl() throws Exception {
        Path directory = uuidDirectory();
        assertEquals(directory, ServerPackCache.prepareDirectory(directory, null));
        download(directory, null, 1024);
        content.set("changed without a hash".getBytes(StandardCharsets.UTF_8));
        Path second = download(directory, null, 1024);
        assertEquals(2, requests.get());
        assertArrayEquals(content.get(), Files.readAllBytes(second));
        assertFalse(Files.exists(root.resolve("sre-shared")));
    }

    @Test
    void rejectsInvalidHashAndDoesNotCreateDirectories() throws Exception {
        Path directory = uuidDirectory();
        for (String invalid : new String[]{"", "abc", "../outside", "A".repeat(40), "z".repeat(40)}) {
            assertEquals(directory, ServerPackCache.prepareDirectory(directory, invalid));
        }
        assertFalse(Files.exists(root.resolve("sre-shared")));
    }

    @Test
    void stillEnforcesVanillaDownloadSizeLimit() {
        assertThrows(RuntimeException.class, () -> download(uuidDirectory(), hash(), 4));
        assertEquals(1, requests.get());
    }

    @Test
    void wrongServerHashCannotBecomeSuccessfulCacheHit() {
        HashCode wrong = Hashing.sha1().hashBytes(new byte[]{42});
        assertThrows(RuntimeException.class, () -> download(uuidDirectory(), wrong, 1024));
        assertEquals(1, requests.get());
    }

    @Test
    void ignoresFilesOutsideVanillaUuidDirectories() throws Exception {
        Path unrelated = Files.createDirectory(root.resolve("unrelated"));
        Files.write(unrelated.resolve(hash().toString()), content.get());
        download(uuidDirectory(), hash(), 1024);
        assertEquals(1, requests.get());
        assertTrue(Files.exists(unrelated.resolve(hash().toString())));
    }
}
