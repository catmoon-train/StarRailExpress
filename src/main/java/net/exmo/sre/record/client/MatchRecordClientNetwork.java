package net.exmo.sre.record.client;

import net.exmo.sre.record.MatchRecord;
import net.exmo.sre.record.network.RecordListRequestC2SPayload;
import net.exmo.sre.record.network.RecordListS2CPayload;
import net.exmo.sre.record.network.RecordReplayRequestC2SPayload;
import net.exmo.sre.record.network.RecordReplayS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 客户端网络处理：接收战绩列表 / 回放数据并打开对应 GUI，提供向服务端发起请求的辅助方法。
 */
public final class MatchRecordClientNetwork {

    private MatchRecordClientNetwork() {
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(RecordListS2CPayload.ID, (payload, context) -> {
            List<MatchRecord.Summary> summaries = MatchRecord.summaryListFromJson(payload.json());
            context.client().execute(() -> {
                ClientMatchRecordCache.setSummaries(summaries);
                Minecraft client = context.client();
                if (client.screen instanceof MatchRecordsScreen recordsScreen) {
                    recordsScreen.refreshEntries();
                } else {
                    client.setScreen(new MatchRecordsScreen());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RecordReplayS2CPayload.ID, (payload, context) -> {
            String json = payload.json();
            context.client().execute(() -> {
                Minecraft client = context.client();
                if (json == null || json.isEmpty()) {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.translatable("screen.sre.records.replay_missing"), false);
                    }
                    return;
                }
                MatchRecord record = MatchRecord.fromJson(json);
                if (record == null) {
                    return;
                }
                ClientMatchRecordCache.putRecord(record);
                if (client.screen instanceof MatchRecordsScreen recordsScreen) {
                    recordsScreen.showRecord(record);
                } else {
                    // 通过 /sre:records <id> 直接打开某场回放：开屏后再补拉列表填充左侧
                    client.setScreen(new MatchRecordsScreen(record));
                    requestList(0);
                }
            });
        });
    }

    public static void requestList(int limit) {
        if (ClientPlayNetworking.canSend(RecordListRequestC2SPayload.ID)) {
            ClientPlayNetworking.send(new RecordListRequestC2SPayload(limit));
        }
    }

    public static void requestReplay(String matchId) {
        if (matchId != null && ClientPlayNetworking.canSend(RecordReplayRequestC2SPayload.ID)) {
            ClientPlayNetworking.send(new RecordReplayRequestC2SPayload(matchId));
        }
    }
}
