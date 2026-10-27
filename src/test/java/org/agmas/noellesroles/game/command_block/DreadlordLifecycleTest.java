package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;
import org.junit.jupiter.api.Test;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.nbt.CompoundTag;

/** 无图形环境下验证状态复原和客户端清理入口，实际网络与网格效果仍需多人验收。 */
class DreadlordLifecycleTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles", relative));
    }

    @Test
    void syncedEndStateClearsKillingAndDarkAndClearResetsThem() {
        DreadlordRoleData data = new DreadlordRoleData(new RoleDataContext(null, null, null, null));
        CompoundTag active = new CompoundTag();
        active.putBoolean("Killing", true);
        active.putBoolean("Dark", true);
        active.putInt("KillLeft", 15);
        data.readFromSyncNbt(active, null);
        assertTrue(data.isKilling());
        assertTrue(data.isDark());
        data.readFromSyncNbt(new CompoundTag(), null);
        assertFalse(data.isKilling());
        assertFalse(data.isDark());
        data.readFromSyncNbt(active, null);
        data.clear();
        assertFalse(data.isKilling());
        assertFalse(data.isDark());
    }

    @Test
    void endingDoesNotRevokePreviouslyPublishedFormSynchronization() throws Exception {
        String text = source("role_data/killer/DreadlordRoleData.java");
        assertTrue(text.contains("return publicFormSeen || killing || dark || other == this.player;"));
        assertEquals(2, text.split("publicFormSeen = true;", -1).length - 1);
        assertFalse(text.contains("publicFormSeen = false"));
        String ending = text.substring(text.indexOf("private void leaveKilling"), text.indexOf("private void enterDark"));
        assertTrue(ending.indexOf("killing = false;") < ending.indexOf("sync();"));
        assertTrue(ending.contains("setScale(self, 1.0D);"));
    }

    @Test
    void frenzyExpirationRunsBeforeDarkModeEarlyReturn() throws Exception {
        String text = source("role_data/killer/DreadlordRoleData.java");
        String tick = text.substring(text.indexOf("public void serverTick()"), text.indexOf("public void onHit()"));
        assertTrue(tick.indexOf("killLeft--;") < tick.indexOf("tickDark(self, level);"));
        assertTrue(tick.contains("getPsychoTicks() <= 0 || killLeft <= 0"));
        assertEquals(1, text.split("killLeft--;", -1).length - 1);
        assertTrue(tick.substring(0, tick.indexOf("tickStun(self);")).contains("leaveKilling(self, level);"));
    }

    @Test
    void floatingBlocksAreReleasedWhenTheirOwnerStopsOrDisappears() throws Exception {
        String fx = source("client/DreadlordClientFx.java");
        assertTrue(fx.contains("!active.contains(block.owner) || block.age >= 50"));
        assertTrue(fx.contains("new FloatBlock(at, state, source.getUUID())"));
        assertTrue(fx.contains("other.isAlive() && !other.isSpectator()"));
        assertTrue(fx.contains("floatLevel != client.level"));
        assertTrue(fx.contains("ClientPlayConnectionEvents.DISCONNECT.register"));
    }

    @Test
    void blockWorkersReadImmutableSnapshotAndDirtyCoordinatesAreSections() throws Exception {
        String fx = source("client/DreadlordClientFx.java");
        assertTrue(fx.contains("volatile Set<Long> hiddenBlocks"));
        assertTrue(fx.contains("Collectors.toUnmodifiableSet()"));
        String flush = fx.substring(fx.indexOf("private static void flushDirty"));
        assertTrue(flush.indexOf("hiddenBlocks = FLOATS.stream()") < flush.indexOf("setSectionDirty("));
        assertTrue(fx.contains("SectionPos.of(pos)"));
        assertTrue(fx.contains("SectionPos.of(pos.relative(direction))"));
        assertTrue(fx.contains("setSectionDirty(section.x(), section.y(), section.z())"));
        assertFalse(fx.contains("setSectionDirty(block.pos.getX()"));
        assertFalse(fx.contains("setSectionDirty(at.getX()"));
    }

    @Test
    void cleanupTickIsIndependentOfLocalAdventureMode() throws Exception {
        String client = source("client/NoellesrolesClient.java");
        assertEquals(1L, client.lines().filter(line -> line.contains("DreadlordClientFx.register();")).count());
        assertFalse(client.contains("DreadlordClientFx.tickFloats(client);"));
        String fx = source("client/DreadlordClientFx.java");
        assertTrue(fx.contains("ClientTickEvents.END_CLIENT_TICK.register(DreadlordClientFx::tickFloats)"));
    }

    @Test
    void darkDurationAndCooldownAreIndependentOfFrenzy() throws Exception {
        assertEquals(450, DreadlordRole.KILL_FORM_TICKS);
        assertEquals(200, DreadlordRole.DARK_DURATION);
        assertEquals(30 * 20, DreadlordRole.DARK_COOLDOWN);
        String text = source("role_data/killer/DreadlordRoleData.java");
        assertTrue(text.contains("darkUntil = self.tickCount + DreadlordRole.DARK_DURATION;"));
        assertTrue(text.contains("darkReadyAt = self.tickCount + DreadlordRole.DARK_COOLDOWN;"));
        String tick = text.substring(text.indexOf("private void tickDark"), text.indexOf("private void leaveDark"));
        assertTrue(tick.indexOf("self.tickCount >= darkUntil") < tick.indexOf("self.addEffect("));
        assertTrue(tick.contains("leaveDark(self, false);"));
        String leave = text.substring(text.indexOf("private void leaveDark"), text.indexOf("private static void setScale"));
        assertTrue(leave.contains("darkUntil = 0;"));
        assertTrue(leave.contains("self.removeEffect(MobEffects.MOVEMENT_SPEED);"));
        assertTrue(leave.contains("self.removeEffect(ModEffects.INVINCIBLE);"));
        assertTrue(leave.contains("if (killing && killLeft > 0)"));
    }

    @Test
    void localizedDescriptionsMatchCurrentDarkAndHitStunTimings() throws Exception {
        for (String locale : new String[]{"zh_cn", "zh_tw", "en_us"}) {
            var json = com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/role_modifier_intro/lang/" + locale + ".json"))).getAsJsonObject();
            String intro = json.get("info.screen.roleid.dreadlord").getAsString();
            for (int seconds : new int[]{DreadlordRole.DARK_DURATION / 20,
                    DreadlordRole.DARK_COOLDOWN / 20, DreadlordRole.KILLING_HIT_STUN / 20}) {
                assertTrue(java.util.regex.Pattern.compile("(?<![\\d.])" + seconds + "\\s*(?:s\\b|秒)")
                        .matcher(intro).find(), locale + ": missing " + seconds + "s in Dreadlord description");
            }
        }
    }
}
