package io.wifi.starrailexpress.training;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 领域与地图/训练边界的接线回归，不替代真实服务器传送和画面验收。 */
class TrainingDomainIntegrationTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java", path));
    }

    @Test void environmentExemptsDomainMembersBeforeCoordinateAndHazardChecks() throws Exception {
        String world = source("io/wifi/starrailexpress/cca/SREGameWorldComponent.java");
        String detection = world.substring(world.indexOf("public static void playerEnvironmentDetection("),
                world.indexOf("public static boolean isInDarkness("));
        int domain = detection.indexOf("if (ModEffects.isInAnyDomain(player))");
        assertTrue(domain >= 0);
        assertTrue(domain < detection.indexOf("player.getZ() >= 19000"));
        assertTrue(domain < detection.indexOf("checkPlayerIsOutOfAreas(player, areas)"));
        String exempt = detection.substring(domain, detection.indexOf("if (!(player.getZ()"));
        assertTrue(exempt.contains("playerBannedBlockTime.remove(player.getUUID())"));
        assertTrue(exempt.contains("perPlayerDarknessTime.remove(player.getUUID())"));
        assertTrue(exempt.contains("return;"));
        assertFalse(detection.contains("TarotAssemblyManager.havingMeeting"));
        assertTrue(detection.contains("GameUtils.forceKillPlayer"));
    }

    @Test void membershipCoversMarkAndAuthoritativeDomainRecords() throws Exception {
        String effects = source("org/agmas/noellesroles/init/ModEffects.java");
        String membership = effects.substring(effects.indexOf("public static boolean isInAnyDomain("),
                effects.indexOf("public static boolean isInBackworldOrWraithDimension("));
        assertTrue(membership.contains("entity.hasEffect(DOMAIN_MARK)"));
        assertTrue(membership.contains("entity instanceof ServerPlayer player"));
        assertTrue(membership.contains("WarlockDomainManager.isInDomain(uuid)"));
        assertTrue(membership.contains("PoolerDomainManager.isInDomain(uuid)"));
        assertTrue(membership.contains("GroselleJourneyManager.isBanished(uuid)"));
        assertTrue(membership.contains("fool.inMeeting && fool.meetingOriginalPositions.containsKey(uuid)"));
        assertFalse(membership.contains("havingMeeting"));
    }

    @Test void trainingDoesNotReclaimDomainPositionOrReviveParticipants() throws Exception {
        String session = source("io/wifi/starrailexpress/training/TrainingSession.java");
        assertTrue(session.contains("owner.serverLevel() == level() && org.agmas.noellesroles.init.ModEffects.isInAnyDomain(owner)"));
        assertTrue(session.contains("if (!inDomain && (owner.serverLevel() != level()"));
        assertTrue(session.contains("if (!inDomain && !TrainingRules.isAlive(owner))"));
        String bots = source("io/wifi/starrailexpress/training/CarpetTrainingBots.java");
        int domain = bots.indexOf("org.agmas.noellesroles.init.ModEffects.isInAnyDomain(bot)");
        int alive = bots.indexOf("boolean alive = bot != null");
        assertTrue(domain >= 0 && domain < alive);
        String deferred = bots.substring(domain, alive);
        String controller = source("io/wifi/starrailexpress/training/TrainingBotController.java");
        assertTrue(controller.contains("ModEffects.isInAnyDomain(bot)"));
        assertTrue(controller.contains("ModEffects.isInAnyDomain(owner)"));
        assertTrue(deferred.contains("stop(entry)"));
        assertTrue(deferred.contains("entry.deadAt = -1"));
        assertTrue(deferred.contains("continue;"));
    }

    @Test void warlockUsesSameKillerLabelAndLayoutAsSwapper() throws Exception {
        String warlock = source("org/agmas/noellesroles/client/widget/WarlockDomainWidget.java");
        String swapper = source("org/agmas/noellesroles/client/widget/SwapperPlayerWidget.java");
        for (String shared : new String[]{"isVisibleKillerTeammate(", "hud.general.killer_friend",
                "ChatFormatting.GOLD", "0x80000000"}) {
            assertTrue(warlock.contains(shared), shared);
            assertTrue(swapper.contains(shared), shared);
        }
        assertTrue(warlock.contains("getRole(targetPlayer.getProfile().getId())"));
        assertTrue(warlock.contains("font.split(text, 50)"));
        assertTrue(warlock.contains("getY() + getHeight() + 4"));
    }
}
