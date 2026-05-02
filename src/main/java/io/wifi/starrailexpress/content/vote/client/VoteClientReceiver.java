package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import io.wifi.events.day_night_fight.DNF;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class VoteClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(VoteSyncS2CPacket.TYPE, (packet, context) -> {
            Minecraft client = context.client();
            client.execute(() -> {
                ClientVoteCache.updateFromPacket(packet);
                if (packet.active()) {
                    if (client.screen instanceof VoteScreen screen) {
                        // 更新屏幕数据
                        screen.updateData(packet);
                    } else if (packet.hasOptions()
                            && (!"dnf_meeting_vote".equals(ClientVoteCache.getTypeId())
                                    || DNF.isInConfiguredMeetingArea(context.player()))) {
                        client.setScreen(new VoteScreen()); // 无参构造
                    }
                } else {
                    // 投票结束，关闭屏幕
                    if (client.screen instanceof VoteScreen) {
                        client.screen.onClose();
                    }
                }
            });
        });
    }
}
