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
                    "gui.noellesroles.identity_scanner.scan", "gui.noellesroles.identity_scanner.history",
                    "message.noellesroles.identity_scanner.result_target",
                    "message.noellesroles.identity_scanner.drop_cooldown",
                    "message.noellesroles.identity_scanner.neutral_forbidden")) assertTrue(lang.has(key), key);
            var intro = JsonParser.parseString(Files.readString(root.resolve("item_intro/lang/" + locale + ".json"))).getAsJsonObject();
            String itemText = intro.get("item.noellesroles.portable_identity_scanner.desc").getAsString();
            for (String number : List.of("5", "120", "125", "150")) assertTrue(itemText.contains(number), locale + number);
            String neutralWord = locale.equals("en_us") ? "Neutral" : "中立";
            assertTrue(itemText.contains(neutralWord));
            assertTrue(intro.get("block.noellesroles.identity_scanner.desc").getAsString().contains(neutralWord));
            var roles = JsonParser.parseString(Files.readString(root.resolve("role_modifier_intro/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : List.of("info.screen.roleid.coroner", "info.screen.roleid.coroner.simple")) {
                String text = roles.get(key).getAsString();
                for (String number : List.of("125", "150", "120")) assertTrue(text.contains(number), locale + key + number);
            }
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
    @Test void coronerDropLockStartsAtRoundStartOrFirstRoleEntryWithoutBuyingAnything() {
        var ledger = new IdentityScannerLedger();
        UUID initial = UUID.randomUUID(), converted = UUID.randomUUID();
        assertEquals(3000, ledger.coronerDropReadyAt(initial, 0));
        assertEquals(8000, ledger.coronerDropReadyAt(converted, 5000));
        assertEquals(3000, IdentityScannerLedger.remaining(ledger.coronerDropReadyAt(converted, 5000), 5000));
    }

    @Test void coronerDropDeadlineNeverRestartsAfterPurchasesReconnectsOrRoleChanges() {
        var ledger = new IdentityScannerLedger();
        UUID player = UUID.randomUUID();
        assertEquals(3000, ledger.coronerDropReadyAt(player, 0));
        for (long time : new long[] {100, 2999, 3000, 5000, 100_000}) {
            assertEquals(3000, ledger.coronerDropReadyAt(player, time));
        }
        assertEquals(1, IdentityScannerLedger.remaining(ledger.coronerDropReadyAt(player, 2999), 2999));
        assertEquals(0, IdentityScannerLedger.remaining(ledger.coronerDropReadyAt(player, 3000), 3000));
        assertEquals(0, IdentityScannerLedger.remaining(ledger.coronerDropReadyAt(player, 100_000), 100_000));
    }

    @Test void coronerDropRestrictionIsSeparateFromScanningAndOtherPlayers() {
        var ledger = new IdentityScannerLedger();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(3000, ledger.coronerDropReadyAt(first, 0));
        assertEquals(4000, ledger.coronerDropReadyAt(second, 1000));
        ledger.portableScanned(first, 1100);
        assertEquals(2400, ledger.portableRemaining(first, 1100));
        assertEquals(1900, IdentityScannerLedger.remaining(ledger.coronerDropReadyAt(first, 1100), 1100));
        assertEquals(0, ledger.portableRemaining(second, 1100));
    }

    @Test void newRoundRegistersANewInitialDropRestriction() {
        UUID player = UUID.randomUUID();
        var first = new IdentityScannerLedger();
        assertEquals(4000, first.coronerDropReadyAt(player, 1000));
        var next = new IdentityScannerLedger();
        assertEquals(3000, next.coronerDropReadyAt(player, 0));
        assertEquals(3000, IdentityScannerLedger.remaining(next.coronerDropReadyAt(player, 0), 0));
    }

    @Test void coronerDropDeadlineHandlesPreparationTimeAndExpiredLimits() {
        var ledger = new IdentityScannerLedger();
        UUID player = UUID.randomUUID();
        assertEquals(3000, ledger.coronerDropReadyAt(player, -20));
        assertEquals(0, IdentityScannerLedger.remaining(ledger.coronerDropReadyAt(player, 3001), 3001));
        assertEquals(150 * 20, IdentityScannerLedger.CORONER_DROP_LOCK_TICKS);
    }

    @Test void portableResultsUsePrivateTitlePacketsAndVisibleFailureMessages() throws Exception {
        String service = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/scanner/IdentityScannerService.java"));
        String portable = service.substring(service.indexOf("public static void scanPortable"),
                service.indexOf("public static long coronerDropReadyAt"));
        assertTrue(portable.contains("SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20)"));
        assertTrue(portable.contains("SRENetworkMessageUtils.sendTitle(player,"));
        assertTrue(portable.contains("SRENetworkMessageUtils.sendSubtitle(player,"));
        assertTrue(portable.contains("role.color()"));
        assertFalse(portable.contains("message(player, \"result\""));
        assertFalse(portable.contains("sendSystemMessage"));
        assertTrue(portable.contains("portableMessage(player, \"invalid_body\")"));
        assertTrue(portable.contains("portableMessage(player, \"cooldown\""));
    }

    @Test void coronerShopAndDropRulesApplyOnlyToThePortableItem() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String role = Files.readString(root.resolve("role/CoronerRole.java"));
        assertEquals(125, org.agmas.noellesroles.role.CoronerRole.SCANNER_PRICE);
        assertTrue(role.contains("ModItems.PORTABLE_IDENTITY_SCANNER.getDefaultInstance()"));
        assertTrue(role.contains("SCANNER_PRICE, ShopEntry.Type.TOOL"));
        assertTrue(role.contains("RoleData.getNullable(CoronerRoleData.class, player)"));
        assertTrue(role.contains("remaining > 0"));
        assertTrue(role.contains("return InteractionResult.FAIL"));
        assertTrue(role.contains("return InteractionResult.SUCCESS"));
        String drops = Files.readString(root.resolve("init/events/NRRulePredicateEvents.java"));
        assertTrue(drops.contains("\"noellesroles:portable_identity_scanner\""));
        assertFalse(drops.contains("\"noellesroles:identity_scanner\""));
        String registration = Files.readString(root.resolve("role/ModRoles.java"));
        assertTrue(registration.contains("new CoronerRole(CORONER_ID"));
    }

    @Test void portableItemIsHiddenInEitherHandWithoutHidingItsInventoryOrGroundModel() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String renderer = Files.readString(root.resolve("client/InvisbleHandItem.java"));
        int hidden = renderer.indexOf("itemStack.is(ModItems.PORTABLE_IDENTITY_SCANNER)");
        assertTrue(hidden >= 0);
        assertTrue(renderer.substring(hidden, renderer.indexOf("});", hidden)).contains("return ItemStack.EMPTY"));
        assertFalse(renderer.substring(renderer.indexOf("public static void register()"), hidden).contains("if (!mainHand)"));
        var model = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/noellesroles/models/item/portable_identity_scanner.json"))).getAsJsonObject();
        assertTrue(model.has("textures"));
    }
    @Test void everyNeutralFactionVariantIsDeniedWhileNonNeutralFactionsRemainAllowed() {
        for (int flags = 0; flags < 8; flags++) {
            assertEquals(flags == 0, IdentityScannerAccess.allowsFaction(
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
        }
    }

    @Test void neutralPermissionIsCheckedBeforeReturningHistoryChargingOrStartingCooldown() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/scanner/IdentityScannerService.java"));
        String operate = source.substring(source.indexOf("private static boolean canOperate"),
                source.indexOf("private static boolean currentlyDead"));
        assertTrue(operate.contains("IdentityScannerAccess.canUse(player)"));
        String machine = source.substring(source.indexOf("private static IdentityScannerBlockEntity machine"),
                source.indexOf("public static void open"));
        assertTrue(machine.contains("!IdentityScannerAccess.canUse(player)"));
        String open = source.substring(source.indexOf("public static void open"), source.indexOf("public static void handleRequest"));
        assertTrue(open.indexOf("!IdentityScannerAccess.canUse(player)") >= 0);
        assertTrue(open.indexOf("!IdentityScannerAccess.canUse(player)") < open.indexOf("VIEWERS.put"));
        String request = source.substring(source.indexOf("public static void handleRequest"), source.indexOf("private static void scanRandom"));
        assertTrue(request.contains("if (machine == null) { VIEWERS.remove(player.getUUID()); return; }"));
        assertTrue(request.indexOf("machine(player, pos)") < request.indexOf("scanRandom(player, machine)"));
        String portable = source.substring(source.indexOf("public static void scanPortable"), source.indexOf("public static long coronerDropReadyAt"));
        assertTrue(portable.indexOf("!IdentityScannerAccess.canUse(player)") >= 0);
        assertTrue(portable.indexOf("!IdentityScannerAccess.canUse(player)") < portable.indexOf("IdentityScannerLedger ledger"));
        assertTrue(portable.contains("portableMessage(player, \"neutral_forbidden\")"));
    }

    @Test void changingToANeutralRoleClosesTheExistingTerminalScreen() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles");
        String client = Files.readString(root.resolve("client/IdentityScannerClient.java"));
        String canView = client.substring(client.indexOf("public static boolean canView"));
        assertTrue(canView.contains("IdentityScannerAccess.canUse(client.player)"));
        assertTrue(client.contains("if (!screen.isValid(client))"));
        assertTrue(client.contains("client.setScreen(null)"));
        String screen = Files.readString(root.resolve("client/screen/IdentityScannerScreen.java"));
        assertTrue(screen.contains("IdentityScannerClient.canView(client, pos)"));
    }
}
