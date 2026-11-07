package org.agmas.noellesroles.game.scanner;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityScannerTest {
    private IdentityScannerLedger.Identity dead(UUID uuid, String role) {
        return new IdentityScannerLedger.Identity(uuid, "Player", role, 0x55FF55);
    }
    @Test void seenPlayersDoNotRepeatAcrossMachinesButOtherUsersHaveIndependentPools() {
        var ledger = new IdentityScannerLedger();
        UUID dead = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        ledger.recordDeath(dead(dead, "sre:civilian"));
        ledger.markScanned(first, dead);
        assertTrue(ledger.candidates(first, id -> true).isEmpty());
        assertEquals(1, ledger.candidates(second, id -> true).size());
    }
    @Test void sameRoleOnDifferentDeadPlayersCanBothBeScanned() {
        var ledger = new IdentityScannerLedger();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), viewer = UUID.randomUUID();
        ledger.recordDeath(dead(first, "sre:civilian"));
        ledger.recordDeath(dead(second, "sre:civilian"));
        ledger.markScanned(viewer, first);
        assertEquals(List.of(dead(second, "sre:civilian")), ledger.candidates(viewer, id -> true));
    }
    @Test void revivedPlayersAreExcludedWithoutLosingDeathSnapshot() {
        var ledger = new IdentityScannerLedger();
        UUID dead = UUID.randomUUID(), viewer = UUID.randomUUID();
        ledger.recordDeath(dead(dead, "sre:killer"));
        assertTrue(ledger.candidates(viewer, id -> false).isEmpty());
        assertEquals(1, ledger.candidates(viewer, id -> true).size());
    }
    @Test void repeatedDeathUpdatesRoleWithoutCreatingDuplicatesOrResettingSeen() {
        var ledger = new IdentityScannerLedger();
        UUID dead = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        ledger.recordDeath(dead(dead, "sre:civilian"));
        ledger.markScanned(first, dead);
        ledger.recordDeath(dead(dead, "sre:killer"));
        assertTrue(ledger.candidates(first, id -> true).isEmpty());
        assertEquals(1, ledger.candidates(second, id -> true).size());
        assertEquals("sre:killer", ledger.death(dead).roleId());
    }
    @Test void previewingCandidatesNeverMarksThemSeen() {
        var ledger = new IdentityScannerLedger();
        UUID dead = UUID.randomUUID(), viewer = UUID.randomUUID();
        ledger.recordDeath(dead(dead, "sre:civilian"));
        assertEquals(ledger.candidates(viewer, id -> true), ledger.candidates(viewer, id -> true));
        assertEquals(1, ledger.candidates(viewer, id -> true).size());
    }
    @Test void portableCooldownIsPerPlayerAndDoesNotConsumeRandomScanQuota() {
        var ledger = new IdentityScannerLedger();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), dead = UUID.randomUUID();
        ledger.recordDeath(dead(dead, "sre:civilian"));
        ledger.portableScanned(first, 100);
        assertEquals(2400, ledger.portableRemaining(first, 100));
        assertEquals(1, ledger.portableRemaining(first, 2499));
        assertEquals(0, ledger.portableRemaining(first, 2500));
        assertEquals(0, ledger.portableRemaining(second, 100));
        assertEquals(1, ledger.candidates(first, id -> true).size());
    }
    @Test void nextRoundHasIndependentIdentitySeenAndCooldownState() {
        var first = new IdentityScannerLedger();
        UUID viewer = UUID.randomUUID(), dead = UUID.randomUUID();
        first.recordDeath(dead(dead, "sre:killer"));
        first.markScanned(viewer, dead); first.portableScanned(viewer, 0);
        var next = new IdentityScannerLedger();
        assertNotEquals(first.roundId(), next.roundId());
        assertNull(next.death(dead));
        assertEquals(0, next.portableRemaining(viewer, 0));
        next.recordDeath(dead(dead, "sre:killer"));
        assertEquals(1, next.candidates(viewer, id -> true).size());
    }
    @Test void deadlinesRespectPausedClockAndClampExpiredValues() {
        assertEquals(600, IdentityScannerLedger.remaining(700, 100));
        assertEquals(600, IdentityScannerLedger.remaining(700, 100));
        assertEquals(0, IdentityScannerLedger.remaining(700, 701));
        assertEquals(Integer.MAX_VALUE, IdentityScannerLedger.remaining(3_000_000_000L, 0));
    }
    @Test void transactionValidationPrecedesChargingAndMachinesKeepIndependentDeadlines() throws Exception {
        Path source = Path.of("src/main/java/org/agmas/noellesroles");
        String service = Files.readString(source.resolve("game/scanner/IdentityScannerService.java"));
        int scan = service.indexOf("private static void scanRandom");
        int cost = service.indexOf("MoneyUtils.cost(player, 50)", scan);
        assertTrue(service.indexOf("machine.remaining(now)", scan) < cost);
        assertTrue(service.indexOf("candidates.isEmpty()", scan) < cost);
        assertTrue(service.indexOf("ledger.markScanned", scan) > cost);
        String machine = Files.readString(source.resolve("content/block_entity/IdentityScannerBlockEntity.java"));
        assertTrue(machine.contains("private long readyAt"));
        assertTrue(machine.contains("readyAt = now + 30 * 20"));
        assertTrue(machine.contains("tag.putLong(\"ScannerReadyAt\", readyAt)"));
        assertTrue(machine.contains("history.clear()"));
    }
    @Test void translationsModelsAndGeneratedTexturesExist() throws Exception {
        Path root = Path.of("src/main/resources/assets");
        for (String locale : List.of("zh_cn", "zh_tw", "en_us")) {
            var lang = JsonParser.parseString(Files.readString(root.resolve("noellesroles/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : List.of("block.noellesroles.identity_scanner", "item.noellesroles.portable_identity_scanner",
                    "gui.noellesroles.identity_scanner.scan", "gui.noellesroles.identity_scanner.history")) assertTrue(lang.has(key), key);
            var intro = JsonParser.parseString(Files.readString(root.resolve("item_intro/lang/" + locale + ".json"))).getAsJsonObject();
            assertTrue(intro.has("item.noellesroles.portable_identity_scanner.desc"));
        }
        for (String face : List.of("front", "side", "top", "bottom")) {
            var image = ImageIO.read(root.resolve("noellesroles/textures/block/identity_scanner_" + face + ".png").toFile());
            assertEquals(64, image.getWidth()); assertEquals(64, image.getHeight());
            assertFalse(image.getColorModel().hasAlpha());
        }
        var item = ImageIO.read(root.resolve("noellesroles/textures/item/portable_identity_scanner.png").toFile());
        assertEquals(64, item.getWidth()); assertTrue(item.getColorModel().hasAlpha());
        assertEquals(0, (item.getRGB(0, 0) >>> 24) & 255);
        var states = JsonParser.parseString(Files.readString(root.resolve("noellesroles/blockstates/identity_scanner.json"))).getAsJsonObject();
        assertEquals(4, states.getAsJsonObject("variants").size());
    }
}
