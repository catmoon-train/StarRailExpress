package net.exmo.sre.sixtyseconds.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 睡觉时间强制入眠黑屏演出（{@code SleepBlackoutS2CPacket} 触发）：全屏黑幕渐入渐出，
 * 期间轮播随机幸存者独白（{@link #MONOLOGUE_COUNT} 条语言键池，开演时洗牌不重复），
 * 下方实时显示自己入睡以来的状态变化（健康/饥饿/口渴/理智/污染 增减）。
 */
public final class SixtySecondsSleepOverlay {
    private static final int MONOLOGUE_COUNT = 40;
    private static final int FADE_IN = 20;         // 黑幕渐入 1s
    private static final int FADE_OUT = 20;        // 黑幕渐出 1s
    private static final int LINE_INTERVAL = 65;   // 每条独白展示 3.25s
    private static final Random RANDOM = new Random();

    private static boolean active = false;
    private static long startTick;
    private static int duration;
    /** 入睡瞬间的状态快照：health/hunger/thirst/sanity/pollution。 */
    private static int[] snapshot = new int[5];
    /** 本次演出各时段展示的独白编号（洗牌后取前 N，条数不足时循环）。 */
    private static final List<Integer> lineOrder = new ArrayList<>();

    private SixtySecondsSleepOverlay() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) ->
                render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }

    /** 收到服务端强制入眠包：开始黑屏演出。 */
    public static void start(int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        active = true;
        startTick = mc.player.tickCount;
        duration = Math.max(40, durationTicks);
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(mc.player);
        snapshot = new int[] { stats.health, stats.hunger, stats.thirst, stats.sanity, stats.pollution };
        lineOrder.clear();
        List<Integer> pool = new ArrayList<>(MONOLOGUE_COUNT);
        for (int i = 0; i < MONOLOGUE_COUNT; i++) {
            pool.add(i);
        }
        Collections.shuffle(pool, RANDOM);
        int slots = Math.max(1, duration / LINE_INTERVAL);
        for (int i = 0; i < slots; i++) {
            lineOrder.add(pool.get(i % pool.size()));
        }
    }

    public static void reset() {
        active = false;
        lineOrder.clear();
    }

    private static void render(FakeGuiGraphics g, float partialTick) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            reset();
            return;
        }
        float elapsed = (mc.player.tickCount - startTick) + partialTick;
        if (elapsed >= duration || elapsed < 0) {
            reset();
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float alpha = 1f;
        if (elapsed < FADE_IN) {
            alpha = elapsed / FADE_IN;
        } else if (elapsed > duration - FADE_OUT) {
            alpha = (duration - elapsed) / FADE_OUT;
        }
        alpha = Mth.clamp(alpha, 0f, 1f);
        g.fill(0, 0, w, h, ((int) (0xF6 * alpha)) << 24);
        if (alpha < 0.35f) {
            return; // 黑幕尚浅时不叠文字（低 alpha 字体渲染会被当成不透明）
        }

        // ── 独白：按时段轮播，条内渐入渐出 ─────────────────────────
        if (!lineOrder.isEmpty()) {
            int slot = Math.min((int) (elapsed / LINE_INTERVAL), lineOrder.size() - 1);
            float slotT = Mth.clamp((elapsed - slot * (float) LINE_INTERVAL) / LINE_INTERVAL, 0f, 1f);
            float lineAlpha = slotT < 0.2f ? slotT / 0.2f : slotT > 0.85f ? (1f - slotT) / 0.15f : 1f;
            int a = (int) (0xFF * Mth.clamp(lineAlpha, 0f, 1f) * alpha);
            if (a > 4) {
                Component line = Component.translatable(
                        "overlay.noellesroles.sixty_seconds.sleep_monologue." + lineOrder.get(slot));
                g.drawString(mc.font, line, w / 2 - mc.font.width(line) / 2, h / 2 - 24,
                        (a << 24) | 0xC8C8D8, false);
            }
        }

        // ── 自身状态变化：与入睡快照比对，非零项逐行显示 ─────────────
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(mc.player);
        int[] cur = { stats.health, stats.hunger, stats.thirst, stats.sanity, stats.pollution };
        String[] keys = { "health", "hunger", "thirst", "sanity", "pollution" };
        int textAlpha = (int) (0xE0 * alpha);
        int y = h / 2 + 6;
        for (int i = 0; i < cur.length; i++) {
            int delta = cur[i] - snapshot[i];
            if (delta == 0 || textAlpha <= 4) {
                continue;
            }
            boolean good = i == 4 ? delta < 0 : delta > 0; // 污染降=好，其余升=好
            Component text = Component.empty()
                    .append(Component.translatable("hud.noellesroles.sixty_seconds." + keys[i]))
                    .append(Component.literal((delta > 0 ? " +" : " ") + delta));
            g.drawString(mc.font, text, w / 2 - mc.font.width(text) / 2, y,
                    (textAlpha << 24) | (good ? 0x8AE08A : 0xE08A8A), false);
            y += 11;
        }
    }
}
