package net.exmo.sre.sixtyseconds.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.network.SupplySearchS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 物资箱「搜刮」进度条（搜打撤式）：搜刮时在准星下方画一条分段进度条 + 扫描光带 + 百分比；
 * 中断闪红「已中断」约 0.75 秒。完成后弹出「获得物资」面板，搜出的物品<b>逐件揭示</b>
 * （每件间隔 {@link #REVEAL_INTERVAL} tick，入场滑入 + 高光闪白 + 拾取音，搜打撤同款），
 * 停留片刻后整体淡出。进度由客户端按 tick + partialTick 平滑推进。配色遵循 docs/ui_style.md。
 */
public final class SixtySecondsSearchHud {
    private static final int BAR_W = 160;
    private static final int BAR_H = 11;

    // ── 揭示动画参数（tick）──────────────────────────────────────────
    private static final int REVEAL_INTERVAL = 5;   // 每件物资间隔 0.25s
    private static final int REVEAL_ANIM = 8;       // 单件入场动画时长
    private static final int REVEAL_HOLD = 50;      // 最后一件揭示后的停留
    private static final int REVEAL_FADE = 10;      // 整体淡出
    private static final int ROW_H = 20;

    // 状态：0=无，1=搜刮中，2=完成（闪光+物资揭示），3=中断闪光
    private static int state = 0;
    private static long startTick = 0;
    private static int durationTicks = 0;
    private static long flashUntilTick = 0;
    private static float lastProgress = 0f;
    // 完成揭示
    private static final List<ItemStack> revealItems = new ArrayList<>();
    private static long revealStartTick = 0;
    private static int revealedCount = 0; // 已播过入场音的件数

    private SixtySecondsSearchHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) ->
                render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }

    /** 收到服务端搜刮事件。 */
    public static void onPacket(SupplySearchS2CPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.player != null ? mc.player.tickCount : 0;
        switch (packet.state()) {
            case SupplySearchS2CPacket.STATE_START -> {
                state = 1;
                startTick = now;
                durationTicks = Math.max(1, packet.durationTicks());
                revealItems.clear();
            }
            case SupplySearchS2CPacket.STATE_COMPLETE -> {
                state = 2;
                flashUntilTick = now + 15;
                revealItems.clear();
                for (ItemStack stack : packet.items()) {
                    if (!stack.isEmpty()) {
                        revealItems.add(stack);
                    }
                }
                revealStartTick = now;
                revealedCount = 0;
            }
            case SupplySearchS2CPacket.STATE_CANCEL -> {
                state = 3;
                flashUntilTick = now + 15;
            }
            default -> {
            }
        }
    }

    public static void reset() {
        state = 0;
        revealItems.clear();
    }

    private static void render(FakeGuiGraphics g, float partialTick) {
        if (state == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            state = 0;
            revealItems.clear();
            return;
        }
        long now = mc.player.tickCount;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 16;
        int x = cx - BAR_W / 2;

        if (state == 2 || state == 3) {
            boolean done = state == 2;
            boolean flashing = now < flashUntilTick;
            if (flashing) {
                float fade = (float) (flashUntilTick - now) / 15f; // 1→0
                int overlayAlpha = (int) (0x77 * fade) << 24;
                drawBarFrame(g, x, y, done ? 1f : lastProgress);
                // 半透明绿/红覆盖，随时间淡出
                g.fill(x, y, x + BAR_W, y + BAR_H, overlayAlpha | (done ? 0x5CE06A : 0xE05C5C));
                String key = done ? "hud.noellesroles.sixty_seconds.search_done"
                        : "hud.noellesroles.sixty_seconds.search_cancel";
                Component text = Component.translatable(key);
                g.drawString(mc.font, text, cx - mc.font.width(text) / 2, y - 12,
                        done ? 0xFF7CE08A : 0xFFE07C7C);
            }
            boolean revealing = done && !revealItems.isEmpty()
                    && renderReveal(g, mc, cx, y + BAR_H + 8, now, partialTick);
            if (!flashing && !revealing) {
                state = 0;
                revealItems.clear();
            }
            return;
        }

        // 搜刮中：若久未收到完成/中断包（如游戏中途强停），自动清除避免卡住
        if (now - startTick > durationTicks + 40) {
            state = 0;
            return;
        }
        float progress = Mth.clamp(((now - startTick) + partialTick) / durationTicks, 0f, 1f);
        lastProgress = progress;
        drawBarFrame(g, x, y, progress);

        // 扫描光带（在已填充区域内左右扫动）
        int fillW = (int) (BAR_W * progress);
        if (fillW > 4) {
            float t = (float) ((now % 20) + partialTick) / 20f;
            int sx = x + (int) (t * (fillW - 3));
            g.fill(sx, y + 1, sx + 3, y + BAR_H - 1, 0x88FFF4DC);
        }

        // 文字：搜刮中 xx%
        int pct = (int) (progress * 100);
        Component label = Component.translatable("hud.noellesroles.sixty_seconds.searching", pct);
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, y - 12, 0xFFFFE066);
    }

    /**
     * 完成后的「获得物资」揭示面板：物品逐件滑入 + 闪白高光 + 拾取音，停留后整体淡出。
     * @return 面板是否仍在展示（false = 揭示流程结束）
     */
    private static boolean renderReveal(FakeGuiGraphics g, Minecraft mc, int cx, int top,
            long now, float partialTick) {
        float elapsed = (now - revealStartTick) + partialTick;
        float lastRevealAt = (revealItems.size() - 1) * (float) REVEAL_INTERVAL;
        float end = lastRevealAt + REVEAL_ANIM + REVEAL_HOLD + REVEAL_FADE;
        if (elapsed >= end) {
            return false;
        }
        // 整体淡出因子（仅作用于面板/文字；图标在过淡时直接不画）
        float fadeOut = elapsed > lastRevealAt + REVEAL_ANIM + REVEAL_HOLD
                ? 1f - (elapsed - lastRevealAt - REVEAL_ANIM - REVEAL_HOLD) / REVEAL_FADE
                : 1f;
        fadeOut = Mth.clamp(fadeOut, 0f, 1f);
        if (fadeOut < 0.05f) {
            return true; // 近全透明帧：alpha≈0 会被字体渲染当作不透明，直接跳过绘制
        }

        // 面板尺寸：宽随最长物品名自适应
        int textW = 0;
        for (ItemStack stack : revealItems) {
            textW = Math.max(textW, mc.font.width(rowLabel(stack)));
        }
        Component title = Component.translatable("hud.noellesroles.sixty_seconds.loot_gained");
        textW = Math.max(textW, mc.font.width(title) - 22);
        int panelW = Mth.clamp(6 + 18 + 4 + textW + 8, 120, 240);
        int panelH = 6 + 12 + revealItems.size() * ROW_H + 4;
        int px = cx - panelW / 2;

        // 面板：渐变底 + 棕褐描边 + 顶部装饰线（ui_style.md 三步范式）
        g.fillGradient(px, top, px + panelW, top + panelH,
                withAlpha(0xD81A1008, fadeOut), withAlpha(0xD820140A, fadeOut));
        g.renderOutline(px, top, panelW, panelH, withAlpha(0xFF8B6914, fadeOut));
        g.fill(px + 1, top + 1, px + panelW - 1, top + 2, withAlpha(0x33FFE8C0, fadeOut));
        g.drawString(mc.font, title, px + 6, top + 5, withAlpha(0xFFD4AF37, fadeOut), false);

        boolean iconsVisible = fadeOut > 0.35f;
        for (int i = 0; i < revealItems.size(); i++) {
            float revealAt = i * (float) REVEAL_INTERVAL;
            if (elapsed < revealAt) {
                break; // 尚未揭示：后面的更没到，直接停
            }
            // 首帧揭示：播放拾取音（逐件“叮”一声）
            if (i >= revealedCount) {
                revealedCount = i + 1;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.ITEM_PICKUP, 0.9f + i * 0.08f));
            }
            float t = Mth.clamp((elapsed - revealAt) / REVEAL_ANIM, 0f, 1f);
            float ease = easeOutCubic(t);
            int rowY = top + 6 + 12 + i * ROW_H;
            int slide = (int) ((1f - ease) * 14f); // 从左滑入 14px
            int rowX = px + 6 - slide;

            ItemStack stack = revealItems.get(i);
            if (iconsVisible) {
                g.renderItem(stack, rowX, rowY + 1);
                g.renderItemDecorations(mc.font, stack, rowX, rowY + 1);
            }
            int nameAlpha = (int) (0xFF * ease * fadeOut);
            if (nameAlpha > 4) {
                g.drawString(mc.font, rowLabel(stack), rowX + 22, rowY + 5,
                        (nameAlpha << 24) | 0xFFF4DC, false);
            }
            // 揭示瞬间的高光闪白：随入场进度淡出
            if (t < 1f) {
                int flashAlpha = (int) (0x66 * (1f - ease) * fadeOut);
                if (flashAlpha > 0) {
                    g.fill(px + 2, rowY, px + panelW - 2, rowY + ROW_H - 2, (flashAlpha << 24) | 0xFFE8C0);
                }
            }
        }
        return true;
    }

    private static Component rowLabel(ItemStack stack) {
        Component name = stack.getHoverName();
        return stack.getCount() > 1
                ? Component.empty().append(name).append(Component.literal(" ×" + stack.getCount()))
                : Component.empty().append(name);
    }

    private static int withAlpha(int argb, float mult) {
        int a = (int) ((argb >>> 24) * Mth.clamp(mult, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    /** 画进度条外框 + 轨道 + 金色填充 + 4 段刻度。 */
    private static void drawBarFrame(FakeGuiGraphics g, int x, int y, float progress) {
        // 外描边
        g.fill(x - 1, y - 1, x + BAR_W + 1, y, 0xFF3E2A12);
        g.fill(x - 1, y + BAR_H, x + BAR_W + 1, y + BAR_H + 1, 0xFF3E2A12);
        g.fill(x - 1, y, x, y + BAR_H, 0xFF3E2A12);
        g.fill(x + BAR_W, y, x + BAR_W + 1, y + BAR_H, 0xFF3E2A12);
        // 轨道
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xCC141014);
        // 填充
        int fillW = (int) (BAR_W * Mth.clamp(progress, 0f, 1f));
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + BAR_H, 0xFFD4AF37);
            g.fill(x, y, x + fillW, y + 1, 0x55FFFFFF); // 顶部高光
        }
        // 分段刻度
        for (int i = 1; i < 4; i++) {
            int tx = x + BAR_W * i / 4;
            g.fill(tx, y, tx + 1, y + BAR_H, 0x33000000);
        }
    }
}
