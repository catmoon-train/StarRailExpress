/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RadioItem extends Item {
    // 全局临时组（简单实现）：加入即认为在同一语音组
    public static final Set<UUID> RADIO_GROUP = new HashSet<>();

    public RadioItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (!world.isClientSide) {
            UUID id = user.getUUID();
            if (RADIO_GROUP.contains(id)) {
                RADIO_GROUP.remove(id);
                user.displayClientMessage(
                        Component.translatable("message.noellesroles.radio.left").withStyle(ChatFormatting.RED), true);
            } else {
                RADIO_GROUP.add(id);
                user.displayClientMessage(
                        Component.translatable("message.noellesroles.radio.joined").withStyle(ChatFormatting.GREEN),
                        true);
            }
        }
        return InteractionResultHolder.consume(itemStack);
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
        if (player.isSpectator())
            return;
        var api = event.getVoicechat();
        if (RADIO_GROUP.contains(player.getUUID())) {
            for (UUID p_u : RADIO_GROUP) {
                if (p_u == player.getUUID())
                    continue;
                ServerPlayer p = getPlayerByUUID(player.serverLevel(), p_u);
                if (p == null || p.isSpectator() || p.distanceTo(player) >= api.getVoiceChatDistance() * 8)
                    continue;
                VoicechatConnection con = api.getConnectionOf(p_u);
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
}
