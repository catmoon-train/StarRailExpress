package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.exmo.sre.sixtyseconds.network.SixtySecondsHelicopterS2CPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.AABB;

/**
 * 直升机撤离系统：<b>全局单架</b>，最后一天清晨在管理员预设的 {@code helicopterLandingPos}
 * 处抵达。前 {@link #EVAC_MAX} 名进入撤离区半径的<b>幸存玩家</b>（未倒地、非怪化）可撤离获胜，
 * 先到先得。
 */
public final class SixtySecondsHelicopterEvac {

    /** 撤离区半径（格）。 */
    public static final int EVAC_RADIUS = 8;
    /** 撤离上限。 */
    public static final int EVAC_MAX = 8;

    private SixtySecondsHelicopterEvac() {
    }

    /**
     * 直升机抵达：在 {@code landingPos} 标记撤离区，广播 S2C 包。
     */
    public static void arrive(ServerLevel level, SixtySecondsState.Data data, BlockPos landingPos) {
        data.helicopterArrived = true;
        data.helicopterEvacuated.clear();

        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.noellesroles.sixty_seconds.helicopter_arrive",
                        landingPos.getX(), landingPos.getY(), landingPos.getZ())
                        .withStyle(ChatFormatting.GOLD), false);

        SixtySecondsHelicopterS2CPacket.broadcastArrive(
                level.players(), landingPos, EVAC_RADIUS, EVAC_MAX, 0);
    }

    /**
     * 每 tick 检查撤离区内的玩家，逐个撤离。
     * 在 {@link SixtySecondsManager#tick} 中调用。
     */
    public static void tick(ServerLevel level, SixtySecondsState.Data data) {
        if (!data.helicopterArrived) return;
        if (data.helicopterEvacuated.size() >= EVAC_MAX) return;

        var config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || config.helicopterLandingPos == null) return;
        BlockPos landingPos = config.helicopterLandingPos.toBlockPos();

        AABB zone = new AABB(landingPos).inflate(EVAC_RADIUS);

        for (ServerPlayer player : level.players()) {
            if (data.helicopterEvacuated.size() >= EVAC_MAX) break;
            if (data.helicopterEvacuated.contains(player.getUUID())) continue;
            if (player.isSpectator() || player.isCreative()) continue;

            // 检查是否在撤离区内
            if (!zone.contains(player.getX(), player.getY(), player.getZ())) continue;

            // 必须是幸存状态：未倒地、非怪化
            var stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed || stats.monster) continue;

            // 撤离！
            evacPlayer(level, data, player);
        }
    }

    private static void evacPlayer(ServerLevel level, SixtySecondsState.Data data,
            ServerPlayer player) {
        data.helicopterEvacuated.add(player.getUUID());
        int count = data.helicopterEvacuated.size();

        // 广播已撤离人数
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.noellesroles.sixty_seconds.helicopter_evac",
                        player.getDisplayName(), count, EVAC_MAX)
                        .withStyle(count >= EVAC_MAX ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                false);

        // 同步新计数给所有在线玩家
        var config = SixtySecondsConfigStore.current(level).orElse(null);
        BlockPos landingPos = config != null && config.helicopterLandingPos != null
                ? config.helicopterLandingPos.toBlockPos() : BlockPos.ZERO;
        SixtySecondsHelicopterS2CPacket.broadcastArrive(
                level.players(), landingPos, EVAC_RADIUS, EVAC_MAX, count);

        // 撤离者切旁观
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.helicopter_evac_you")
                        .withStyle(ChatFormatting.GREEN), false);

        // 全员撤离
        if (count >= EVAC_MAX) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.helicopter_full")
                            .withStyle(ChatFormatting.GREEN), false);
        }
    }
}
