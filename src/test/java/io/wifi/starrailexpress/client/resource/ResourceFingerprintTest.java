package io.wifi.starrailexpress.client.resource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceFingerprintTest {
    @TempDir Path root;
    @AfterEach void clear() { ResourceFingerprint.clear(); }

    String hash(Path... paths) throws Exception {
        var digest = ResourceFingerprint.digest();
        for (var path : paths) ResourceFingerprint.path(digest, path);
        return ResourceFingerprint.finish(digest);
    }

    @Test void identicalContentReusesAcrossNamesAndLocations() throws Exception {
        Path a = Files.writeString(root.resolve("server-a.zip"), "same pack");
        Path b = Files.writeString(root.resolve("server-b.zip"), "same pack");
        assertEquals(hash(a), hash(b));
        assertEquals(hash(a), hash(a));
    }

    @Test void detectsContentUpdateAtSamePath() throws Exception {
        Path p = Files.writeString(root.resolve("pack.zip"), "version one");
        String first = hash(p);
        Files.writeString(p, "version two");
        Files.setLastModifiedTime(p, FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis() + 2000));
        assertNotEquals(first, hash(p));
    }

    @Test void manualClearDetectsEditsEvenIfSizeAndTimestampWerePreserved() throws Exception {
        Path p = Files.writeString(root.resolve("pack.zip"), "one");
        var modified = Files.getLastModifiedTime(p);
        String first = hash(p);
        Files.writeString(p, "two");
        Files.setLastModifiedTime(p, modified);
        ResourceFingerprint.clear();
        assertNotEquals(first, hash(p));
    }

    @Test void directoryDetectsAddedRemovedAndRenamedResources() throws Exception {
        Path p = Files.createDirectory(root.resolve("pack"));
        Path a = Files.writeString(p.resolve("a.json"), "{}");
        String first = hash(p);
        Path b = Files.writeString(p.resolve("b.json"), "{}");
        assertNotEquals(first, hash(p));
        Files.delete(b);
        assertEquals(first, hash(p));
        Files.move(a, p.resolve("new-name.json"));
        assertNotEquals(first, hash(p));
    }

    @Test void packOrderChangesFingerprint() throws Exception {
        Path a = Files.writeString(root.resolve("a"), "A");
        Path b = Files.writeString(root.resolve("b"), "B");
        assertNotEquals(hash(a, b), hash(b, a));
    }

    @Test void lengthPrefixesPreventConcatenationCollision() {
        var first = ResourceFingerprint.digest();
        var second = ResourceFingerprint.digest();
        List.of("a", "bc").forEach(s -> ResourceFingerprint.text(first, s));
        List.of("ab", "c").forEach(s -> ResourceFingerprint.text(second, s));
        assertNotEquals(ResourceFingerprint.finish(first), ResourceFingerprint.finish(second));
    }

    @Test void supportsNestedZipUriWithUnicodeSpacesAndPercent() throws Exception {
        Path p = root.resolve("[中文 UI] file%20+test.jar");
        try (var zip = FileSystems.newFileSystem(p, Map.of("create", "true"))) {
            Files.writeString(zip.getPath("/pack.mcmeta"), "{}");
            assertEquals(hash(zip.getPath("/")), hash(zip.getPath("/")));
        }
    }

    @Test void missingSourcesFailRatherThanReuseNameOnlyHash() {
        assertThrows(java.io.IOException.class, () -> hash(root.resolve("missing.zip")));
    }

    @Test void onlyCommittedNonemptyMetadataCanLoad() throws Exception {
        Path p = Files.createDirectory(root.resolve("cache"));
        Files.createDirectory(p.resolve("atlases"));
        assertFalse(DashCacheFiles.complete(p));
        Path metadata = Files.write(p.resolve("metadata.bin"), new byte[0]);
        assertFalse(DashCacheFiles.complete(p));
        Files.write(metadata, new byte[5]);
        assertFalse(DashCacheFiles.complete(p));
        Files.write(metadata, new byte[30]);
        assertTrue(DashCacheFiles.complete(p));
    }
}
