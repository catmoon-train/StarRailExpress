package net.exmo.sre.sixtyseconds.arena;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 搜索区进出：存返回点 → 传送到本队搜索区 → 每 tick 限制在盒内 → 送回（参照 {@code WarlockDomainManager}）。
 * P0 骨架：仅完成传送 + 限制 + 送回；搜索区内的物资箱/掉落业务见 {@code SupplyBox}（后续批次）。
 */
public final class SixtySecondsSearchZones {
    public static final int EXPLORE_INVIS_TICKS = 20 * 15;    // 出门隐身 15s
    public static final int RETURN_COOLDOWN_TICKS = 20 * 120; // 归来冷却 120s

    private static final Map<UUID, ReturnPos> RETURNS = new HashMap<>();

    private SixtySecondsSearchZones() {
    }

    /** 出门探索：传送到本队搜索区，隐身 15s；归来需等 120s 冷却。 */
    public static void enter(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
        if (team == null || team.searchZoneSpawn == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.no_search_zone"), true);
            return;
        }
        RETURNS.put(player.getUUID(), new ReturnPos(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()));
        player.teleportTo(level, team.searchZoneSpawn.getX() + 0.5D, team.searchZoneSpawn.getY(),
                team.searchZoneSpawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
        stats.exploreCooldownEndTick = level.getGameTime() + RETURN_COOLDOWN_TICKS;
        stats.sync();
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EXPLORE_INVIS_TICKS, 0, false, false, true));
    }

    /** 把玩家送回进入搜索区前的位置（受 120s 归来冷却限制）。 */
    public static void returnPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        long now = level.getGameTime();
        if (now < stats.exploreCooldownEndTick) {
            int seconds = (int) Math.ceil((stats.exploreCooldownEndTick - now) / 20.0D);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.return_cooldown", seconds), true);
            return;
        }
        ReturnPos pos = RETURNS.remove(player.getUUID());
        if (pos != null) {
            player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
        }
    }

    public static boolean isInSearchZone(ServerPlayer player) {
        return RETURNS.containsKey(player.getUUID());
    }

    /** 每 tick 把搜索区内的玩家限制在本队搜索区盒里。 */
    public static void tick(ServerLevel level) {
        if (RETURNS.isEmpty()) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (ServerPlayer player : level.players()) {
            if (!RETURNS.containsKey(player.getUUID())) {
                continue;
            }
            SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
            if (team != null && team.searchZoneBox != null) {
                GameUtils.limitPlayerToBox(player, team.searchZoneBox);
            }
        }
    }

    public static void reset(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ReturnPos pos = RETURNS.get(player.getUUID());
            if (pos != null) {
                player.teleportTo(level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch);
            }
        }
        RETURNS.clear();
    }

    private record ReturnPos(double x, double y, double z, float yaw, float pitch) {
    }
}
