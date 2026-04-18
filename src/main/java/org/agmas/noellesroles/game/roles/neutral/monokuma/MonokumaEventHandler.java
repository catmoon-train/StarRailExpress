package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.utils.StupidRoleUtils;

/**
 * 黑白角色事件注册
 *
 * 处理：
 * - 被击中时触发狂暴前奏
 * - 特制左轮50%概率命中
 * - 黑白熊形态免疫一切伤害
 * - 好人误杀好人机制
 * - 获胜条件判定
 */
public class MonokumaEventHandler {

    public static void register() {
        registerHitTrigger();
        registerMonokumaProtection();
        registerWinCondition();
        registerPickupRestriction();
    }

    /**
     * 被攻击时触发狂暴前奏
     */
    private static void registerHitTrigger() {
        // 使用 AllowPlayerDeathWithKiller 来截获被攻击事件
        // 在伪装义警阶段(phase 1)，任何攻击命中都触发狂暴前奏
        // AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
        // if (!(player instanceof ServerPlayer sp)) return true;
        // WorldModifierComponent worldModifierComponent =
        // WorldModifierComponent.KEY.get(sp.level());
        // if (!worldModifierComponent.isModifier(sp, SEModifiers.BLACK_WHITE)) return
        // true;
        //
        // MonokumaPlayerComponent comp = MonokumaPlayerComponent.KEY.get(sp);
        // if (comp.phase == 1) {
        // // 触发狂暴前奏，阻止死亡
        // comp.onHitTriggered();
        // return false;
        // }
        // // 狂暴阶段(2)正常接受伤害判定（有护盾）
        // // 黑白熊阶段(3)由 INVINCIBLE 效果处理
        // return true;
        // });
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(killer instanceof ServerPlayer sp))
                return true;
            if (killer.getMainHandItem().is(TMMItems.REVOLVER)) {
                WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(sp.level());
                if (worldModifierComponent.isModifier(killer, SEModifiers.BLACK_WHITE)) {
                    return !(Math.random() > 0.5);
                }

            }
            return true;
        });

        AllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN))
                return true;
            if (deathReason.equals(StupidExpress.id("ignited")))
                return true;
            if (!(player instanceof ServerPlayer sp))
                return true;
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(sp.level());
            if (!worldModifierComponent.isModifier(sp, SEModifiers.BLACK_WHITE))
                return true;
            MonokumaPlayerComponent comp = MonokumaPlayerComponent.KEY.get(sp);
            if (comp.phase == 1) {
                StupidRoleUtils.changeRole(player, ModRoles.MONOKUMA);
                StupidRoleUtils.sendWelcomeAnnouncement(sp);

                comp.onHitTriggered();
                return false;
            }
            if (comp.phase == 3) {
                return false;
            }
            return true;
        });
    }

    // /**
    // * 特制左轮50%命中和好人误杀机制
    // */
    // private static void registerRevolverMechanic() {
    // // 拦截枪击事件：检查是否为特制左轮
    // OnRevolverUsed.EVENT.register((shooter, target) -> {
    // if (target == null) return;
    // if (!(shooter instanceof ServerPlayer sp)) return;
    // SREGameWorldComponent gameComponent =
    // SREGameWorldComponent.KEY.get(sp.level());
    // if (!gameComponent.isRole(sp, ModRoles.MONOKUMA)) return;
    //
    // MonokumaPlayerComponent comp = MonokumaPlayerComponent.KEY.get(sp);
    // if (comp.phase != 1) return;
    //
    // // 50%概率不击杀 → 在 onGunHit 回调中处理
    // });
    // }

    /**
     * 黑白熊形态保护：
     * - 无法被击杀（INVINCIBLE效果已处理）
     * - 无法被控制（手铐、风弹）
     */
    private static void registerMonokumaProtection() {
        // 黑白熊无法被验尸官查验
        // 这通过在杀手直觉/角色查看中返回特殊标记处理
    }

    /**
     * 获胜条件判定
     */
    private static void registerWinCondition() {
        AllowGameEnd.EVENT.register((serverLevel, winStatus, isLooseEnd) -> {
            if (isLooseEnd)
                return GameUtils.WinStatus.NOT_MODIFY;

            // 不阻止游戏结束，只是在结束时计算黑白熊的胜负
            // 黑白熊的获胜/失败在结果界面中体现
            // 返回 NOT_MODIFY 不修改默认胜负逻辑
            return GameUtils.WinStatus.NOT_MODIFY;
        });
    }

    /**
     * 物品拾取限制：黑白熊形态无法捡起左轮
     */
    private static void registerPickupRestriction() {
        // 通过 Role.cantPickupItem 处理
    }

    /**
     * 检查某个玩家是否是黑白熊形态（供其他系统查询）
     */
    public static boolean isMonokumaBearForm(Player player) {
        var comp = MonokumaPlayerComponent.KEY.maybeGet(player).orElse(null);
        return comp != null && comp.phase == 3;
    }

    /**
     * 检查某个玩家是否在狂暴前奏阶段
     */
    public static boolean isInFrenzy(Player player) {
        var comp = MonokumaPlayerComponent.KEY.maybeGet(player).orElse(null);
        return comp != null && comp.phase == 2;
    }
}
