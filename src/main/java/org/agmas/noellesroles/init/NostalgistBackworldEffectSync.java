package org.agmas.noellesroles.init;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把“怀旧者的里世界标记效果”同步给所有其它客户端。
 *
 * <p>原版只会把玩家自身的 MobEffect 同步给他自己，其它玩家的效果不会下发。而隐藏手持物品
 * （{@code InvisbleHandItem}，监听 {@code AllowItemShowInHand}）以及杀手透视隐藏
 * （{@code InstinctRenderer}）都需要在<b>每个客户端</b>对<b>怀旧者</b>调用
 * {@code player.hasEffect(NOSTALGIST_BACKWORLD)} 才能判断其是否处于里世界。若不额外同步，
 * 其它客户端永远查不到怀旧者的里世界标记，导致“别人仍能看到手持物品/被透视”。</p>
 *
 * <p>这里在服务端每隔若干 tick 主动把 {@link ModEffects#NOSTALGIST_BACKWORLD} 以隐藏粒子/图标的
 * 形式广播给其他所有人，让其它客户端的 {@code player.hasEffect(NOSTALGIST_BACKWORLD)} 能返回真值。</p>
 */
public final class NostalgistBackworldEffectSync {

    private NostalgistBackworldEffectSync() {
    }

    /** 刷新间隔（tick）。小于客户端效果时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 记录上一次已广播里世界标记的玩家，便于在效果消失时下发移除包。 */
    private static final Map<UUID, Boolean> HAD_EFFECT = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(NostalgistBackworldEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_EFFECT.isEmpty()) HAD_EFFECT.clear();
            return;
        }

        for (ServerPlayer player : players) {
            MobEffectInstance instance = player.getEffect(ModEffects.NOSTALGIST_BACKWORLD);
            boolean had = HAD_EFFECT.getOrDefault(player.getUUID(), false);

            if (instance != null) {
                HAD_EFFECT.put(player.getUUID(), true);
                // 隐藏粒子/图标，仅作为信息载体广播给其他客户端。
                MobEffectInstance hidden = new MobEffectInstance(
                        ModEffects.NOSTALGIST_BACKWORLD, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                ClientboundUpdateMobEffectPacket update =
                        new ClientboundUpdateMobEffectPacket(player.getId(), hidden, false);
                broadcastExcept(players, player, update);
            } else if (had) {
                // 效果刚消失：下发移除包，让其它客户端清掉。
                HAD_EFFECT.remove(player.getUUID());
                ClientboundRemoveMobEffectPacket remove =
                        new ClientboundRemoveMobEffectPacket(player.getId(), ModEffects.NOSTALGIST_BACKWORLD);
                broadcastExcept(players, player, remove);
            }
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except,
                                        net.minecraft.network.protocol.Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver == except) continue;
            receiver.connection.send(packet);
        }
    }
}
