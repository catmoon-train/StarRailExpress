package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 不启动Minecraft注册表的生命周期接线回归；真实联机行为另行验收。 */
class SackGhostLifecycleWiringTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles", path));
    }

    @Test void smoothRewindGuardPrecedesSpectatorDeathCleanupAndKeepsProgress() throws Exception {
        String role = source("role_data/killer/MaChenXuRoleData.java");
        String tick = role.substring(role.indexOf("@Override public void serverTick()"), role.indexOf("@Override public void clientTick()"));
        assertTrue(tick.indexOf("TimeRewind.isSmoothRewinding(owner)") < tick.indexOf("!GameUtils.isPlayerAliveAndSurvival(owner)"));
        assertTrue(tick.contains("cancelForRewind(); return;"));
        String cancel = role.substring(role.indexOf("public void cancelForRewind()"), role.indexOf("private Player rayTarget"));
        assertFalse(cancel.contains("stage ="));
        assertFalse(cancel.contains("clearOwnerArts"));
        assertTrue(cancel.contains("endOtherworld(false)"));
    }

    @Test void snapshotsFilterRealmInvulnerabilityAndPreserveCurrentRelicsOnBothRestoreExits() throws Exception {
        String rewind = source("api/time/TimeRewind.java");
        assertTrue(rewind.contains("TimeRewindEvents.CAPTURE.invoker().apply(player, vanillaState)"));
        assertTrue(rewind.indexOf("TimeRewindEvents.BEFORE_RESTORE.invoker()") < rewind.indexOf("moveToSnapshotDimension(player, snapshot, vanillaState)"));
        assertEquals(2, rewind.split(java.util.regex.Pattern.quote("TimeRewindEvents.AFTER_RESTORE.invoker()"), -1).length - 1);
        String bridge = source("game/roles/killer/ma_chen_xu/SackGhostRewind.java");
        assertTrue(bridge.contains("TimeRewindEvents.CAPTURE.register"));
        assertTrue(bridge.contains("TimeRewindEvents.BEFORE_RESTORE.register"));
        assertTrue(bridge.contains("TimeRewindEvents.AFTER_RESTORE.register"));
        assertTrue(bridge.contains("PRESERVED.remove(player.getUUID())"));
        assertTrue(bridge.contains("noellesroles:ghost_ward_relic"));
        String role = source("role_data/killer/MaChenXuRoleData.java");
        assertTrue(role.contains("state.putBoolean(\"Invulnerable\", previousInvulnerable)"));
        assertTrue(role.contains("previous = previousAt(target, effect, shared.get(effect))"));
    }

    @Test void protectedItemsAreExcludedFromEveryAreaRewindPhaseAndFromBundles() throws Exception {
        String area = source("api/time/TimeRewindAreaEngine.java");
        assertEquals(4, area.split(java.util.regex.Pattern.quote("TimeRewindEvents.ALLOW_AREA_ITEM.invoker().allow"), -1).length - 1);
        for (String item : new String[]{"GhostArtItem", "GhostWardRelicItem"}) {
            assertTrue(source("content/item/" + item + ".java").contains("canFitInsideContainerItems() { return false; }"));
        }
        String items = source("game/roles/killer/ma_chen_xu/SackGhostItems.java");
        assertTrue(items.contains("SackGhostRewind.register()"));
        assertTrue(items.contains("maintainWards(level, round)"));
    }
}
