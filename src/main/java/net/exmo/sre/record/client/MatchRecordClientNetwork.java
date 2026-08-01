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
 * 客户端网络处理：接收战绩列表分页 / 回放数据并打开对应 GUI，
 * 提供「按需拉取某一页」与「拉取某场回放」的辅助方法（仅在滚动到对应区间时调用）。
 */
public final class MatchRecordClientNetwork {

    private MatchRecordClientNetwork() {
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(RecordListS2CPayload.ID, (payload, context) -> {
            List<MatchRecord.Summary> summaries = MatchRecord.summaryListFromJson(payload.json());
            context.client().execute(() -> {
                ClientMatchRecordCache.applyWindow(payload.offset(), payload.total(), summaries);
                Minecraft client = context.client();
                if (client.screen instanceof MatchRecordsScreen recordsScreen) {
                    recordsScreen.onWindowUpdated();
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
                    // 通过 /sre:records <id> 直接打开某场回放：开屏后再补拉首页填充左侧列表
                    ClientMatchRecordCache.resetWindows();
                    client.setScreen(new MatchRecordsScreen(record));
                    requestWindow(0, MatchRecordsScreen.PAGE_SIZE);
                }
            });
        });
    }

    /** 请求战绩列表的一页（offset 起 limit 条）。 */
    public static void requestWindow(int offset, int limit) {
        if (ClientPlayNetworking.canSend(RecordListRequestC2SPayload.ID)) {
            ClientPlayNetworking.send(new RecordListRequestC2SPayload(offset, limit));
        }
    }

    public static void requestReplay(String matchId) {
        if (matchId != null && ClientPlayNetworking.canSend(RecordReplayRequestC2SPayload.ID)) {
            ClientPlayNetworking.send(new RecordReplayRequestC2SPayload(matchId));
        }
    }
}
