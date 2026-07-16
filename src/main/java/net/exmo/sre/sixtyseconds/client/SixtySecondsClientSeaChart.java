package net.exmo.sre.sixtyseconds.client;

import net.exmo.sre.sixtyseconds.client.screen.SeaChartScreen;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartS2CPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 客户端海图数据持有器：缓存服务端最近一次下发的 {@link SixtySecondsSeaChartS2CPacket}，
 * 供 {@code SeaChartScreen} 渲染；{@code openScreen} 包直接弹出海图。
 * 打开入口：聊天栏点击（/sre:60s island map）或客户端命令 /sre:client screen sea_chart。
 */
public final class SixtySecondsClientSeaChart {

    private static SixtySecondsSeaChartS2CPacket data;

    private SixtySecondsClientSeaChart() {
    }

    /** 网络接收（主线程）。 */
    public static void accept(SixtySecondsSeaChartS2CPacket packet) {
        data = packet;
        if (packet.openScreen()) {
            open();
        }
    }

    public static SixtySecondsSeaChartS2CPacket data() {
        return data;
    }

    /** 打开海图界面；无数据/未开启时给聊天提示。 */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (data == null || !data.enabled() || data.islands().isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.island.chart_no_data")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        minecraft.setScreen(new SeaChartScreen(data));
    }

    public static void reset() {
        data = null;
    }
}
