package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 生存四值 + 健康值驱动：
 * <ul>
 *   <li>每分钟消耗 饥饿/口渴/san、累积污染（在家 ×{@link SixtySecondsBalance#HOME_DRAIN_MULT}）；</li>
 *   <li><b>健康保护</b>：饥饿或口渴清空 → 每秒最多扣 {@link SixtySecondsBalance#HEALTH_LOSS_PER_SEC}（单一来源、封顶、不叠加），
 *       归零走非受伤直接死亡；</li>
 *   <li>污染满 → 缓慢 + 概率生病（<b>不</b>扣健康，避免掉血过快）。</li>
 * </ul>
 * san 归零变怪物仍为 TODO。数值集中在 {@link SixtySecondsBalance}。
 */
public final class SixtySecondsStatsSystem {
    private static final int MAX = SixtySecondsStatsComponent.MAX;
    private static final int MINUTE = 20 * 60;

    private SixtySecondsStatsSystem() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        boolean minuteTick = now % MINUTE == 0;
        SixtySecondsState.Data data = SixtySecondsState.get(level);

        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed) {
                continue;
            }
            boolean changed = false;

            // 每分钟消耗（在家减半）
            if (minuteTick) {
                double mult = isInHome(player, data, stats.teamId) ? SixtySecondsBalance.HOME_DRAIN_MULT : 1.0;
                stats.hunger = clampDown(stats.hunger, scale(SixtySecondsBalance.HUNGER_DRAIN_PER_MIN, mult));
                stats.thirst = clampDown(stats.thirst, scale(SixtySecondsBalance.THIRST_DRAIN_PER_MIN, mult));
                stats.sanity = clampDown(stats.sanity, scale(SixtySecondsBalance.SANITY_DRAIN_PER_MIN, mult));
                stats.pollution = clampUp(stats.pollution, scale(SixtySecondsBalance.POLLUTION_GAIN_PER_MIN, mult));
                changed = true;
            }

            // 健康保护：饥渴清空时每秒扣血（单一来源、封顶）
            if ((stats.hunger <= 0 || stats.thirst <= 0) && stats.health > 0) {
                stats.health = Math.max(0, stats.health - SixtySecondsBalance.HEALTH_LOSS_PER_SEC);
                changed = true;
                if (stats.health <= 0) {
                    SixtySecondsHealthSystem.onHealthZero(player, false, null);
                    continue;
                }
            }

            // 污染满：缓慢 + 概率生病（不扣健康）
            if (stats.pollution >= MAX) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, false));
                if (now % SixtySecondsBalance.POLLUTION_SICK_ROLL_INTERVAL == 0
                        && level.getRandom().nextDouble() < SixtySecondsBalance.POLLUTION_SICK_CHANCE) {
                    SixtySecondsSicknessSystem.makeSick(player);
                }
            }

            // TODO(P2): san 归零一段时间后变怪物 / 自我解脱换队胜。

            if (changed) {
                stats.sync();
            }
        }
    }

    private static boolean isInHome(ServerPlayer player, SixtySecondsState.Data data, int teamId) {
        SixtySecondsState.TeamData team = data.teams.get(teamId);
        if (team == null) {
            return false;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        return (team.residentialBox != null && team.residentialBox.contains(x, y, z))
                || (team.shelterBox != null && team.shelterBox.contains(x, y, z));
    }

    private static int scale(int base, double mult) {
        return Math.max(0, (int) Math.round(base * mult));
    }

    private static int clampDown(int value, int amount) {
        return Math.max(0, value - amount);
    }

    private static int clampUp(int value, int amount) {
        return Math.min(MAX, value + amount);
    }
}
