package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsClockItem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 末日60秒模式 HUD：<b>左侧垂直居中</b>的复古车票风面板（配色见 docs/ui_style.md），
 * 显示「第 X/7 天 + 家庭身份 + 饥饿/口渴/理智/污染/健康」。
 * 每条 = 色签 + 名称 + 分段刻度进度条 + 数值；数值过低（≤{@link #LOW_RATIO}）加红框脉冲警示。
 * 面板<b>顶端锚点固定</b>（按典型高度对屏幕垂直居中），临时警示行只向下扩展、不会让面板跳动；
 * 左中位置与顶部中央信息行/右上代币/左下聊天/右下技能 HUD 互不遮挡。
 * 参照 {@code net.exmo.sre.repair.client.RepairEscapeHud}（{@code CommonHudRenderCallback.EVENT}）。
 */
public final class SixtySecondsHud {
    private static final int PANEL_X = 6;
    private static final int PANEL_W = 142;
    /** 顶端锚点 = 屏幕中线上移半个典型面板高（标题+身份+时钟+5条 ≈ 124px）。 */
    private static final int PANEL_ANCHOR_UP = 62;
    private static final int PAD = 7;
    private static final int ROW_H = 13;
    private static final int BAR_H = 8;
    /** 头部信息与状态条之间的分隔线区高度。 */
    private static final int SEP_H = 4;
    private static final double LOW_RATIO = 0.25;

    // 配色（ARGB，取自 docs/ui_style.md 基础色板）
    private static final int COL_BG_TOP = 0xD81A1008;      // 面板背景（上）
    private static final int COL_BG_BOTTOM = 0xD820140A;   // 面板背景（下）
    private static final int COL_BORDER = 0xFF8B6914;      // 棕褐描边
    private static final int COL_DECO_LINE = 0x33FFE8C0;   // 顶部装饰线
    private static final int COL_TRACK = 0xFF2A1B0E;       // 进度条轨道（暖棕黑）
    private static final int COL_TRACK_EDGE = 0xFF120A04;  // 轨道外框
    private static final int COL_TICK = 0x2EFFE8C0;        // 25% 分段刻度
    private static final int COL_TITLE = 0xFFD4AF37;       // 亮金标题
    private static final int COL_FAMILY = 0xFF5EB7D8;      // 功能蓝（身份）
    private static final int COL_LABEL = 0xFFC8B898;       // 暗米色正文
    private static final int COL_VALUE = 0xFFFFF4DC;       // 主文字（浅奶油）

    private SixtySecondsHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isRunning()
                || SixtySecondsMod.MODE == null
                || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
            SixtySecondsStateAlerts.reset();
            return;
        }
        LocalPlayer player = client.player;
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);

        // 未入队者（点了「不参与」的玩家/旁观者）不渲染 60s 状态栏与警示
        if (stats.teamId < 0) {
            SixtySecondsStateAlerts.reset();
            return;
        }

        // 低状态警示：受伤红闪 / 低健康边缘脉冲 / 跨档文本提示（先画，位于面板之下）
        SixtySecondsStateAlerts.tick(graphics, client, player, stats);

        if (stats.downed) {
            renderDownedOverlay(graphics, client, player, stats);
        }
        // 自动复活倒计时（死亡后才有值；剩余时间由本地按 gameTime 推算，零同步）
        if (stats.reviveEndTick > 0L) {
            renderReviveOverlay(graphics, client, player, stats);
        }

        boolean hasFamily = stats.familyPosition != null;
        long gameTime = client.level.getGameTime();
        long remaining = stats.phaseEndTick - gameTime;
        // 默认不显示时间；只有背包中有末日时钟，或在避难所安全区时才显示
        boolean isDayPhase = stats.dayNumber >= 1 && remaining > 0
                && remaining <= net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.DAY_TOTAL_TICKS;
        boolean hasClock = isDayPhase && (hasClockInInventory(player) || isInShelterZone(player));
        boolean prepCountdown = stats.dayNumber == 0 && remaining > 0;
        long exploreCd = stats.exploreCooldownEndTick - gameTime;
        boolean hasExploreCd = exploreCd > 0;
        int rows = 5;
        int headerH = 14 + (hasFamily ? 11 : 0) + (hasClock ? 11 : 0) + (prepCountdown ? 11 : 0)
                + (stats.sick ? 11 : 0) + (hasExploreCd ? 11 : 0);
        int panelH = PAD + headerH + SEP_H + rows * ROW_H + PAD - 2;

        // 左侧垂直居中；顶端锚点固定（不随临时警示行增减跳动），只向下扩展
        int panelY = Math.max(6, graphics.guiHeight() / 2 - PANEL_ANCHOR_UP);
        drawPanel(graphics, PANEL_X, panelY, PANEL_W, panelH);

        int x = PANEL_X + PAD;
        int y = panelY + PAD;

        // 标题：末日生存 · 第 X/N 天（N=本局总日数，按玩家同步过来，可按图配置）
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.sixty_seconds.day",
                        Math.max(0, stats.dayNumber), stats.totalDays),
                x, y, COL_TITLE);
        y += 14;

        if (hasFamily) {
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.family."
                            + stats.familyPosition.name().toLowerCase()),
                    x, y, COL_FAMILY);
            y += 11;
        }

        // 日内时钟：子相位 + 剩余时间（phaseEndTick 每日同步一次，客户端本地推算）
        if (hasClock) {
            boolean sleep = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.isSleepWindowByRemaining(remaining);
            Component subName = sleep
                    ? Component.translatable("hud.noellesroles.sixty_seconds.subphase.sleep")
                    : Component.translatable(net.exmo.sre.sixtyseconds.SixtySecondsDayCycle
                            .subPhaseByRemaining(remaining).translationKey());
            long left = net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhaseRemainingByRemaining(remaining);
            long seconds = left / 20;
            String time = String.format("%02d:%02d", seconds / 60, seconds % 60);
            int color = sleep ? 0xFFB06AE6
                    : net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.subPhaseByRemaining(remaining)
                            == net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.SubPhase.NIGHT
                                    ? 0xFF6FA8FF : 0xFFFFD08A;
            graphics.drawString(client.font,
                    Component.empty().append(subName).append(Component.literal(" " + time)), x, y, color);
            y += 11;
        }

        float pulse = 0.55f + 0.45f * Mth.sin(player.tickCount * 0.35f); // 低值警示脉冲

        // 准备阶段倒计时（最后 10 秒红色脉冲）
        if (prepCountdown) {
            int seconds = (int) Math.ceil(remaining / 20.0);
            int color = seconds <= 10 ? (((int) (0x80 + 0x7F * pulse)) << 24 | 0xFF5050) : 0xFFFFD08A;
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.prep_countdown", seconds), x, y, color);
            y += 11;
        }
        // 生病警示（红色脉冲：必须服药否则发烧掉血）
        if (stats.sick) {
            int color = ((int) (0x90 + 0x6F * pulse)) << 24 | 0xFF6060;
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.sick_warning"), x, y, color);
            y += 11;
        }
        // 探索归来冷却
        if (hasExploreCd) {
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.explore_cooldown",
                            (int) Math.ceil(exploreCd / 20.0)), x, y, COL_FAMILY);
            y += 11;
        }

        // 头部与状态条之间的分隔线（车票风：细金线）
        graphics.fill(x, y + 1, PANEL_X + PANEL_W - PAD, y + 2, 0x4D8B6914);
        y += SEP_H;

        y = bar(graphics, client, x, y, "hunger", stats.hunger, 0xFFE0A030, pulse, false);
        y = bar(graphics, client, x, y, "thirst", stats.thirst, 0xFF37A7E6, pulse, false);
        y = bar(graphics, client, x, y, "sanity", stats.sanity, 0xFFB06AE6, pulse, false, stats.sanityMax);
        // 污染是「越高越坏」：只有满值才闪红警示
        y = bar(graphics, client, x, y, "pollution", stats.pollution, 0xFF74B04A, pulse, true);
        bar(graphics, client, x, y, "health", stats.health, 0xFFE64848, pulse, false);
    }

    /**
     * 倒地覆盖层（屏幕中央准星下方）：红色脉冲大字「你已倒地」+ 剩余健康值 + 救援提示。
     * 倒地健康值随补刀/时间流失，归零死亡。
     */
    /**
     * 自动复活倒计时：死亡后画在屏幕中央——「距离复活 m:ss」+ 尸体已标记的提示。
     * 剩余时间 = {@code reviveEndTick - gameTime} 本地推算，服务端只在死亡/复活时各同步一次
     * （同 phaseEndTick 的纪律，见 ai_doc.md）。
     */
    private static void renderReviveOverlay(FakeGuiGraphics graphics, Minecraft client, LocalPlayer player,
            SixtySecondsStatsComponent stats) {
        long remainingTicks = stats.reviveEndTick - client.level.getGameTime();
        if (remainingTicks < 0) {
            remainingTicks = 0; // 到点了但服务端那一 tick 还没跑到：显示 0:00 而不是负数
        }
        int totalSeconds = (int) Math.ceil(remainingTicks / 20.0);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;

        Component title = Component.translatable("hud.noellesroles.sixty_seconds.revive_title");
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy - 60, 0);
        graphics.pose().scale(1.4f, 1.4f, 1f);
        graphics.drawString(client.font, title, -client.font.width(title) / 2, 0, 0xFFE8D9A8);
        graphics.pose().popPose();

        // 倒计时数字：最后 10 秒转绿并脉冲，给「马上要活了」的预期
        boolean soon = totalSeconds <= 10;
        float pulse = 0.6f + 0.4f * Mth.sin(player.tickCount * 0.4f);
        int color = soon ? ((Mth.clamp((int) (0xC0 + 0x3F * pulse), 0, 0xFF) << 24) | 0x60FF60) : 0xFFFFFFFF;
        Component time = Component.translatable("hud.noellesroles.sixty_seconds.revive_countdown",
                totalSeconds / 60, String.format("%02d", totalSeconds % 60));
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy - 40, 0);
        graphics.pose().scale(2.0f, 2.0f, 1f);
        graphics.drawString(client.font, time, -client.font.width(time) / 2, 0, color);
        graphics.pose().popPose();

        Component hint = Component.translatable("hud.noellesroles.sixty_seconds.revive_hint");
        graphics.drawString(client.font, hint, cx - client.font.width(hint) / 2, cy - 16, 0xFFAAAAAA);
    }

    private static void renderDownedOverlay(FakeGuiGraphics graphics, Minecraft client, LocalPlayer player,
            SixtySecondsStatsComponent stats) {
        float pulse = 0.55f + 0.45f * Mth.sin(player.tickCount * 0.35f);
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;

        Component title = Component.translatable("hud.noellesroles.sixty_seconds.downed_title");
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy + 28, 0);
        graphics.pose().scale(1.5f, 1.5f, 1f);
        int alpha = Mth.clamp((int) (0xB0 + 0x4F * pulse), 0, 0xFF);
        graphics.drawString(client.font, title, -client.font.width(title) / 2, 0, (alpha << 24) | 0xFF4040);
        graphics.pose().popPose();

        int y = cy + 46;
        // 倒地健康值
        int health = stats.health;
        Component healthText = Component.translatable("hud.noellesroles.sixty_seconds.downed_health", health);
        int healthColor = health > 15 ? 0xFFFFA0A0 : 0xFFFF4040;
        graphics.drawString(client.font, healthText, cx - client.font.width(healthText) / 2, y, healthColor);
        y += 11;

        Component hint = Component.translatable("hud.noellesroles.sixty_seconds.downed_hint",
                net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.REVIVE_TICKS / 20);
        graphics.drawString(client.font, hint, cx - client.font.width(hint) / 2, y, COL_LABEL);
    }

    /** 车票风面板：上下渐变底 + 棕褐描边 + 顶部装饰线（ui_style 三步范式）。 */
    private static void drawPanel(FakeGuiGraphics g, int px, int py, int w, int h) {
        g.fillGradient(px, py, px + w, py + h, COL_BG_TOP, COL_BG_BOTTOM);
        g.renderOutline(px, py, w, h, COL_BORDER);
        g.fill(px + 1, py + 1, px + w - 1, py + 2, COL_DECO_LINE);
    }

    /** @param highIsBad true=数值越高越坏（污染）：仅满值警示；false=低值（≤25%）警示。 */
    private static int bar(FakeGuiGraphics graphics, Minecraft client, int x, int y, String key, int value,
            int color, float pulse, boolean highIsBad) {
        return bar(graphics, client, x, y, key, value, color, pulse, highIsBad, SixtySecondsStatsComponent.MAX);
    }

    /** 带上限缺口的变体：{@code capMax} < 100 时条右端画暗红「锁死区」（杀人永久降理智上限）。 */
    private static int bar(FakeGuiGraphics graphics, Minecraft client, int x, int y, String key, int value,
            int color, float pulse, boolean highIsBad, int capMax) {
        int max = SixtySecondsStatsComponent.MAX;
        int clamped = Mth.clamp(value, 0, max);
        double ratio = clamped / (double) max;
        boolean low = highIsBad ? clamped >= max : ratio <= LOW_RATIO;

        // 色签 + 名称
        graphics.fill(x, y + 1, x + 2, y + BAR_H - 1, color);
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.sixty_seconds." + key), x + 5, y, COL_LABEL);

        int barX = x + 30;
        int barRight = PANEL_X + PANEL_W - PAD;
        int barW = barRight - barX;

        // 轨道（带 1px 暗框，嵌入感）
        graphics.fill(barX - 1, y - 1, barRight + 1, y + BAR_H + 1, COL_TRACK_EDGE);
        graphics.fill(barX, y, barRight, y + BAR_H, COL_TRACK);
        // 填充：本色 + 顶部高光线 + 底部阴影线（层次感）
        int fillW = (int) Math.round(barW * ratio);
        if (fillW > 0) {
            graphics.fill(barX, y, barX + fillW, y + BAR_H, color);
            graphics.fill(barX, y, barX + fillW, y + 1, 0x50FFFFFF);
            graphics.fill(barX, y + BAR_H - 1, barX + fillW, y + BAR_H, 0x33000000);
        }
        // 上限缺口（杀人永久降理智上限）：capMax..100 区间画暗红锁死区盖住轨道
        if (capMax < max) {
            int capX = barX + (int) Math.round(barW * (capMax / (double) max));
            graphics.fill(capX, y, barRight, y + BAR_H, 0xB03A1418);
            graphics.fill(capX, y, capX + 1, y + BAR_H, 0xFF802828);
        }
        // 分段刻度（每 25%）
        for (int i = 1; i < 4; i++) {
            int tx = barX + barW * i / 4;
            graphics.fill(tx, y, tx + 1, y + BAR_H, COL_TICK);
        }
        // 低值红框脉冲
        if (low) {
            int a = (int) (0x50 + 0xAF * pulse) << 24;
            int warn = a | 0x00FF4040;
            graphics.fill(barX, y, barRight, y + 1, warn);
            graphics.fill(barX, y + BAR_H - 1, barRight, y + BAR_H, warn);
            graphics.fill(barX, y, barX + 1, y + BAR_H, warn);
            graphics.fill(barRight - 1, y, barRight, y + BAR_H, warn);
        }
        // 数值（条右侧内对齐）
        String text = String.valueOf(clamped);
        int tw = client.font.width(text);
        graphics.drawString(client.font, text, barRight - tw - 2, y, low ? 0xFFFF6060 : COL_VALUE);

        return y + ROW_H;
    }

    /** 检查玩家背包中是否持有末日时钟（主物品栏 + 副手）。 */
    private static boolean hasClockInInventory(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SixtySecondsClockItem) {
                return true;
            }
        }
        return player.getOffhandItem().getItem() instanceof SixtySecondsClockItem;
    }

    /** 检查玩家是否在避难所/住宅安全区（服务端推送的安全区 AABB 内）。 */
    private static boolean isInShelterZone(LocalPlayer player) {
        AABB zone = SixtySecondsClientMapZone.activeZone();
        if (zone == null || !SixtySecondsClientMapZone.isInSafeZone()) {
            return false;
        }
        return zone.contains(player.getX(), player.getY(), player.getZ());
    }
}
