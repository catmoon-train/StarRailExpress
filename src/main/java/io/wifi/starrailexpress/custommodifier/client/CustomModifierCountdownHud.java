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

package io.wifi.starrailexpress.custommodifier.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.network.packet.CustomModifierCountdownPacket;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义修饰符「死亡后倒计时」HUD（与难民修饰符同款：屏幕中下方的一行倒计时）。
 *
 * <p>
 * 倒计时的权威时间在服务端，这里只按服务端下发的剩余秒数换算成本地的到期刻来显示，
 * 与 {@code RefugeeHud} 的做法一致。时钟用「游戏开始刻」（{@link SREClient#getTicksFromGameStart()}）：
 * 它只在非冻结刻递增，所以时间冻结（会议等）时倒计时数字会跟着停住，与服务端行为一致。
 */
@Environment(EnvType.CLIENT)
public final class CustomModifierCountdownHud {

    /** 「修饰符 id + 触发组下标」-> 倒计时状态（同一个修饰符的多个触发组各占一行）。 */
    private static final Map<String, Entry> ACTIVE = new HashMap<>();

    private static String key(String modifierId, int group) {
        return modifierId + "|" + group;
    }

    /** 一行倒计时：到期游戏刻 / 是否附带复活 / 修饰符显示名。 */
    private record Entry(long deadline, boolean revive, String label) {
    }

    private CustomModifierCountdownHud() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CustomModifierCountdownPacket.ID,
                (payload, context) -> context.client().execute(() -> apply(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
        CommonHudRenderCallback.EVENT.register(
                (graphics, deltaTracker) -> render(graphics.guiWidth(), graphics.guiHeight(), graphics));
    }

    private static void apply(CustomModifierCountdownPacket payload) {
        if (payload == null || SREClient.timeComponent == null) {
            return;
        }
        if (!payload.active()) {
            ACTIVE.remove(key(payload.modifierId(), payload.group()));
            return;
        }
        long now = SREClient.getTicksFromGameStart();
        ACTIVE.put(key(payload.modifierId(), payload.group()),
                new Entry(now + Math.max(0, payload.seconds()) * 20L, payload.revive(), payload.label()));
    }

    private static void render(int width, int height, FakeGuiGraphics graphics) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) {
            return;
        }
        long now = SREClient.getTicksFromGameStart();
        // 服务端会主动发取消包；这里再兜一层，避免包丢失时倒计时卡在屏幕上
        ACTIVE.entrySet().removeIf(entry -> now > entry.getValue().deadline() + 20L);
        if (ACTIVE.isEmpty()) {
            return;
        }

        List<Entry> entries = new ArrayList<>(ACTIVE.values());
        int centerX = width / 2;
        int baseY = height - 102;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int seconds = (int) Math.max(0L, (entry.deadline() - now + 19L) / 20L);
            Component text = Component
                    .translatable(entry.revive() ? "hud.sre.custom_modifier.countdown_revive"
                            : "hud.sre.custom_modifier.countdown_trigger",
                            Component.literal(seconds + "s").withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.YELLOW);
            int y = baseY + i * 24;
            graphics.drawString(client.font, text, centerX - client.font.width(text) / 2, y, 0xFFFFFFFF);
            if (entry.label() != null && !entry.label().isBlank()) {
                Component name = Component.literal(entry.label()).withStyle(ChatFormatting.GOLD);
                graphics.drawString(client.font, name, centerX - client.font.width(name) / 2, y + 12,
                        0xFFFFFFFF);
            }
        }
    }
}
