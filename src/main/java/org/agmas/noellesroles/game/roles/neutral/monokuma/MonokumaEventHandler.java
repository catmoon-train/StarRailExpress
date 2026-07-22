package org.agmas.noellesroles.game.roles.neutral.monokuma;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowGameEnd;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
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
        // // 黑白熊阶段(3)
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

        // 黑白玩家在狂暴前奏中掉线时，平衡疯狂 BGM 与全服狂暴效果，避免音乐永久残留。
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer sp = handler.getPlayer();
            if (sp == null)
                return;
            MonokumaPlayerComponent comp = MonokumaPlayerComponent.KEY.maybeGet(sp).orElse(null);
            if (comp != null && comp.phase == 2) {
                comp.clear();
            }
        });

        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (deathReason.equals(GameConstants.DeathReasons.DEATH_AFK))
                return true;
            if (deathReason.equals(GameConstants.DeathReasons.DISCONNECT))
                return true;
            if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN))
                return true;
            if (deathReason.equals(GameConstants.DeathReasons.IGNITED))
                return true;
            if (!(player instanceof ServerPlayer sp))
                return true;
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(sp.level());
            if (!worldModifierComponent.isModifier(sp, SEModifiers.BLACK_WHITE))
                return true;
            MonokumaPlayerComponent comp = MonokumaPlayerComponent.KEY.get(sp);
            if (!RefugeeComponent.KEY.get(sp.level()).isAnyRevivals && comp.phase == 1) {
                // 注意：直接在死亡事件回调里同步执行换职业 / 启动疯狂，会让其中任何异常顺着
                // “攻击者攻击封包”的调用栈抛出，导致触发黑白的玩家（如义警）掉线。
                // 这里只同步取消死亡，把繁重的狂暴触发推迟到干净的服务端任务栈上执行并捕获异常。
                if (sp.getServer() != null) {
                    sp.getServer().execute(() -> {
                        try {
                            MonokumaPlayerComponent c = MonokumaPlayerComponent.KEY.get(sp);
                            if (c.phase != 1)
                                return;
                            RoleUtils.dropAndClearAllSatisfiedItems(sp, TMMItemTags.GUNS);
                            StupidRoleUtils.changeRole(sp, ModRoles.MONOKUMA);
                            StupidRoleUtils.sendWelcomeAnnouncement(sp);
                            c.onHitTriggered();
                        } catch (Exception e) {
                            org.agmas.noellesroles.Noellesroles.LOGGER.error("黑白狂暴触发失败", e);
                        }
                    });
                }
                return false;
            } else if (comp.phase == 3) {
                var gameCCA = SREGameWorldComponent.KEY.get(player.level());
                if (!gameCCA.isRole(player, ModRoles.MONOKUMA))
                    return true;
                boolean flag = false;
                if (!flag) {
                    if (killer != null) {
                        if (gameCCA.isRole(player, ModRoles.MONOKUMA)) {
                            if (gameCCA.isRole(killer, TMMRoles.LOOSE_END)) {
                                return true;
                            }
                        }
                    }
                }
                if (!flag) {
                    boolean hasGood = false, hasBad = false;
                    final var players = player.level().players();
                    for (var p : players) {
                        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p))
                            continue;
                        if (p.getUUID().equals(player.getUUID()))
                            continue;
                        SRERole role = gameCCA.getRole(p);
                        if (role != null) {
                            if (role.isCanUseKiller() && !role.isInnocent() && !role.isNeutrals()) {
                                hasBad = true;
                            }
                            if (role.isInnocent()) {
                                hasGood = true;
                            }
                            if (hasBad && hasGood)
                                break;
                        }
                    }
                    if (!hasBad || !hasGood) {
                        flag = true;
                    }
                }
                return flag;
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
