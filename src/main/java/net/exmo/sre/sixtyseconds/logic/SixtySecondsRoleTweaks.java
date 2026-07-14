package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.world.entity.player.Player;

/**
 * 末日60秒模式对<b>觉醒职业</b>的数值修正集中入口（详见 {@link SixtySecondsRoleAwakening}）。
 * <p>
 * 无统一冷却/消耗倍率字段，各职业的冷却/消耗散在各自硬编码点。这里提供<b>单行委托</b>供那些点调用：
 * 都以 {@code SixtySecondsMod.isActive(level)} 为门控——60s 模式下这些职业必为觉醒所得，故直接按模式判定即可，
 * 无需 per-player 状态。所有数值集中在 {@link SixtySecondsBalance}，io.wifi/noellesroles 端只留一句调用。
 */
public final class SixtySecondsRoleTweaks {
    private SixtySecondsRoleTweaks() {
    }

    private static boolean active(Player player) {
        return player != null && SixtySecondsMod.isActive(player.level());
    }

    // ── 玉将军 / 斗士：技能冷却×（调用点已在各自职业分支内，无需再判角色）──────────────
    /** 玉将军技能冷却缩放：60s 模式 ×{@link SixtySecondsBalance#JADE_GENERAL_COOLDOWN_MULT}，否则原值。 */
    public static int jadeGeneralCooldown(Player player, int baseTicks) {
        return active(player) ? (int) Math.round(baseTicks * SixtySecondsBalance.JADE_GENERAL_COOLDOWN_MULT) : baseTicks;
    }

    /** 斗士技能冷却缩放：60s 模式 ×{@link SixtySecondsBalance#FIGHTER_COOLDOWN_MULT}，否则原值。 */
    public static int fighterCooldown(Player player, int baseTicks) {
        return active(player) ? (int) Math.round(baseTicks * SixtySecondsBalance.FIGHTER_COOLDOWN_MULT) : baseTicks;
    }

    // ── 广播员：每次广播消耗提高 ─────────────────────────────────────────────
    /** 广播员每次广播消耗的金币：60s 模式 {@link SixtySecondsBalance#BROADCASTER_BROADCAST_COST}，否则原版 50。 */
    public static int broadcastCost(Player player) {
        return active(player) ? SixtySecondsBalance.BROADCASTER_BROADCAST_COST : 50;
    }

    // ── 明星：技能不再奖励金币 ───────────────────────────────────────────────
    /** 60s 模式下明星技能不发放金币奖励（末日经济收紧）。 */
    public static boolean starSkillCoinRewardDisabled(Player player) {
        return active(player);
    }

    // ── 药剂师：禁调鹤顶红 ──────────────────────────────────────────────────
    /** 60s 模式下药剂师禁止调制鹤顶红（避免毒杀队友破坏合作生存）。 */
    public static boolean poisonCraftBanned(Player player) {
        return active(player);
    }

    // ── 小透明：技能隐身直接解锁 ─────────────────────────────────────────────
    /**
     * 60s 模式下小透明的隐身<b>技能</b>无需等原版「剩 3 分钟」条件、觉醒即解锁
     * （原版解锁看 {@code SREGameTimeComponent} 剩余时间，60s 全程 7 天基本不会触达）。
     * 隐身仍走技能本身（150 金币 / 8s / 冷却 20s），<b>不再</b>由本类每 tick 强加常驻隐身。
     */
    public static boolean ghostSkillAlwaysUnlocked(Player player) {
        return active(player);
    }
}
