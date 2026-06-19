package org.agmas.noellesroles.voice;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把“说话者身上的语音药水效果”同步给所有客户端。
 *
 * <p>原版只会把玩家自身的 MobEffect 同步给他自己（{@code ServerPlayer.onEffectAdded}
 * 只发给本人的连接），其它玩家的效果不会下发。而
 * {@link VoiceEffectsOpenALPlugin} 是在<b>听者</b>客户端运行、根据<b>说话者</b>是否带有
 * {@link ModEffects#HEAVY_METAL_VOICE} / {@link ModEffects#VOICE_ECHO} 来处理声源，
 * 因此如果不额外同步，听者永远查不到说话者的效果，重金属/回响语音就不会生效。</p>
 *
 * <p>这里在服务端每隔若干 tick 主动把这两个效果以隐藏粒子的形式广播给所有人，
 * 让听者客户端的 {@code level.getPlayerByUUID(speaker).hasEffect(...)} 能返回真值。</p>
 */
public final class VoiceEffectSync {

    private VoiceEffectSync() {
    }

    /** 刷新间隔（tick）。小于客户端效果时长，保证不会在两次刷新之间过期。 */
    private static final int REFRESH_INTERVAL = 10;

    /** 下发到客户端的效果时长，需明显大于刷新间隔以避免闪断。 */
    private static final int SYNC_DURATION = 40;

    /** 需要同步给其它玩家的“说话者侧”语音效果。 */
    private static final Holder<MobEffect>[] SYNCED_EFFECTS = effects();

    @SuppressWarnings("unchecked")
    private static Holder<MobEffect>[] effects() {
        return new Holder[] { ModEffects.HEAVY_METAL_VOICE, ModEffects.VOICE_ECHO };
    }

    /** 记录每个玩家上一次已广播的效果，便于在效果消失时下发移除包。key=玩家UUID，value=效果索引位掩码。 */
    private static final Map<UUID, Integer> LAST_STATE = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(VoiceEffectSync::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server.overworld().getGameTime() % REFRESH_INTERVAL != 0) {
            return;
        }

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!LAST_STATE.isEmpty()) LAST_STATE.clear();
            return;
        }

        for (ServerPlayer speaker : players) {
            int prevMask = LAST_STATE.getOrDefault(speaker.getUUID(), 0);
            int mask = 0;

            for (int i = 0; i < SYNCED_EFFECTS.length; i++) {
                Holder<MobEffect> effect = SYNCED_EFFECTS[i];
                MobEffectInstance instance = speaker.getEffect(effect);
                boolean had = (prevMask & (1 << i)) != 0;

                if (instance != null) {
                    mask |= (1 << i);
                    // 隐藏粒子/图标，仅作为信息载体广播给其他客户端。
                    MobEffectInstance hidden = new MobEffectInstance(
                            effect, SYNC_DURATION, instance.getAmplifier(), false, false, false);
                    ClientboundUpdateMobEffectPacket update =
                            new ClientboundUpdateMobEffectPacket(speaker.getId(), hidden, false);
                    broadcastExcept(players, speaker, update);
                } else if (had) {
                    // 效果刚消失：下发移除包，让其它客户端清掉。
                    ClientboundRemoveMobEffectPacket remove =
                            new ClientboundRemoveMobEffectPacket(speaker.getId(), effect);
                    broadcastExcept(players, speaker, remove);
                }
            }

            if (mask == 0) {
                LAST_STATE.remove(speaker.getUUID());
            } else {
                LAST_STATE.put(speaker.getUUID(), mask);
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
