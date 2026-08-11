/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.neutral.leader;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayerIdentifier;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.LeaderRoleData;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 领袖（Leader）事件处理。
 *
 * <p>负责：技能释放（G 键 + 准星目标）、追随者联动事件（阿蒙代死、小偷/赌徒/
 * 初学者死亡联动、记录员免疫、雇佣兵解锁、领袖击杀给小偷金币）、持续效果维护。</p>
 */
public final class LeaderEventHandler {

    private LeaderEventHandler() {
    }

    /** 渡鸦狩猎期一次性手枪发放记录 */
    private static final Set<UUID> RAVEN_HUNT_GUN_GIVEN = new HashSet<>();

    /** 领袖「只能伤害指定目标」（宿命的罪人）标记 */
    private static final java.util.Map<UUID, UUID> ONLY_TARGET_KILL = new java.util.HashMap<>();

    /** 宿命的罪人：排队立刻随机死因死亡（下一 tick 执行） */
    private static final Set<UUID> PENDING_DOOMED_KILL = new HashSet<>();

    /** 熊猫形态领袖（黑白追随者效果）金币光环计时 */
    private static final Set<UUID> PANDA_LEADERS = new HashSet<>();
    private static final java.util.Map<UUID, Integer> PANDA_COIN_TIMERS = new java.util.HashMap<>();
    private static final int PANDA_AURA_RANGE = 6;
    private static final int PANDA_COIN_INTERVAL = 5 * 20;
    private static final int PANDA_COIN_AMOUNT = 8;

    /** 需要守护：记录错误死因对追随者记录员免疫 */

    public static void register() {
        registerSkill();
        registerDeathEvents();
        registerFollowerDeathEvents();
        registerKillEvents();
        registerRoleChangeEvents();
        registerTickEvents();
    }

    // ==================== 技能注册 ====================

    private static void registerSkill() {
        RoleSkill.register(ModRoles.LEADER,
                RoleSkill.skill(ModRoles.LEADER_ID.withPath("leader_recruit"),
                        "skill.noellesroles.leader.recruit",
                        context -> {
                            ServerPlayer leader = context.player();
                            UUID targetUid = context.target();
                            if (targetUid == null) {
                                return false;
                            }
                            ServerPlayer target = leader.serverLevel().getServer().getPlayerList().getPlayer(targetUid);
                            return LeaderFollowerEffects.tryRecruit(leader, target);
                        })
                        .withTarget(true).charges(1).showOnHud(true).announceToSelf(false).build());
    }

    // ==================== 死亡事件 ====================

    private static void registerDeathEvents() {
        // 阿蒙代死：优先消耗宿主（AmonEventHandler 先注册），无宿主时领袖代死
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer amon)) {
                return true;
            }
            return handleAmonSacrifice(amon, deathReason);
        });
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            if (!(player instanceof ServerPlayer amon)) {
                return true;
            }
            return handleAmonSacrifice(amon, deathReason);
        });

        // 追随者初学者：免疫「考核失败」死亡（考核失败的代价由领袖承受）
        AfterShieldAllowPlayerDeath.EVENT.register((player, deathReason) -> {
            if (!(player instanceof ServerPlayer sp)) {
                return true;
            }
            if (GameConstants.DeathReasons.FAILED_INITIATION.equals(deathReason)
                    && LeaderFollowerEffects.isFollowerOfLeader(sp)) {
                return false;
            }
            return true;
        });

        // 教父家族互不可伤：领袖与教父/家族成员互相无法造成伤害
        io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer sv) || !(killer instanceof ServerPlayer sk)) {
                return true;
            }
            if (deathReason != null && deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
                return true;
            }
            boolean killerIsLeader = SREGameWorldComponent.KEY.get(sk.level()).isRole(sk, ModRoles.LEADER);
            boolean victimIsLeader = SREGameWorldComponent.KEY.get(sv.level()).isRole(sv, ModRoles.LEADER);
            if (killerIsLeader && isGodfatherFamily(sv)) {
                return false;
            }
            if (victimIsLeader && isGodfatherFamily(sk)) {
                return false;
            }
            // 宿命的罪人：领袖只能伤害目标罪人
            if (killerIsLeader && !canOnlyKillTarget(sk.getUUID(), sv.getUUID())) {
                return false;
            }
            return true;
        });
    }

    /**
     * 阿蒙代死：仅当阿蒙为追随者且无宿主可消耗时，领袖代替阿蒙死亡。
     * <p>注意：AmonEventHandler 先于本监听器注册（NREventRegister 中）。
     * AfterShieldAllowPlayerDeath 为短路事件——若 AmonEventHandler 夺舍成功
     * 会返回 {@code false} 并短路，本监听器根本不会执行；能执行到这里即代表
     * 阿蒙已无宿主可消耗（将真正死亡），此时由领袖代死。</p>
     */
    private static boolean handleAmonSacrifice(ServerPlayer amon, ResourceLocation deathReason) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(amon.level());
        if (!game.isRunning() || !game.isRole(amon, ModRoles.AMON)) {
            return true;
        }
        if (!LeaderFollowerEffects.isFollowerOfLeader(amon)) {
            return true;
        }
        ServerPlayer leader = LeaderFollowerEffects.getLeaderOf(amon);
        if (leader == null || !GameUtils.isPlayerAliveAndSurvival(leader)) {
            return true;
        }
        // 无宿主可消耗：领袖代替阿蒙死亡，取消阿蒙死亡
        GameUtils.killPlayer(leader, true, null, deathReason);
        return false;
    }

    // ==================== 死亡事件：追随者死亡联动 ====================

    private static void registerFollowerDeathEvents() {
        // 小偷死亡 → 领袖 GUN_SHOT 死；阿蒙死亡 → 领袖代死（在 registerDeathEvents 处理）
        io.wifi.starrailexpress.event.OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer sv)) {
                return;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(sv.level());
            if (!game.isRunning()) {
                return;
            }
            if (game.isRole(sv, ModRoles.THIEF) && LeaderFollowerEffects.isFollowerOfLeader(sv)) {
                ServerPlayer leader = LeaderFollowerEffects.getLeaderOf(sv);
                if (leader != null && GameUtils.isPlayerAliveAndSurvival(leader)) {
                    GameUtils.killPlayer(leader, true, null, GameConstants.DeathReasons.GUN_SHOT);
                }
            }
        });
    }

    // ==================== 击杀事件 ====================

    private static void registerKillEvents() {
        OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, deathReason) -> {
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(victim.level());
            // 领袖每杀 1 人，给追随者小偷 100 金币
            if (game.isRole(killer, ModRoles.LEADER)) {
                LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, killer);
                if (data != null) {
                    for (UUID followerId : data.followers) {
                        ServerPlayer follower = killer.serverLevel().getServer().getPlayerList().getPlayer(followerId);
                        if (follower != null && game.isRole(follower, ModRoles.THIEF)) {
                            SREPlayerShopComponent.KEY.get(follower).addToBalance(100);
                            follower.displayClientMessage(Component.translatable(
                                    "message.noellesroles.leader.thief_coin", killer.getName(), 100), true);
                        }
                    }
                }
            }

            // 雇佣兵解锁「帮助任意一方」：击杀使胜利计数增加（场上除雇佣兵与领袖外 ≤4 人时）
            if (game.isRole(killer, ModRoles.MERCENARY)
                    && LeaderFollowerEffects.isMercenaryHelpUnlocked(killer)
                    && LeaderFollowerEffects.isFollowerOfLeader(killer)) {
                long others = game.getPlayerCount() - 2; // 除雇佣兵与领袖外
                if (others <= 4) {
                    MercenaryPlayerComponent merc = MercenaryPlayerComponent.KEY.get(killer);
                    if (merc != null) {
                        merc.onContractTargetKilled();
                    }
                }
            }
        });
    }

    // ==================== 转职事件 ====================

    private static void registerRoleChangeEvents() {
        // 赌徒转职（不再是赌徒）→ 领袖 GUN_SHOT 死；初学者转型 → 领袖 failed_initiation 死
        ModdedRoleRemoved.EVENT.register((player, role) -> {
            if (!(player instanceof ServerPlayer sp)) {
                return;
            }
            if (!LeaderFollowerEffects.isFollowerOfLeader(sp)) {
                return;
            }
            ServerPlayer leader = LeaderFollowerEffects.getLeaderOf(sp);
            if (leader == null) {
                return;
            }
            String removedPath = role != null ? role.identifier().getPath() : "";
            // 赌徒不再是赌徒：领袖因左轮手枪而死
            if (removedPath.equals("gambler") || removedPath.equals("thief")) {
                GameUtils.killPlayer(leader, true, null, GameConstants.DeathReasons.GUN_SHOT);
            }
            // 初学者转型：领袖因考核失败而死
            if (removedPath.equals("initiate")) {
                GameUtils.killPlayer(leader, true, null, GameConstants.DeathReasons.FAILED_INITIATION);
            }
        });
    }

    // ==================== 持续效果 ====================

    private static void registerTickEvents() {
        ServerTickEvents.END_SERVER_TICK.register(LeaderEventHandler::serverTick);
    }

    private static void serverTick(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer any = players.getFirst();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(any.level());
        if (!game.isRunning()) {
            return;
        }

        // 宿命的罪人：排队随机死因立即死亡
        for (UUID fid : new java.util.ArrayList<>(PENDING_DOOMED_KILL)) {
            PENDING_DOOMED_KILL.remove(fid);
            ServerPlayer follower = any.serverLevel().getServer().getPlayerList().getPlayer(fid);
            if (follower != null && GameUtils.isPlayerAliveAndSurvival(follower)) {
                GameUtils.killPlayer(follower, true, null, randomDeathReason(follower));
            }
        }

        for (ServerPlayer leader : players) {
            LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, leader);
            if (data == null) {
                continue;
            }

            // 渡鸦狩猎期额外一次性手枪
            for (UUID fid : data.followers) {
                ServerPlayer follower = any.serverLevel().getServer().getPlayerList().getPlayer(fid);
                if (follower == null || !game.isRole(follower, ModRoles.RAVEN)) {
                    continue;
                }
                RavenPlayerComponent raven = RavenPlayerComponent.KEY.get(follower);
                if (raven.isHunting() && RAVEN_HUNT_GUN_GIVEN.add(fid)) {
                    RoleUtils.insertStackInFreeSlot(follower, ModItems.ONCE_REVOLVER.getDefaultInstance());
                    follower.displayClientMessage(Component.translatable(
                            "message.noellesroles.leader.raven_hunt_gun"), true);
                }
            }

            // 熊猫领袖（黑白追随者效果）：金币光环
            if (PANDA_LEADERS.contains(leader.getUUID()) && GameUtils.isPlayerAliveAndSurvival(leader)) {
                int timer = PANDA_COIN_TIMERS.merge(leader.getUUID(), 1, Integer::sum);
                if (timer >= PANDA_COIN_INTERVAL) {
                    PANDA_COIN_TIMERS.put(leader.getUUID(), 0);
                    for (ServerPlayer target : players) {
                        if (target == leader || !GameUtils.isPlayerAliveAndSurvival(target)) {
                            continue;
                        }
                        if (leader.distanceTo(target) <= PANDA_AURA_RANGE) {
                            SREPlayerShopComponent.KEY.get(target).addToBalance(PANDA_COIN_AMOUNT);
                        }
                    }
                }
            }
        }

        // 鹈鹕：游戏时间 < 2 分钟时全服发光至结束（追随者鹈鹕专属效果）
        if (LeaderFollowerEffects.hasPelicanFollower(any)) {
            var time = io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(any.level());
            if (time.time < 2 * 60 * 20) {
                for (ServerPlayer p : players) {
                    if (GameUtils.isPlayerAliveAndSurvival(p)
                            && !p.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, true, false, false));
                    }
                }
            }
        }
    }

    private static ResourceLocation randomDeathReason(ServerPlayer player) {
        var reasons = java.util.List.of(
                GameConstants.DeathReasons.GUN_SHOT,
                GameConstants.DeathReasons.KNIFE,
                GameConstants.DeathReasons.POISON,
                GameConstants.DeathReasons.GRENADE);
        return reasons.get(player.getRandom().nextInt(reasons.size()));
    }

    // ==================== 工具方法（供 LeaderFollowerEffects 调用） ====================

    /** 玩家是否为教父或教父家族成员 */
    private static boolean isGodfatherFamily(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game.isRole(player, ModRoles.GODFATHER)) {
            return true;
        }
        for (ServerPlayer p : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            if (!game.isRole(p, ModRoles.GODFATHER)) {
                continue;
            }
            org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent comp
                    = org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent.KEY.get(p);
            if (comp != null && comp.familyMembers.contains(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** 标记「只能伤害指定目标」（宿命的罪人） */
    public static void markOnlyTargetKill(UUID killerUuid, UUID targetUuid) {
        ONLY_TARGET_KILL.put(killerUuid, targetUuid);
    }

    /** 宿命的罪人：领袖是否只能伤害该目标 */
    public static boolean canOnlyKillTarget(UUID killerUuid, UUID targetUuid) {
        UUID t = ONLY_TARGET_KILL.get(killerUuid);
        return t == null || t.equals(targetUuid);
    }

    /** 排队宿命的罪人立即随机死因死亡 */
    public static void scheduleDoomedSinnerKill(ServerPlayer doomedSinner) {
        PENDING_DOOMED_KILL.add(doomedSinner.getUUID());
    }

    /** 标记渡鸦狩猎期发枪（确保未发过） */
    public static void markRavenHuntGun(UUID ravenUuid) {
        // 释放时标记，狩猎开始后发放
        RAVEN_HUNT_GUN_GIVEN.remove(ravenUuid);
    }

    /** 标记熊猫形态领袖（黑白追随者效果） */
    public static void markPandaLeader(UUID leaderUuid) {
        PANDA_LEADERS.add(leaderUuid);
        PANDA_COIN_TIMERS.put(leaderUuid, 0);
    }
}
