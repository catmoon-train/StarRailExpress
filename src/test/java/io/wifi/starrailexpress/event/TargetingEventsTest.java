package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.client.gui.screen.ingame.PlayerPaginationHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetingEventsTest {
    private static final Path JAVA = Path.of("src/main/java");

    @Test void targetAndPickupGatesDefaultToAllow() {
        assertTrue(TargetingEvents.targetGate().invoker().allow(null, null));
        assertTrue(ItemGameplayEvents.pickupGate().invoker().allow(null, null));
    }

    @Test void targetRejectionShortCircuitsAndCannotBeOverridden() {
        var gate = TargetingEvents.targetGate();
        List<Integer> calls = new ArrayList<>();
        gate.register((actor, target) -> { calls.add(1); return true; });
        gate.register((actor, target) -> { calls.add(2); return false; });
        gate.register((actor, target) -> { fail("否决后不得继续派发"); return true; });
        assertFalse(gate.invoker().allow(null, null));
        assertEquals(List.of(1, 2), calls);
    }

    @Test void rejectingFrontCandidateStillLetsTheRayChooseTheNextCandidate() {
        var gate = TargetingEvents.targetGate();
        AtomicInteger queries = new AtomicInteger();
        gate.register((actor, target) -> queries.getAndIncrement() != 0);
        int selected = IntStream.range(0, 2).filter(index -> gate.invoker().allow(null, null))
                .findFirst().orElse(-1);
        assertEquals(1, selected);
        assertEquals(2, queries.get());
    }

    @Test void worldPickupVetoStopsSpecialPickupHandling() {
        var gate = ItemGameplayEvents.pickupGate();
        gate.register((player, stack) -> false);
        gate.register((player, stack) -> { fail("禁止拾取必须短路"); return true; });
        assertFalse(gate.invoker().allow(null, null));
    }

    @Test void headFilterRunsBeforeSearchAndSurvivesListRefreshes() {
        var pagination = new PlayerPaginationHelper<String>((screen, x, y, entry, index) -> null, null);
        pagination.setEntryFilter(entry -> !entry.equals("immune"));
        pagination.setNameExtractor(entry -> entry);
        pagination.setPlayerEntries(List.of("immune", "allowed"));
        pagination.setSearchQuery("imm");
        assertTrue(pagination.getVisibleEntries().isEmpty());
        pagination.setSearchQuery("");
        pagination.setPlayerEntries(List.of("immune", "second", "allowed"));
        assertEquals(List.of("allowed", "second"), pagination.getVisibleEntries());
    }

    @Test void targetedDispatchChecksBothLegacyAndUnifiedBeforeHandlersOrCosts() throws Exception {
        String skill = read("io/wifi/starrailexpress/api/RoleSkill.java");
        int begin = skill.indexOf("boolean shifted, boolean ignoreEffect) {");
        int gate = skill.indexOf("TargetingEvents.ALLOW_SKILL_TARGET", begin);
        assertTrue(begin >= 0 && gate > begin);
        assertTrue(gate < skill.indexOf("return useUnified(", begin));
        assertTrue(gate < skill.indexOf("if (!beforeUse(player, role))", begin));
        assertTrue(gate < skill.indexOf("consumer.accept(", begin));
        assertTrue(skill.substring(begin, gate).contains("phase != Phase.RELEASE"));
        String sessions = read("org/agmas/noellesroles/game/roles/neutral/roulette_devil/RouletteDevilSessions.java");
        int challenge = sessions.indexOf("public static boolean challenge(");
        int challengeGate = sessions.indexOf("TargetingEvents.ALLOW_SKILL_TARGET", challenge);
        assertTrue(challenge >= 0 && challengeGate > challenge);
        assertTrue(challengeGate < sessions.indexOf("RouletteDevilRoom.create(", challenge));
    }

    @Test void gunSeamsFilterCandidatesRatherThanCancelingAnAlreadyChosenHit() throws Exception {
        String hit = read("io/wifi/starrailexpress/api/hit/SREHitManager.java");
        int resolve = hit.indexOf("public static HitPriority resolvePriority(");
        int gate = hit.indexOf("TargetingEvents.ALLOW_GUN_TARGET", resolve);
        assertTrue(gate > resolve && gate < hit.indexOf("entity instanceof Player player", resolve));
        assertTrue(hit.substring(resolve, gate).contains("type.isRanged()"));
        String client = read("org/agmas/noellesroles/client/events/ClientRoleEventHandlers.java");
        int gun = client.indexOf("private static HitResult getGunTarget(");
        assertTrue(client.indexOf("entity -> io.wifi.starrailexpress.event.TargetingEvents.ALLOW_GUN_TARGET", gun) > gun);
        String standard = read("io/wifi/starrailexpress/content/item/StandardRevolverItem.java");
        assertTrue(standard.contains("if (!io.wifi.starrailexpress.event.TargetingEvents.ALLOW_GUN_TARGET"));
        String sniper = read("io/wifi/starrailexpress/content/item/SniperRifleItem.java");
        assertTrue(sniper.contains("entity -> io.wifi.starrailexpress.event.TargetingEvents.ALLOW_GUN_TARGET"));
        String custom = read("io/wifi/starrailexpress/customitem/CustomItemRuntime.java");
        assertTrue(custom.contains("getSniperHitResult(shooter, gunTargets, data.gunRange)"));
        assertTrue(custom.contains("getHitResultOnViewVector(shooter, gunTargets, data.gunRange)"));
    }

    @Test void passivesRemainActiveInDuelsWithoutWallPhasingOrBlockingToolGrants() throws Exception {
        String role = read("org/agmas/noellesroles/game/roles/neutral/roulette_devil/RouletteDevilRole.java");
        int tick = role.indexOf("public static void tick(");
        assertTrue(role.indexOf("refreshPassives(p);", tick) < role.indexOf("boolean duel =", tick));
        assertTrue(role.contains("removePassives(p);"));
        assertTrue(role.contains("onPickUpItem(Player player, ItemStack item) {\n        return TrueFalseResult.FALSE;"));
        assertFalse(role.contains("noPhysics"));
        String pickup = read("io/wifi/starrailexpress/mixin/entity/item/ItemEntityMixin.java");
        assertTrue(pickup.indexOf("ALLOW_WORLD_PICKUP") < pickup.indexOf("getMurderGoldAmount("));
        String helper = read("io/wifi/starrailexpress/client/gui/screen/ingame/RoleScreenHelper.java");
        assertTrue(helper.contains("setEntryFilter(this::isSkillTargetEntry)"));
        assertTrue(helper.contains("paginationHelper.refreshPage(screen)"));
        String effects = read("org/agmas/noellesroles/init/ModEffects.java");
        assertTrue(effects.contains("EffectBroadcastSync.register(GUN_TARGET_IMMUNITY)"));
        assertTrue(effects.contains("EffectBroadcastSync.register(SKILL_TARGET_IMMUNITY)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(JAVA.resolve(path)).replace("\r\n", "\n");
    }
}
