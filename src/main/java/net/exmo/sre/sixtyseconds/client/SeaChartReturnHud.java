package net.exmo.sre.sixtyseconds.client;

import net.exmo.sre.sixtyseconds.client.screen.SeaChartFullScreen;
import net.exmo.sre.sixtyseconds.client.screen.SixtySecondsSearchZonesClient;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIsland;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartArrivalS2CPacket;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartPositionsS2CPacket;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartReturnStartS2CPacket;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartS2CPacket;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartSailStartS2CPacket;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;

/**
 * 海图返回住所——客户端划船动画 HUD 叠加层。
 * <p>
 * 收到 {@link SixtySecondsSeaChartReturnStartS2CPacket} 后在屏幕中央渲染海上划船动画，
 * 持续 10 秒后自动关闭。动画期间玩家无法移动（通过画满屏遮罩限制）。
 * 动画结束后海图界面关闭、玩家被服务端传送回家。
 * </p>
 */
public final class SeaChartReturnHud {

    private static final ResourceLocation BOAT_ICON = Noellesroles.id("textures/gui/boat_return.png");

    /** 划船演出的两个方向：去程（扬帆上岛）与回程（返回住所）。 */
    public enum Leg {
        /** 扬帆去程：结束时人在岛上，落点缓存由服务端的 Arrival 包重发，这里不能清。 */
        SAIL,
        /** 返航回程：结束时人已到家，清掉落点缓存与「在探索区」标记。 */
        RETURN
    }

    /** 动画状态 */
    private static boolean active = false;
    private static long startTick = 0;
    private static int durationTicks = 200; // 默认 10 秒
    private static final int WATER_WAVE_COUNT = 40;
    private static float animProgress = 0;
    private static Leg leg = Leg.RETURN;
    /** 去程目的岛名（SAIL 时报幕用；null=不显示）。 */
    private static Component sailTarget = null;

    private SeaChartReturnHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (active) {
                render(graphics);
            }
        });
    }

    /** 启动划船动画（{@code leg} 决定文案与收尾处理）。 */
    public static void start(int durationTicks, Leg leg, Component sailTarget) {
        active = true;
        startTick = System.currentTimeMillis() / 50;
        SeaChartReturnHud.durationTicks = durationTicks;
        SeaChartReturnHud.leg = leg;
        SeaChartReturnHud.sailTarget = sailTarget;
        animProgress = 0;
    }

    /**
     * 动画播完收尾。真正的传送由服务端倒计时执行，这里只收界面：
     * 关掉海图；<b>仅回程</b>清落点缓存与「在探索区」标记——去程播完人正在岛上，
     * 清了会让海图误判成「没出门」而把返航按钮永久置灰。
     */
    public static void finish() {
        active = false;
        animProgress = 0;
        // 关闭海图界面
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SeaChartFullScreen) {
            minecraft.screen.onClose();
        }
        if (leg == Leg.RETURN) {
            SeaChartFullScreen.cachedArrivalPos = null;
            SixtySecondsSearchZonesClient.setInSearchZone(false);
        }
        sailTarget = null;
    }

    /** 取消返回（服务端发来取消包时调用）。 */
    public static void cancel() {
        active = false;
        animProgress = 0;
    }

    public static boolean isActive() {
        return active;
    }

    /** 每 tick 由客户端总控调用。 */
    public static void tick() {
        if (!active) {
            return;
        }
        long elapsed = System.currentTimeMillis() / 50 - startTick;
        if (elapsed >= durationTicks) {
            finish();
            return;
        }
        animProgress = (float) elapsed / durationTicks;
    }

    private static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        // 半透明黑遮罩
        graphics.fill(0, 0, width, height, 0x88000000);

        // 倒计时文字（去程/回程文案不同）
        int remaining = (int) Math.ceil((durationTicks - (System.currentTimeMillis() / 50 - startTick)) / 20.0);
        String prefix = "message.noellesroles.sixty_seconds.island."
                + (leg == Leg.SAIL ? "sail_countdown" : "return_countdown");
        graphics.drawCenteredString(minecraft.font, Component.translatable(prefix, remaining).getString(),
                width / 2, height / 2 - 50, 0xFFFFFFFF);

        // 提示文字：去程报目的岛名，回程报通用提示
        String hint = leg == Leg.SAIL && sailTarget != null
                ? Component.translatable("message.noellesroles.sixty_seconds.island.sail_hint",
                        sailTarget).getString()
                : Component.translatable("message.noellesroles.sixty_seconds.island.return_hint").getString();
        graphics.drawCenteredString(minecraft.font, hint, width / 2, height / 2 - 30, 0xFFAAAAAA);

        // 划船动画区域
        renderRowingAnimation(graphics, width, height);

        // 进度条
        int barW = 200;
        int barH = 6;
        int barX = width / 2 - barW / 2;
        int barY = height / 2 + 50;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        int fillW = (int) (barW * animProgress);
        graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF4A90D9);
    }

    /**
     * 绘制简单划船动画：水波纹 + 船体左右晃动。
     */
    private static void renderRowingAnimation(GuiGraphics graphics, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2 + 10;
        int boatW = 80;
        int boatH = 30;

        float progress = animProgress;
        long tick = System.currentTimeMillis() / 50;

        // 船体晃动（正弦摆动模拟划船）
        float sway = (float) Math.sin(tick * 0.15) * 8;
        int boatX = centerX - boatW / 2 + (int) sway;
        int boatY = centerY;

        // 水波纹（蓝色波浪线）
        for (int i = 0; i < WATER_WAVE_COUNT; i++) {
            float wavePhase = (float) (tick * 0.1 + i * 0.5);
            int wx = (int) (centerX - 120 + (i * 240.0 / WATER_WAVE_COUNT));
            int wy = boatY + 22 + (int) (Math.sin(wavePhase) * 4);
            int alpha = 0x55 + (int) (Math.abs(Math.sin(wavePhase * 1.3)) * 0x44);
            int color = (alpha << 24) | 0x004488;
            graphics.fill(wx, wy, wx + 2, wy + 3, color);
        }

        // 船体（简单矩形 + 三角形船头）
        int hullColor = 0xFF8B6914;
        int hullDark = 0xFF6B4E0A;
        // 船底
        graphics.fill(boatX, boatY + 4, boatX + boatW, boatY + boatH, hullColor);
        // 船身上半
        graphics.fill(boatX + 4, boatY - 4, boatX + boatW - 4, boatY + 4, 0xFFA0782C);
        // 船头三角
        int bowX = boatX + boatW - 4;
        graphics.fill(bowX, boatY - 8, bowX + 16, boatY + boatH / 2, hullColor);
        graphics.fill(bowX, boatY + 8, bowX + 8, boatY + boatH / 2, hullDark);
        // 桅杆
        graphics.fill(centerX + (int) sway, boatY - 30, centerX + (int) sway + 2, boatY - 4, 0xFF5C3A0A);
        // 帆（随进度张满）
        float sailFull = Mth.clamp(progress * 2.5f, 0, 1);
        int sailW = (int) (12 * sailFull);
        int sailH = (int) (20 * sailFull);
        int sailColor = 0xCCF5F0E0;
        graphics.fill(centerX + (int) sway + 2, boatY - 28, centerX + (int) sway + 2 + sailW, boatY - 28 + sailH, sailColor);

        // 双桨动画
        float paddleAngle = (float) Math.sin(tick * 0.2) * 0.6f;
        // 左桨
        int leftPaddleX = centerX - 20 + (int) sway;
        int leftPaddleY = boatY + 2;
        drawPaddle(graphics, leftPaddleX, leftPaddleY, -paddleAngle, true);
        // 右桨
        int rightPaddleX = centerX + 20 + (int) sway;
        int rightPaddleY = boatY + 2;
        drawPaddle(graphics, rightPaddleX, rightPaddleY, paddleAngle, false);
    }

    private static void drawPaddle(GuiGraphics graphics, int x, int y, float angle, boolean left) {
        // 浆柄
        int px = x + (int) (Math.sin(angle) * 12);
        int py = y + (int) (Math.cos(angle) * 8);
        graphics.fill(px, py, px + 2, py + 14, 0xFF5C3A0A);
        // 浆叶
        int bladeX = px + (left ? -4 : 4);
        graphics.fill(bladeX, py + 12, bladeX + 4, py + 18, 0xFF8B6914);
    }

    // ── 网络包接收（由 NoellesrolesClient 调用） ──────────────────────────

    public static void onReturnStart(SixtySecondsSeaChartReturnStartS2CPacket packet) {
        start(packet.durationTicks(), Leg.RETURN, null);
    }

    /** 扬帆去程动画：岛名从客户端海图缓存里按 id 反查（元数据早已下发，无需随包再传一次名字）。 */
    public static void onSailStart(SixtySecondsSeaChartSailStartS2CPacket packet) {
        Component target = null;
        SixtySecondsSeaChartS2CPacket chart = SixtySecondsClientSeaChart.data();
        if (chart != null) {
            for (SixtySecondsSeaChartS2CPacket.Entry entry : chart.islands()) {
                if (entry.id() == packet.islandId()) {
                    target = Component.translatable(SixtySecondsIsland.LANG + "name_prefix." + entry.namePrefix())
                            .append(Component.translatable(
                                    SixtySecondsIsland.LANG + "name_suffix." + entry.nameSuffix()));
                    break;
                }
            }
        }
        start(packet.durationTicks(), Leg.SAIL, target);
    }

    /** 庇护所 / 队友点位（服务端在海图开着时每秒推）。 */
    public static void onPositions(SixtySecondsSeaChartPositionsS2CPacket packet) {
        SixtySecondsClientSeaChart.acceptPositions(packet);
    }

    public static void onArrivalSync(SixtySecondsSeaChartArrivalS2CPacket packet) {
        SeaChartFullScreen.cachedArrivalPos = packet.arrivalPos();
        SixtySecondsSearchZonesClient.setInSearchZone(true);
    }

    public static void onSeaChartData(SixtySecondsSeaChartS2CPacket packet) {
        SixtySecondsClientSeaChart.accept(packet);
    }

    /** 打开全屏海图 */
    public static void openFullScreenChart() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // 请求服务端下发海图数据并打开
        minecraft.player.connection.sendCommand("sre:60s island map");
    }
}
