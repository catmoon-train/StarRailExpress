package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 做客状态（拜访被同意进入别队避难所后）：
 * <ul>
 *   <li>访客全程持有 {@code USED_BANED}（每 tick 续期）——客户端按键抑制使其无法攻击/丢弃/使用物品与方块
 *       （{@code MobEffectKeyMixin}），<b>唯独放行避难所门</b>（离开/对话都在门上操作）。</li>
 *   <li>访客右键任意避难所门 → 门菜单只给「回自己的避难所」「与主人对话」（见 {@code SixtySecondsDoorMenu}）。
 *       离开=安全落点传回<b>自己队</b>的避难所内，而不是把人丢在别队门外。</li>
 *   <li>访客与家庭成员互不可攻击由跨队 PvP 禁令（{@code SixtySecondsHealthSystem.isPvpBlocked}）天然覆盖。</li>
 *   <li>倒地/淘汰/变怪自动解除做客（不传送——人已倒了，交由倒地/死亡流程处理）。</li>
 * </ul>
 */
public final class SixtySecondsVisiting {
    /** 访客 UUID → 做客的主队 teamId。 */
    private static final Map<UUID, Integer> VISITS = new HashMap<>();

    private SixtySecondsVisiting() {
    }

    public static boolean isVisiting(ServerPlayer player) {
        return VISITS.containsKey(player.getUUID());
    }

    /** 拜访被同意、传送进主队避难所后调用：登记做客关系并立刻上交互限制。 */
    public static void start(ServerPlayer visitor, int hostTeamId) {
        VISITS.put(visitor.getUUID(), hostTeamId);
        applyRestriction(visitor);
        visitor.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.visit_guest_hint"), false);
    }

    /** 每 tick：给做客者续期 USED_BANED（40 tick 时长，跨传送/丢包自动恢复）；倒地/淘汰/变怪自动解除。 */
    public static void tick(ServerLevel level) {
        if (VISITS.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!VISITS.containsKey(player.getUUID())) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (!GameUtils.isPlayerAliveAndSurvival(player) || stats.downed || stats.monster) {
                VISITS.remove(player.getUUID());
                player.removeEffect(ModEffects.USED_BANED);
                SixtySecondsVisitChat.end(player.getUUID());
                continue;
            }
            applyRestriction(player);
        }
    }

    /** 访客点门「离开」：解除限制 + 结束对话 + 安全落点传回自己队的避难所内。 */
    public static void leave(ServerPlayer visitor) {
        if (VISITS.remove(visitor.getUUID()) == null) {
            return;
        }
        visitor.removeEffect(ModEffects.USED_BANED);
        SixtySecondsVisitChat.end(visitor.getUUID());
        ServerLevel level = visitor.serverLevel();
        SixtySecondsState.TeamData home = SixtySecondsState.get(level).teams
                .get(SixtySecondsStatsComponent.KEY.get(visitor).teamId);
        if (home != null && home.shelterSpawn != null) {
            BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, home.shelterSpawn);
            visitor.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    visitor.getYRot(), visitor.getXRot());
            net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(
                    visitor, home.shelterBox, home.shelterSpawn, true);
            // 回家时清除避难所内的怪物
            SixtySecondsDefenseSystem.clearShelterMobs(level, home.shelterBox);
        }
        visitor.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.visit_left"), false);
    }

    private static void applyRestriction(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 40, 0, false, false, false));
    }

    public static void reset() {
        VISITS.clear();
    }
}
