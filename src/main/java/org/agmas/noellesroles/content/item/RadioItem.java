package org.agmas.noellesroles.content.item;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.packet.OpenRadioChannelS2CPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对讲机：右键打开频道界面输入一个频道号（{@link #MIN_CHANNEL}..{@link #MAX_CHANNEL}）接入通话。
 * 只有<b>接入同一频道</b>的持机玩家能互相听到（超出 8× 语音距离仍可听到，模拟无线电）。
 * <p>
 * 频道成员表 {@link #CHANNELS}（uuid→频道号）由 {@code NRGameStateEvents} 每 10 tick 懒清理
 * （掉线 / 变旁观 / 不再持有对讲机的成员移除）。
 */
public class RadioItem extends Item {
    public static final int MIN_CHANNEL = 1;
    public static final int MAX_CHANNEL = 9999;

    /** uuid → 已接入的频道号。key 集合即「当前在通话中的持机玩家」。 */
    public static final Map<UUID, Integer> CHANNELS = new ConcurrentHashMap<>();

    public RadioItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
            // 打开频道输入界面（携带当前频道以预填）
            int current = CHANNELS.getOrDefault(user.getUUID(), 0);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
                    new OpenRadioChannelS2CPacket(current));
        }
        return InteractionResultHolder.consume(itemStack);
    }

    /** 接入指定频道（clamp 到合法范围）。 */
    public static void joinChannel(ServerPlayer player, int channel) {
        int ch = Math.max(MIN_CHANNEL, Math.min(MAX_CHANNEL, channel));
        CHANNELS.put(player.getUUID(), ch);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.radio.joined", ch)
                        .withStyle(net.minecraft.ChatFormatting.GREEN), true);
    }

    /** 退出当前频道。 */
    public static void leave(ServerPlayer player) {
        if (CHANNELS.remove(player.getUUID()) != null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.radio.left")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    /** 清空所有频道成员（换局/重置调用）。 */
    public static void clear() {
        CHANNELS.clear();
    }

    public static ServerPlayer getPlayerByUUID(ServerLevel level, UUID uUID) {
        for (int i = 0; i < level.players().size(); ++i) {
            ServerPlayer player = level.players().get(i);
            if (uUID.equals(player.getUUID())) {
                return player;
            }
        }
        return null;
    }

    public static void vcparanoidEvent(SREGameWorldComponent gameWorldComponent, ServerPlayer player,
            MicrophonePacketEvent event) {
        if (player.isSpectator()) {
            return;
        }
        Integer myChannel = CHANNELS.get(player.getUUID());
        if (myChannel == null) {
            return;
        }
        var api = event.getVoicechat();
        for (Map.Entry<UUID, Integer> entry : CHANNELS.entrySet()) {
            if (entry.getKey().equals(player.getUUID()) || !entry.getValue().equals(myChannel)) {
                continue; // 只发给同频道的其他人
            }
            ServerPlayer p = getPlayerByUUID(player.serverLevel(), entry.getKey());
            if (p == null || p.isSpectator()) {
                continue;
            }
            VoicechatConnection con = api.getConnectionOf(entry.getKey());
            if (con != null && con.isInstalled() && con.isConnected()) {
                api.sendLocationalSoundPacketTo(con, event.getPacket()
                        .locationalSoundPacketBuilder()
                        .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                        .distance((float) api.getVoiceChatDistance())
                        .build());
            }
        }
    }
}
