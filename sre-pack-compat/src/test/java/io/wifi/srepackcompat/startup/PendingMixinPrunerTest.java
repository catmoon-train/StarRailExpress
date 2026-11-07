package io.wifi.srepackcompat.startup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PendingMixinPrunerTest {
    private static final String PKG = "dev.doctor4t.ratatouille.mixin";
    private static final SreMixinCancellationPolicy POLICY = new SreMixinCancellationPolicy(true, true);
    private static final Map<String, Set<String>> VERIFIED = Map.of("test.json", Set.of(PKG + ".Remove", PKG + ".ClientRemove",
            PKG + ".client.BlockRenderManagerAccessor"));

    static class Config implements PendingMixinPruner.ConfigView {
        String pkg = PKG;
        boolean prepared, failRead, failWrite, failRollback, changeOnRead;
        int commonReads, writes;
        List<String> common = List.of("Remove"), client = List.of("ClientRemove"), server = List.of("Remove");
        @Override public String name() { return "test.json"; }
        @Override public String mixinPackage() { return pkg; }
        @Override public boolean prepared() { return prepared; }
        @Override public List<String> declarations(boolean sided) throws IllegalAccessException {
            if (failRead) throw new IllegalAccessException("test read");
            if (!sided && changeOnRead && ++commonReads == 2) common = new ArrayList<>(common);
            return sided ? client : common;
        }
        @Override public void declarations(boolean sided, List<String> values) throws IllegalAccessException {
            if (failWrite || failRollback && writes > 0) throw new IllegalAccessException("test write");
            if (sided) client = values; else common = values;
            writes++;
        }
    }

    @Test
    void preservesOrderNullsDuplicatesOriginalListsAndAccessor() throws Exception {
        Config config = new Config();
        config.common = Arrays.asList("KeepA", "Remove", null, "KeepB", "Remove", "KeepA");
        config.client = List.of("ClientRemove", "client.BlockRenderManagerAccessor", "UnknownAccessor");
        var common = config.common;
        var client = config.client;
        var server = config.server;
        var result = PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY);
        assertEquals(3, result.removed().size());
        assertEquals(1, result.configs());
        assertEquals(Arrays.asList("KeepA", null, "KeepB", "KeepA"), config.common);
        assertEquals(List.of("client.BlockRenderManagerAccessor", "UnknownAccessor"), config.client);
        assertEquals(6, common.size());
        assertEquals(3, client.size());
        assertSame(server, config.server);
        assertThrows(UnsupportedOperationException.class, () -> config.common.add("mutate"));
    }

    @Test
    void packageWithTrailingDotAndRepeatedInstallAreSafe() throws Exception {
        Config config = new Config(); config.pkg += ".";
        assertEquals(2, PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY).removed().size());
        var original = config.common;
        assertTrue(PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY).removed().isEmpty());
        assertSame(original, config.common);
    }

    @Test
    void preparedUnknownConfigAndUnverifiedRuleRemainUntouched() throws Exception {
        Config config = new Config(); var original = config.common;
        config.prepared = true;
        assertTrue(PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY).removed().isEmpty());
        config.prepared = false;
        assertTrue(PendingMixinPruner.apply(List.of(config), Map.of(), POLICY).removed().isEmpty());
        assertTrue(PendingMixinPruner.apply(List.of(config), VERIFIED, new SreMixinCancellationPolicy(false, false)).removed().isEmpty());
        assertSame(original, config.common);
        assertEquals(0, config.writes);
    }

    @Test
    void unknownMixinEvenInCancelledNamespaceRemainsUntouched() throws Exception {
        Config config = new Config(); config.common = List.of("NewMixin", "Remove"); config.client = null;
        PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY);
        assertEquals(List.of("NewMixin"), config.common);
        assertNull(config.client);
    }

    @Test
    void readFailureNeverCommitsAnyConfig() {
        Config first = new Config(), second = new Config(); second.failRead = true;
        var original = first.common;
        assertThrows(IllegalAccessException.class, () -> PendingMixinPruner.apply(List.of(first, second), VERIFIED, POLICY));
        assertSame(original, first.common);
        assertEquals(0, first.writes);
    }

    @Test
    void writeFailureRestoresBothSidesOfEarlierConfigsByIdentity() {
        Config first = new Config(), second = new Config(); second.failWrite = true;
        var common = first.common; var client = first.client;
        assertThrows(IllegalAccessException.class, () -> PendingMixinPruner.apply(List.of(first, second), VERIFIED, POLICY));
        assertSame(common, first.common);
        assertSame(client, first.client);
    }

    @Test
    void cannotSilentlyFallbackAfterFailedRollback() {
        Config first = new Config(), second = new Config(); first.failRollback = true; second.failWrite = true;
        assertThrows(PendingMixinPruner.RollbackFailure.class,
                () -> PendingMixinPruner.apply(List.of(first, second), VERIFIED, POLICY));
    }

    @Test
    void refusesConcurrentReplacementBeforeCommit() {
        Config config = new Config(); config.changeOnRead = true;
        assertThrows(IllegalStateException.class, () -> PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY));
        assertEquals(0, config.writes);
        assertEquals(List.of("Remove"), config.common);
    }

    @Test
    void absentPackageDoesNotTurnRelativeNamesIntoCandidates() throws Exception {
        Config config = new Config(); config.pkg = "";
        assertTrue(PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY).removed().isEmpty());
        config.pkg = null;
        assertTrue(PendingMixinPruner.apply(List.of(config), VERIFIED, POLICY).removed().isEmpty());
        assertEquals(0, config.writes);
    }
}
