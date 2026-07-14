package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * 末日60秒模式的低状态警示（纯客户端，由 {@link SixtySecondsHud} 每 tick 驱动）：
 * <ul>
 *   <li><b>受伤红闪</b>：健康值单次掉幅 ≥{@link #FLASH_MIN_DROP} 时屏幕边缘红闪一次
 *       （画法参照 {@code RedScreenRenderer} 的边缘渐变）；带 {@link #FLASH_COOLDOWN_TICKS} 冷却，
 *       并过滤饥渴清空的 1/s 阴刻掉血——那种由下面的常驻边缘光负责。</li>
 *   <li><b>常驻低健康边缘脉冲</b>：健康 ≤{@link #LOW} 时边缘持续红光脉冲，越低越亮（倒地有专属覆盖层，不叠加）。</li>
 *   <li><b>低状态文本提示</b>：五值分「偏低/危急」两档（污染反向），<b>只在恶化跨档瞬间发一次</b>聊天消息
 *       + 提示音；好转需回升超过阈值+{@link #HYST} 才退档，防止在阈值附近抖动反复触发。绝不逐 tick 重发。</li>
 * </ul>
 * 另供 {@code SansRenderer}（低 san 滤镜复用）与 {@code ImmersiveFilterShader}（低状态滤镜）读取本地 60s 状态。
 */
public final class SixtySecondsStateAlerts {
    private static final int MAX = SixtySecondsStatsComponent.MAX;

    // 分档阈值与迟滞（进档立即、退档需回升超过阈值+HYST）
    private static final int LOW = 25;
    private static final int CRIT = 10;
    private static final int HYST = 10;

    // 受伤红闪
    private static final int FLASH_MIN_DROP = 3;         // 忽略饥渴/过夜的 1/s 阴刻掉血
    private static final int FLASH_COOLDOWN_TICKS = 60;  // 两次红闪至少间隔 3s，连续受击不刷屏
    private static final int FLASH_DURATION_TICKS = 14;

    /** 激活后前 3s 只记录不提示：跳过进局/重连时组件从默认 MAX 跳到真实值的首包跳变。 */
    private static final int WARMUP_TICKS = 60;

    private static int lastHealth = -1;
    private static int flashTicksLeft = 0;
    private static float flashIntensity = 0f;
    private static long lastFlashGameTime = Long.MIN_VALUE / 2;
    private static int warmup = 0;
    /** 每值当前档位：0=正常 1=偏低 2=危急。顺序：饥饿/口渴/理智/健康/污染。 */
    private static final int[] zones = new int[5];

    private SixtySecondsStateAlerts() {
    }

    /** 本地玩家的 60s 状态组件；不在 60s 局内（或已淘汰/旁观）返回 null。 */
    public static SixtySecondsStatsComponent localStats() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || SixtySecondsMod.MODE == null
                || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE
                || !SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
            return null;
        }
        return SixtySecondsStatsComponent.KEY.get(mc.player);
    }

    /**
     * 60s 理智映射为低 san 滤镜的驱动值（0..1，等价于情绪 mood）；非 60s 局内恒 1（滤镜不生效）。
     * 供 {@code SansRenderer} 取 min(情绪, 本值)——60s 里玩家无 REAL 情绪职业、mood 恒为 1，
     * 因此滤镜强度实际完全由 60s 理智驱动。
     */
    public static float sanityMoodScale() {
        SixtySecondsStatsComponent stats = localStats();
        return stats == null ? 1f : Mth.clamp(stats.sanity / (float) MAX, 0f, 1f);
    }

    /** 每 tick 由 {@link SixtySecondsHud} 调用（HUD 已保证在 60s 局内）。 */
    public static void tick(FakeGuiGraphics graphics, Minecraft client, LocalPlayer player,
            SixtySecondsStatsComponent stats) {
        if (!SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
            reset();
            return;
        }
        boolean warm = warmup >= WARMUP_TICKS;
        if (!warm) {
            warmup++;
        }
        long gameTime = client.level.getGameTime();

        // 受伤红闪：健康单次掉幅达标 + 冷却结束才触发
        if (warm && lastHealth >= 0 && stats.health < lastHealth) {
            int drop = lastHealth - stats.health;
            if (drop >= FLASH_MIN_DROP && gameTime - lastFlashGameTime >= FLASH_COOLDOWN_TICKS) {
                lastFlashGameTime = gameTime;
                flashTicksLeft = FLASH_DURATION_TICKS;
                flashIntensity = Mth.clamp(0.25f + drop / 50f * 0.35f, 0.25f, 0.6f);
            }
        }
        lastHealth = stats.health;

        // 文本提示：只在恶化跨档时发一次（warm 前静默采档，避免重连补包触发）
        updateZone(player, 0, stats.hunger, "low_hunger", "crit_hunger", warm);
        updateZone(player, 1, stats.thirst, "low_thirst", "crit_thirst", warm);
        updateZone(player, 2, stats.sanity, "low_sanity", "crit_sanity", warm);
        updateZone(player, 3, stats.health, "low_health", "crit_health", warm);
        updateZone(player, 4, MAX - stats.pollution, "high_pollution", "crit_pollution", warm);

        // 一次性受伤红闪（线性衰减）
        if (flashTicksLeft > 0) {
            renderEdges(graphics, 0xFF2020, flashIntensity * flashTicksLeft / FLASH_DURATION_TICKS);
            flashTicksLeft--;
        }
        // 常驻低健康边缘脉冲（倒地有专属覆盖层，不叠加）
        if (!stats.downed && stats.health > 0 && stats.health <= LOW) {
            float pulse = 0.55f + 0.45f * Mth.sin(player.tickCount * 0.25f);
            renderEdges(graphics, 0xC01818, (0.10f + 0.25f * (LOW - stats.health) / LOW) * pulse);
        }
    }

    /** 离开 60s 局（或淘汰）时清空跟踪状态；幂等、可每 tick 调。 */
    public static void reset() {
        lastHealth = -1;
        flashTicksLeft = 0;
        flashIntensity = 0f;
        lastFlashGameTime = Long.MIN_VALUE / 2;
        warmup = 0;
        Arrays.fill(zones, 0);
    }

    private static void updateZone(LocalPlayer player, int idx, int value, String lowKey, String critKey,
            boolean warm) {
        int zone = zones[idx];
        // 恶化立即进档；好转需回升超过阈值+HYST 才退档（防阈值附近抖动反复触发）
        int newZone;
        if (value <= CRIT) {
            newZone = 2;
        } else if (value <= LOW) {
            newZone = zone == 2 && value <= CRIT + HYST ? 2 : 1;
        } else {
            newZone = zone >= 1 && value <= LOW + HYST ? Math.min(zone, 1) : 0;
        }
        if (newZone > zone && warm) {
            boolean crit = newZone == 2;
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds." + (crit ? critKey : lowKey)), false);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_PLING.value(), crit ? 0.6F : 1.2F));
        }
        zones[idx] = newZone;
    }

    /** 屏幕边缘渐变光（画法参照 {@code RedScreenRenderer}：上下 fillGradient + 左右逐列衰减）。 */
    private static void renderEdges(FakeGuiGraphics g, int rgb, float intensity) {
        if (intensity <= 0.01f) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int solid = (int) (Mth.clamp(intensity, 0f, 1f) * 255) << 24 | rgb;
        int transparent = rgb; // alpha=0
        int edgeH = Math.max(1, (int) (h * 0.15f));
        int edgeW = Math.max(1, (int) (w * 0.12f));
        g.fillGradient(0, 0, w, edgeH, solid, transparent);
        g.fillGradient(0, h - edgeH, w, h, transparent, solid);
        // 左右侧无横向渐变原语，逐列（步长 2）画衰减
        for (int i = 0; i < edgeW; i += 2) {
            int col = (int) ((1f - i / (float) edgeW) * intensity * 255) << 24 | rgb;
            int x2 = Math.min(i + 2, edgeW);
            g.fill(i, 0, x2, h, col);
            g.fill(w - x2, 0, w - i, h, col);
        }
    }
}
