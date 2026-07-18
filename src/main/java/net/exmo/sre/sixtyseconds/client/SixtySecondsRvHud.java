package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.init.ModItems;

/**
 * 房车 HUD：
 * <ul>
 *   <li><b>驾驶态</b>（本地玩家正乘坐本队房车）：屏幕底部中央画燃料 / 耐久两条，停机时红字提示。</li>
 *   <li><b>导航条</b>（手持罗盘，或装了远程电台在车上时）：顶部中央的指南针风横条，
 *       标出本队房车的方位；装导航阵列显示精确距离。</li>
 * </ul>
 * 数据全部来自已同步的实体数据（teamId/fuel/health/disabled）与本地玩家朝向，无需额外网络包。
 */
public final class SixtySecondsRvHud {
    private static final int COL_BG_TOP = 0xD81A1008;
    private static final int COL_BG_BOTTOM = 0xD820140A;
    private static final int COL_BORDER = 0xFF8B6914;
    private static final int COL_TRACK = 0xFF2A1B0E;
    private static final int COL_TRACK_EDGE = 0xFF120A04;
    private static final int COL_LABEL = 0xFFC8B898;
    private static final int COL_VALUE = 0xFFFFF4DC;

    /** 导航条：横向可视角范围（左右各 60°）与像素密度。 */
    private static final int NAV_HALF_FOV = 60;
    private static final int NAV_HALF_WIDTH = 82;

    private SixtySecondsRvHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isRunning() || SixtySecondsMod.MODE == null
                || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
            return;
        }
        LocalPlayer player = client.player;
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (stats == null || stats.teamId < 0) {
            return;
        }
        SixtySecondsRvEntity rv = findTeamRv(client, stats.teamId);
        if (rv == null) {
            return;
        }
        boolean driving = player.getVehicle() == rv;
        if (driving) {
            drawDrivingHud(graphics, client, player, rv);
        }
        boolean holdingCompass = isHoldingCompass(player);
        boolean radioAlways = driving && rv.hasPart(SixtySecondsRvPart.LONG_RANGE_RADIO);
        if (holdingCompass || radioAlways) {
            drawNavBar(graphics, client, player, rv);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 驾驶 HUD
    // ─────────────────────────────────────────────────────────────────

    private static void drawDrivingHud(FakeGuiGraphics g, Minecraft client, LocalPlayer player,
            SixtySecondsRvEntity rv) {
        int w = 130;
        // 右中下：贴屏幕右侧、垂直居中偏下，避开底部物品栏/状态栏
        int x = g.guiWidth() - w - 8;
        int y = g.guiHeight() / 2 + 30;
        int h = rv.isDisabled() ? 44 : 34;
        g.fillGradient(x, y, x + w, y + h, COL_BG_TOP, COL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, COL_BORDER);

        int fuelSeconds = rv.fuelTicks() / 20;
        drawBar(g, client, x + 6, y + 5, w - 12, "message.noellesroles.sixty_seconds.rv_fuel",
                rv.fuelTicks(), rv.maxFuelTicks(), 0xFFE0A030,
                Component.literal(fuelSeconds + "s"));
        drawBar(g, client, x + 6, y + 18, w - 12, "message.noellesroles.sixty_seconds.rv_durability",
                rv.vehicleHealth(), rv.maxVehicleHealth(), 0xFFE64848,
                Component.literal(rv.vehicleHealth() + "/" + rv.maxVehicleHealth()));
        if (rv.isDisabled()) {
            float pulse = 0.55f + 0.45f * Mth.sin(player.tickCount * 0.35f);
            int color = ((int) (0x90 + 0x6F * pulse)) << 24 | 0xFF5050;
            Component msg = Component.translatable("message.noellesroles.sixty_seconds.rv_disabled");
            // 红字相对 HUD 面板居中（不再用屏幕中心）
            g.drawString(client.font, msg, x + w / 2 - client.font.width(msg) / 2, y + 33, color);
        }

        // 速度仪表（HUD 左侧圆形指针表盘——HUD 已贴右边界，仪表只能放左侧）
        int gaugeCx = x - 28;
        int gaugeCy = y + h / 2;
        int gaugeR = 18;
        drawSpeedGauge(g, client, gaugeCx, gaugeCy, gaugeR, rv);
    }

    /**
     * 圆形速度仪表：外圈 + 刻度 + 指针 + 数字。
     * 指针角度 = throttle × 90°（0 朝上、+1 朝右、-1 朝左）；
     * 数字按 throttle × {@link #MAX_DISPLAY_KMH} 显示 km/h，倒车前缀 "R"。
     */
    private static final int MAX_DISPLAY_KMH = 25;

    private static void drawSpeedGauge(FakeGuiGraphics g, Minecraft client, int cx, int cy, int r,
            SixtySecondsRvEntity rv) {
        // 背景方块 + 外圈圆环
        g.fillGradient(cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3, COL_BG_TOP, COL_BG_BOTTOM);
        drawCircle(g, cx, cy, r, COL_BORDER);
        drawCircle(g, cx, cy, r - 1, COL_TRACK);

        // 刻度：从 -90°（左/倒车底）经 0°（上/停车）到 +90°（右/全油门），每 30° 一条
        for (int deg = -90; deg <= 90; deg += 30) {
            double rad = Math.toRadians(deg);
            int x1 = cx + (int) Math.round((r - 5) * Math.sin(rad));
            int y1 = cy - (int) Math.round((r - 5) * Math.cos(rad));
            int x2 = cx + (int) Math.round(r * Math.sin(rad));
            int y2 = cy - (int) Math.round(r * Math.cos(rad));
            drawLine(g, x1, y1, x2, y2, COL_LABEL);
        }

        // 指针：角度 = throttle × π/2，蓝色（前进）/红色（倒车）
        float throttle = rv.throttle();
        double angle = throttle * (Math.PI / 2.0);
        int px = cx + (int) Math.round((r - 4) * Math.sin(angle));
        int py = cy - (int) Math.round((r - 4) * Math.cos(angle));
        int pointerColor = throttle < -0.05F ? 0xFFE64848 : 0xFF48C8E6;
        drawLine(g, cx, cy, px, py, pointerColor);
        // 中心轴
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, COL_VALUE);

        // 数字：km/h，倒车前缀 "R"
        float speedKmh = Math.abs(throttle) * MAX_DISPLAY_KMH;
        String prefix = throttle < -0.05F ? "R " : "";
        Component text = Component.literal(prefix + String.format("%.0f", speedKmh) + " km/h");
        g.drawString(client.font, text, cx - client.font.width(text) / 2, cy + r + 4, COL_VALUE);
    }

    /** Bresenham 圆：用 1×1 像素拼接圆周（fill 只能画矩形，圆周由像素点近似）。 */
    private static void drawCircle(FakeGuiGraphics g, int cx, int cy, int r, int color) {
        int xx = r, yy = 0, err = 1 - r;
        while (xx >= yy) {
            plot8(g, cx, cy, xx, yy, color);
            yy++;
            if (err < 0) {
                err += 2 * yy + 1;
            } else {
                xx--;
                err += 2 * (yy - xx) + 1;
            }
        }
    }

    private static void plot8(FakeGuiGraphics g, int cx, int cy, int x, int y, int color) {
        g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
        g.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
        g.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
        g.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
        g.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
        g.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
        g.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
        g.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
    }

    /** Bresenham 直线：用 2×2 像素拼接（指针够粗，看得清）。 */
    private static void drawLine(FakeGuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1, y = y1;
        while (true) {
            g.fill(x, y, x + 2, y + 2, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    /** 通用小血条：标签 + 轨道 + 填充 + 右对齐数值。 */
    private static void drawBar(FakeGuiGraphics g, Minecraft client, int x, int y, int w, String labelKey,
            int value, int max, int color, Component valueText) {
        int barH = 8;
        double ratio = max <= 0 ? 0 : Mth.clamp(value / (double) max, 0, 1);
        g.drawString(client.font, Component.translatable(labelKey), x, y, COL_LABEL);
        int barX = x + 34;
        int barRight = x + w;
        g.fill(barX - 1, y - 1, barRight + 1, y + barH + 1, COL_TRACK_EDGE);
        g.fill(barX, y, barRight, y + barH, COL_TRACK);
        int fillW = (int) Math.round((barRight - barX) * ratio);
        if (fillW > 0) {
            g.fill(barX, y, barX + fillW, y + barH, color);
            g.fill(barX, y, barX + fillW, y + 1, 0x50FFFFFF);
        }
        int tw = client.font.width(valueText);
        g.drawString(client.font, valueText, barRight - tw - 2, y, COL_VALUE);
    }

    // ─────────────────────────────────────────────────────────────────
    // 导航条（指南针风横条）
    // ─────────────────────────────────────────────────────────────────

    private static void drawNavBar(FakeGuiGraphics g, Minecraft client, LocalPlayer player,
            SixtySecondsRvEntity rv) {
        int cx = g.guiWidth() / 2;
        int y = 6;
        int stripLeft = cx - NAV_HALF_WIDTH;
        int stripRight = cx + NAV_HALF_WIDTH;
        int stripH = 12;
        g.fillGradient(stripLeft - 3, y - 1, stripRight + 3, y + stripH + 1, COL_BG_TOP, COL_BG_BOTTOM);
        g.renderOutline(stripLeft - 3, y - 1, (stripRight + 3) - (stripLeft - 3), stripH + 2, COL_BORDER);

        float yaw = player.getYRot();
        // 基准朝向刻度（南=0/西=90/北=180/东=-90，与 MC yaw 约定一致）
        drawCardinal(g, client, cx, y, stripLeft, stripRight, yaw, 0, "S");
        drawCardinal(g, client, cx, y, stripLeft, stripRight, yaw, 90, "W");
        drawCardinal(g, client, cx, y, stripLeft, stripRight, yaw, 180, "N");
        drawCardinal(g, client, cx, y, stripLeft, stripRight, yaw, -90, "E");
        // 中心准线
        g.fill(cx, y, cx + 1, y + stripH, 0x66FFE8C0);

        double dx = rv.getX() - player.getX();
        double dz = rv.getZ() - player.getZ();
        double rvAzimuth = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(rvAzimuth - yaw);
        int markerX = cx + (int) Math.round(Mth.clamp(rel, -NAV_HALF_FOV, NAV_HALF_FOV)
                * (NAV_HALF_WIDTH / (double) NAV_HALF_FOV));
        markerX = Mth.clamp(markerX, stripLeft, stripRight);
        // 房车方位标记：金色三角（越界时贴边）
        int mColor = 0xFFFFD24A;
        g.fill(markerX - 2, y + 1, markerX + 3, y + 4, mColor);
        g.fill(markerX - 1, y + 4, markerX + 2, y + 6, mColor);
        g.fill(markerX, y + 6, markerX + 1, y + 8, mColor);

        // 距离文字（装了导航阵列显示精确距离，否则显示约数）
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        String label = rv.hasPart(SixtySecondsRvPart.NAVIGATION_ARRAY)
                ? dist + "m"
                : "~" + (dist / 10 * 10) + "m";
        Component nav = Component.translatable("message.noellesroles.sixty_seconds.rv_nav")
                .copy().append(Component.literal(" " + label));
        g.drawString(client.font, nav, cx - client.font.width(nav) / 2, y + stripH + 2, 0xFFFFD24A);
    }

    private static void drawCardinal(FakeGuiGraphics g, Minecraft client, int cx, int y, int left, int right,
            float yaw, double azimuth, String letter) {
        double rel = Mth.wrapDegrees(azimuth - yaw);
        if (Math.abs(rel) > NAV_HALF_FOV) {
            return;
        }
        int px = cx + (int) Math.round(rel * (NAV_HALF_WIDTH / (double) NAV_HALF_FOV));
        px = Mth.clamp(px, left, right);
        g.drawString(client.font, letter, px - client.font.width(letter) / 2, y + 2, 0xFFC8B898);
    }

    // ─────────────────────────────────────────────────────────────────
    // 辅助
    // ─────────────────────────────────────────────────────────────────

    private static SixtySecondsRvEntity findTeamRv(Minecraft client, int teamId) {
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof SixtySecondsRvEntity rv && rv.teamId() == teamId) {
                return rv;
            }
        }
        return null;
    }

    private static boolean isHoldingCompass(LocalPlayer player) {
        return isCompass(player.getMainHandItem()) || isCompass(player.getOffhandItem());
    }

    private static boolean isCompass(ItemStack stack) {
        return stack.is(ModItems.SIXTY_SECONDS_COMPASS);
    }
}
