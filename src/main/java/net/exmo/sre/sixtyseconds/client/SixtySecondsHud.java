package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsClockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 末日60秒模式 HUD：<b>物品栏上方的现代化状态面板</b> + <b>左上角时间信息</b>。
 * <p>
 * 时间信息（第 X/N 天 · 家庭身份 · 时钟 · 警示）放在屏幕左上角，从 y=30 向下自动排列。
 * 物品栏上方面板仅显示状态条：健康独占一行（全宽）+ 饥饿/口渴/理智/污染 四条横排。
 * <ul>
 *   <li>健康值上限 = {@link SixtySecondsStatsComponent#HEALTH_MAX}（150），不再被 100 截断。</li>
 *   <li>理智上限缺口（杀人永久降上限）保留：sanityMax &lt; 100 时画暗红锁死区。</li>
 *   <li>低值（≤25%）脉冲红框警示；污染满值才警示。</li>
 *   <li>倒地 / 自动复活覆盖层画在屏幕中央（与面板位置无关）。</li>
 * </ul>
 */
public final class SixtySecondsHud {
    // ── 面板布局 ──
    private static final int PANEL_W = 200;
    private static final int PAD = 5;
    /** 原版 hotbar 顶端 y = guiHeight - 39。 */
    private static final int HOTBAR_TOP_OFFSET = 39;
    private static final int GAP_ABOVE_HOTBAR = 4;
    private static final int SEP_H = 2;
    private static final int BAR_H = 4;            // 纯色简约：原 5，-25% 厚度
    private static final int HEALTH_BAR_H = 5;     // 纯色简约：原 7，-25% 厚度
    private static final int VALUE_GAP = 2;
    private static final int VALUE_H = 9;
    private static final int STAT_GAP = 4;
    private static final int STAT_COUNT = 4; // 饥饿/口渴/理智/污染（健康单独一行）
    private static final int ROW_GAP = 3;
    private static final double LOW_RATIO = 0.25;

    // ── 左上角信息布局 ──
    private static final int INFO_X = 6;
    private static final int INFO_Y_START = 30;
    private static final int INFO_LINE_H = 11;

    // ── 纯色简约配色（ARGB）──
    private static final int COL_TITLE = 0xFFE8D9A8;     // 标题/状态名（浅金）
    private static final int COL_FAMILY = 0xFF5EB7D8;    // 身份（功能蓝）
    private static final int COL_VALUE = 0xFFF0F0F0;     // 数值（亮白）

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

        if (stats.teamId < 0) {
            SixtySecondsStateAlerts.reset();
            return;
        }

        SixtySecondsStateAlerts.tick(graphics, client, player, stats);

        if (stats.downed) {
            renderDownedOverlay(graphics, client, player, stats);
        }
        if (stats.reviveEndTick > 0L) {
            renderReviveOverlay(graphics, client, player, stats);
        }

        // ── 计算时间/状态信息 ──
        boolean hasFamily = stats.familyPosition != null;
        long gameTime = client.level.getGameTime();
        long remaining = stats.phaseEndTick - gameTime;
        boolean isDayPhase = stats.dayNumber >= 1 && remaining > 0
                && remaining <= net.exmo.sre.sixtyseconds.SixtySecondsDayCycle.DAY_TOTAL_TICKS;
        boolean hasClock = isDayPhase && (hasClockInInventory(player) || isInShelterZone(player));
        boolean prepCountdown = stats.dayNumber == 0 && remaining > 0;
        long exploreCd = stats.exploreCooldownEndTick - gameTime;
        boolean hasExploreCd = exploreCd > 0;

        float pulse = 0.55f + 0.45f * Mth.sin(player.tickCount * 0.35f);

        // ── 左上角：时间信息（向下自动排列）──
        renderTopLeftInfo(graphics, client, stats, hasFamily, remaining, hasClock,
                prepCountdown, hasExploreCd, exploreCd, pulse);

        // ── 物品栏上方：状态面板（4条横排 + 健康独占一行）──
        renderStatusBarPanel(graphics, client, stats);
    }

    /**
     * 左上角时间信息：第 X/N 天 / 家庭身份 / 时钟 / 警示行，从 y=30 向下自动排列。
     */
    private static void renderTopLeftInfo(FakeGuiGraphics graphics, Minecraft client,
            SixtySecondsStatsComponent stats, boolean hasFamily, long remaining, boolean hasClock,
            boolean prepCountdown, boolean hasExploreCd, long exploreCd, float pulse) {
        int x = INFO_X;
        int y = INFO_Y_START;

        // 第 X/N 天
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.sixty_seconds.day",
                        Math.max(0, stats.dayNumber), stats.totalDays),
                x, y, COL_TITLE);
        y += INFO_LINE_H;

        // 家庭身份
        if (hasFamily) {
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.family."
                            + stats.familyPosition.name().toLowerCase()),
                    x, y, COL_FAMILY);
            y += INFO_LINE_H;
        }

        // 日内时钟
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
            y += INFO_LINE_H;
        }

        // 准备阶段倒计时（最后 10 秒红色脉冲）
        if (prepCountdown) {
            int seconds = (int) Math.ceil(remaining / 20.0);
            int color = seconds <= 10 ? (((int) (0x80 + 0x7F * pulse)) << 24 | 0xFF5050) : 0xFFFFD08A;
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.prep_countdown", seconds), x, y, color);
            y += INFO_LINE_H;
        }

        // 生病警示
        if (stats.sick) {
            int color = ((int) (0x90 + 0x6F * pulse)) << 24 | 0xFF6060;
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.sick_warning"), x, y, color);
            y += INFO_LINE_H;
        }

        // 探索归来冷却
        if (hasExploreCd) {
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.explore_cooldown",
                            (int) Math.ceil(exploreCd / 20.0)), x, y, COL_FAMILY);
            y += INFO_LINE_H;
        }
    }

    /**
     * 物品栏上方状态面板（纯色简约风、无背景）：
     * 饥饿/口渴/理智/污染 四条横排在上（数值位置保持原样），健康独占一行（全宽 + 右侧数值）在下方。
     * 按下 Shift 时在每个状态条的数值上方显示其名称。整体下移 5px。
     */
    private static void renderStatusBarPanel(FakeGuiGraphics graphics, Minecraft client,
            SixtySecondsStatsComponent stats) {
        boolean shift = isShiftDown(client);
        int statRowH = BAR_H + VALUE_GAP + VALUE_H;
        int healthRowH = Math.max(HEALTH_BAR_H, VALUE_H);
        int panelH = PAD + statRowH + ROW_GAP + healthRowH + PAD;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int panelX = (screenW - PANEL_W) / 2;
        // 整体下移 5px（不显示背景，仅状态条整体下沉）
        int panelY = screenH - HOTBAR_TOP_OFFSET - panelH - GAP_ABOVE_HOTBAR + 5;

        int x = panelX + PAD;
        int y = panelY + PAD;
        int usableW = PANEL_W - PAD * 2;

        // ── 第 1 行：饥饿 / 口渴 / 理智 / 污染（4 条横排）──（数值位置不动）
        int statW = (usableW - STAT_GAP * (STAT_COUNT - 1)) / STAT_COUNT;
        int[] statXs = new int[STAT_COUNT];
        for (int i = 0; i < STAT_COUNT; i++) {
            statXs[i] = x + i * (statW + STAT_GAP);
        }

        drawStat(graphics, client, statXs[0], y, statW,
                stats.hunger, stats.hungerMax, 0xFFE0A030, shift, false, "饥饿");
        drawStat(graphics, client, statXs[1], y, statW,
                stats.thirst, stats.thirstMax, 0xFF37A7E6, shift, false, "口渴");
        drawStat(graphics, client, statXs[2], y, statW,
                stats.sanity, Math.max(stats.sanityMax, SixtySecondsStatsComponent.MAX),
                0xFFB06AE6, shift, false, "理智");
        drawStat(graphics, client, statXs[3], y, statW,
                stats.pollution, stats.pollutionMax, 0xFF74B04A, shift, true, "污染");

        // ── 第 2 行：健康（全宽条 + 右侧数值），位于其他状态条下方 ──
        y += statRowH + ROW_GAP;
        String healthText = String.valueOf(Mth.clamp(stats.health, 0, stats.healthMax));
        int healthTextW = client.font.width(healthText);
        int healthBarW = usableW - healthTextW - 4;
        int healthValX = x + healthBarW + 4;
        drawHealthBar(graphics, client, x, y, healthBarW, stats.health, stats.healthMax, 0xFFE64848);
        graphics.drawString(client.font, healthText, healthValX, y, COL_VALUE);
        if (shift) {
            graphics.drawString(client.font, "健康", healthValX, y - VALUE_H - 1, COL_TITLE);
        }
    }

    /**
     * 倒地覆盖层（屏幕中央准星下方）。
     */
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
        int health = stats.health;
        Component healthText = Component.translatable("hud.noellesroles.sixty_seconds.downed_health", health);
        int healthColor = health > 15 ? 0xFFFFA0A0 : 0xFFFF4040;
        graphics.drawString(client.font, healthText, cx - client.font.width(healthText) / 2, y, healthColor);
        y += 11;

        Component hint = Component.translatable("hud.noellesroles.sixty_seconds.downed_hint",
                net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.REVIVE_TICKS / 20);
        graphics.drawString(client.font, hint, cx - client.font.width(hint) / 2, y, 0xFFB0B8C0);
    }

    /**
     * 自动复活倒计时（屏幕中央）。
     */
    private static void renderReviveOverlay(FakeGuiGraphics graphics, Minecraft client, LocalPlayer player,
            SixtySecondsStatsComponent stats) {
        long remainingTicks = stats.reviveEndTick - client.level.getGameTime();
        if (remainingTicks < 0) {
            remainingTicks = 0;
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

    /**
     * 健康条（纯色简约：仅实心填充，无轨道/边框/刻度/脉冲）：低值时整条变红。
     * 数值由调用方绘制在右侧。
     */
    private static void drawHealthBar(FakeGuiGraphics g, Minecraft client, int x, int y, int w,
            int value, int max, int color) {
        int clamped = Mth.clamp(value, 0, max);
        double ratio = max > 0 ? clamped / (double) max : 0;
        boolean low = ratio <= LOW_RATIO;
        int fill = low ? 0xFFFF4040 : color;

        int fillW = (int) Math.round(w * ratio);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + HEALTH_BAR_H, fill);
        }
    }

    /**
     * 绘制单个状态值（纯色简约：仅实心填充，无轨道/边框/刻度）：色条 + 下方居中数值，
     * 数值位置保持原样。Shift 按下时在数值上方显示名称。
     *
     * @param max       进度条满值（ratio 分母 + clamp 上限）
     * @param highIsBad true=越高越坏（污染），满值视为警示
     */
    private static void drawStat(FakeGuiGraphics g, Minecraft client, int x, int y, int w,
            int value, int max, int color, boolean shift, boolean highIsBad, String name) {
        int clamped = Mth.clamp(value, 0, max);
        double ratio = max > 0 ? clamped / (double) max : 0;
        boolean low = highIsBad ? clamped >= max : ratio <= LOW_RATIO;
        int fill = low ? 0xFFFF6060 : color;

        int fillW = (int) Math.round(w * ratio);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + BAR_H, fill);
        }

        String text = String.valueOf(clamped);
        int tw = client.font.width(text);
        g.drawString(client.font, text, x + (w - tw) / 2, y + BAR_H + VALUE_GAP,
                low ? 0xFFFF6060 : COL_VALUE);

        if (shift) {
            int nameW = client.font.width(name);
            g.drawString(client.font, name, x + (w - nameW) / 2, y - VALUE_H - 1, COL_TITLE);
        }
    }

    /** 是否按住 Shift（用于显式显示各状态条名称）。 */
    private static boolean isShiftDown(Minecraft client) {
        return client.options.keyShift.isDown();
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

    /** 检查玩家是否在避难所/住宅安全区。 */
    private static boolean isInShelterZone(LocalPlayer player) {
        AABB zone = SixtySecondsClientMapZone.activeZone();
        if (zone == null || !SixtySecondsClientMapZone.isInSafeZone()) {
            return false;
        }
        return zone.contains(player.getX(), player.getY(), player.getZ());
    }
}
