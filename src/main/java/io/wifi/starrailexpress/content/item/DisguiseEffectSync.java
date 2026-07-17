package io.wifi.starrailexpress.content.item;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把“玩家身上的伪装效果”同步给所有其它客户端。
 *
 * <p>
 * 原版只会把玩家自身的 {@link net.minecraft.world.effect.MobEffect} 同步给他自己
 * （{@code ServerPlayer.onEffectAdded} 只发给本人的连接），其它玩家的效果不会下发。
 * 而伪装皮肤替换（见 {@code StupidExpressClient} 中的 {@code OnGettingPlayerSkin} 监听器）
 * 是在<b>每个观察者</b>客户端运行、对<b>被渲染的玩家</b>调用
 * {@code player.getEffect(ModEffects.DISGUISE)} 来决定皮肤的。
 * 因此若不额外同步，观察者客户端永远查不到<b>其他玩家</b>的伪装效果，
 * 导致“伪装只有自己能看到”。
 * </p>
 *
 * <p>
 * 这里在服务端每隔若干 tick 主动把 {@link ModEffects#DISGUISE} 以隐藏粒子/图标的形式
 * 广播给其他所有人（保留 amplifier，因为它决定使用 {@link DisguiseVariants} 中的哪套皮肤），
 * 让观察者客户端的 {@code player.getEffect(DISGUISE)} 能返回真值。
 * </p>
 */
public final class DisguiseEffectSync {

    private DisguiseEffectSync() {
    }

    /** 刷新间隔（tick）。小于客户端效果时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 记录上一次已广播伪装效果的玩家，便于在效果消失时下发移除包。 */
    public static final Map<UUID, Boolean> HAD_DISGUISE = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DisguiseEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!HAD_DISGUISE.isEmpty())
                HAD_DISGUISE.clear();
            return;
        }

        for (ServerPlayer player : players) {
            MobEffectInstance instance = player.getEffect(ModEffects.DISGUISE);
            boolean had = HAD_DISGUISE.getOrDefault(player.getUUID(), false);

            if (instance != null) {
                HAD_DISGUISE.put(player.getUUID(), true);
                // 隐藏粒子/图标，仅作为信息载体广播给其他客户端；保留 amplifier 以选中正确的皮肤变体。
                MobEffectInstance hidden = new MobEffectInstance(
                        ModEffects.DISGUISE, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                ClientboundUpdateMobEffectPacket update = new ClientboundUpdateMobEffectPacket(player.getId(), hidden,
                        false);
                broadcastExcept(players, player, update);
            } else if (had) {
                // 效果刚消失：下发移除包，让其它客户端清掉。
                HAD_DISGUISE.remove(player.getUUID());
                ClientboundRemoveMobEffectPacket remove = new ClientboundRemoveMobEffectPacket(player.getId(),
                        ModEffects.DISGUISE);
                broadcastExcept(players, player, remove);
            }
        }
    }

    private static void broadcastExcept(List<ServerPlayer> players, ServerPlayer except,
            net.minecraft.network.protocol.Packet<?> packet) {
        for (ServerPlayer receiver : players) {
            if (receiver == except)
                continue;
            receiver.connection.send(packet);
        }
    }
}
