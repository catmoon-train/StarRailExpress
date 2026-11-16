package io.wifi.sreperf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceResolverTest {
    @Test void observerAndDispatchInfrastructureAreNotBlamedAsMods() {
        assertTrue(SourceResolver.infrastructureId("sre_performance"));
        assertTrue(SourceResolver.infrastructureId("mixinextras"));
        assertTrue(SourceResolver.infrastructureId("fabric-screen-api-v1"));
        assertTrue(SourceResolver.infrastructureId("fabricloader"));
        assertFalse(SourceResolver.infrastructureId("iris"));
        assertFalse(SourceResolver.infrastructureId("starrailexpress"));
    }

    @Test void jarAndExplodedUrlsNormalizeWithoutDecodingPercentOrPlus() {
        assertEquals("file:/G:/mods/a%20b+c.jar!/", SourceResolver.normalize("jar:file:///G:/mods/a%20b+c.jar!/"));
        assertEquals("file:/G:/classes/", SourceResolver.normalize("file:/G:/classes/"));
    }
}
