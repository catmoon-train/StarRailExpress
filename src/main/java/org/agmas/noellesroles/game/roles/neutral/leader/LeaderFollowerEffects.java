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

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.reasoner.ReasonerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.ModMeetingRoles;
import org.agmas.noellesroles.role.touhou.MountainRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role_data.leader.LeaderRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 领袖（Leader）追随者效果统一处理文件。
 *
 * <p>所有「追随者效果」集中在此处按目标职业分发，逻辑不分散到各职业文件中。
 * 追随者效果在成为追随者时触发一次；持续类效果（如永久速度、永久夜视）在释放时
 * 直接施加无限时长效果，由 {@link #serverTick(ServerPlayer)} 兜底维持。</p>
 *
 * <p>特殊的持续联动（阿蒙代死、小偷/赌徒死亡时领袖陪葬、初学者联动、雇佣兵解锁、
 * 记录员免疫等）在 {@link LeaderEventHandler} 中通过事件处理。</p>
 */
public final class LeaderFollowerEffects {

    private LeaderFollowerEffects() {
    }

    /** 布谷鸟「下一颗蛋隐身」标记：玩家 UUID -> 待消费 */
    private static final Set<UUID> INVISIBLE_EGG_FLAGS = new HashSet<>();

    /** 雇佣兵「帮助任意一方结束游戏」解锁状态 */
    private static final Set<UUID> MERCENARY_HELP_UNLOCKED = new HashSet<>();

    /** 森近霖之助 / 河城荷取（金币依附角色） */
    public static boolean isCoinDependentRole(SRERole role) {
        return role != null && (role.identifier().equals(THMiscRoles.RINNOSUKE_ID)
                || role.identifier().equals(MountainRoles.NITORI_ID));
    }

    /** 该玩家是否已被领袖招募为追随者（通过其领袖的 RoleData 查询） */
    public static boolean isFollowerOfLeader(ServerPlayer player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning()) {
            return false;
        }
        for (ServerPlayer p : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, p);
            if (data != null && data.isFollower(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** 获取玩家所属的领袖（若无返回 null） */
    @Nullable
    public static ServerPlayer getLeaderOf(ServerPlayer player) {
        for (ServerPlayer p : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, p);
            if (data != null && data.isFollower(player.getUUID())) {
                return p;
            }
        }
        return null;
    }

    /** 布谷鸟：标记下一颗蛋隐身 */
    public static void markNextEggInvisible(UUID cuckooUuid) {
        INVISIBLE_EGG_FLAGS.add(cuckooUuid);
    }

    /** 布谷鸟：消费「下一颗蛋隐身」标记 */
    public static boolean consumeInvisibleEggFlag(UUID cuckooUuid) {
        return INVISIBLE_EGG_FLAGS.remove(cuckooUuid);
    }

    /** 雇佣兵是否已解锁「帮助任意一方结束游戏」 */
    public static boolean isMercenaryHelpUnlocked(ServerPlayer mercenary) {
        return MERCENARY_HELP_UNLOCKED.contains(mercenary.getUUID());
    }

    /**
     * 追随者雇佣兵解锁「帮助任意一方」后，场上（除雇佣兵与领袖外）≤4 人时可击杀除领袖外的任何人。
     */
    public static boolean mercenaryCanKillAnyone(ServerPlayer mercenary, net.minecraft.world.entity.player.Player target) {
        if (!MERCENARY_HELP_UNLOCKED.contains(mercenary.getUUID())
                || !isFollowerOfLeader(mercenary)) {
            return false;
        }
        ServerPlayer leader = getLeaderOf(mercenary);
        if (leader != null && target.getUUID().equals(leader.getUUID())) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(mercenary.level());
        return (game.getPlayerCount() - 2) <= 4;
    }

    /**
     * 释放技能：对目标职业施加追随者效果。
     *
     * @param leader   领袖
     * @param follower 目标（已成为追随者）
     */
    public static void applyEffect(ServerPlayer leader, ServerPlayer follower) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(leader.level());
        SRERole followerRole = game.getRole(follower);
        if (followerRole == null) {
            return;
        }
        String path = followerRole.identifier().getPath();

        // 禁止释放（不应走到这里，仅防御）
        if (path.equals("loose_end") || path.equals("super_loose_end") || path.equals("leader")) {
            return;
        }

        switch (path) {
            case "dummy_bird" -> applyDummyBird(leader, follower);
            case "amon" -> applyAmon(leader, follower);
            case "candlebearer" -> applyCandleBearer(leader, follower);
            case "cuckoo" -> applyCuckoo(leader, follower);
            case "gambler" -> applyGambler(leader, follower);
            case "raven" -> applyRaven(leader, follower);
            case "mercenary" -> applyMercenary(leader, follower);
            case "monokuma" -> applyMonokuma(leader, follower);
            case "wayfarer" -> applyWayfarer(leader, follower);
            case "recorder" -> applyRecorder(leader, follower);
            case "godfather" -> applyGodfather(leader, follower);
            case "nianshou" -> applyNianShou(leader, follower);
            case "doomed_sinner" -> applyDoomedSinner(leader, follower);
            case "reasoner" -> applyReasoner(leader, follower);
            case "thief" -> applyThief(leader, follower);
            case "pelican" -> applyPelican(leader, follower);
            case "initiate" -> applyInitiate(leader, follower);
            case "amnesiac" -> applyAmnesiac(leader, follower);
            case "arsonist" -> applyArsonist(leader, follower);
            case "morichika_rinnosuke", "kawashiro_nitori" -> applyCoinDependent(leader, follower);
            case "furandoru" -> applyFurandoru(leader, follower);
            default -> applyGeneric(leader, follower);
        }
    }

    // ==================== 具体效果 ====================

    /** 呆呆鸟：领袖投票权重 ×2.5、领袖得刀；追随者呆呆鸟 +1 层护盾 */
    private static void applyDummyBird(ServerPlayer leader, ServerPlayer follower) {
        if (followerRoleIs(follower, ModMeetingRoles.DUMMY_BIRD_ID)) {
            SREArmorPlayerComponent.KEY.get(follower).addArmor();
        }
        giveItem(leader, TMMItems.KNIFE.getDefaultInstance());
        // 投票权重 ×2.5（仅会议模式生效）
        net.exmo.sre.meeting.MeetingManager.setVoteWeight(leader,
                (int) Math.round(net.exmo.sre.meeting.MeetingManager.getVoteWeight(leader) * 2.5));
    }

    /** 阿蒙：无即时物品，代死逻辑在 LeaderEventHandler 处理 */
    private static void applyAmon(ServerPlayer leader, ServerPlayer follower) {
        // 无即时效果；死亡代替由 LeaderEventHandler 监听
    }

    /** 秉烛人：+1 隐身次数、永久速度1；领袖得刀 */
    private static void applyCandleBearer(ServerPlayer leader, ServerPlayer follower) {
        CandleBearerPlayerComponent comp = CandleBearerPlayerComponent.KEY.get(follower);
        comp.invisibilityCharges++;
        comp.sync();
        permanentEffect(follower, MobEffects.MOVEMENT_SPEED, 0);
        giveItem(leader, TMMItems.KNIFE.getDefaultInstance());
    }

    /** 布谷鸟：撬棍；下一颗蛋隐身 */
    private static void applyCuckoo(ServerPlayer leader, ServerPlayer follower) {
        giveItem(follower, TMMItems.CROWBAR.getDefaultInstance());
        markNextEggInvisible(follower.getUUID());
    }

    /** 赌徒：独立胜利 1%→2%、死亡必变杀手/警长（49/49/2）、领袖一次性手枪 */
    private static void applyGambler(ServerPlayer leader, ServerPlayer follower) {
        giveItem(leader, ModItems.ONCE_REVOLVER.getDefaultInstance());
        // 转职/死亡概率见 GamblerHandler（追随者赌徒 49/49/2）、转职联动见 LeaderEventHandler
    }

    /** 渡鸦：+1 充能、永久速度1；领袖假刀假枪 */
    private static void applyRaven(ServerPlayer leader, ServerPlayer follower) {
        RavenPlayerComponent comp = RavenPlayerComponent.KEY.get(follower);
        comp.charges = Math.min(RavenPlayerComponent.MAX_CHARGES, comp.charges + 1);
        comp.sync();
        permanentEffect(follower, MobEffects.MOVEMENT_SPEED, 0);
        giveItem(leader, ModItems.FAKE_KNIFE.getDefaultInstance());
        giveItem(leader, ModItems.FAKE_REVOLVER.getDefaultInstance());
        // 狩猎期额外一次性手枪：LeaderEventHandler 中在渡鸦进入狩猎时发放
        LeaderEventHandler.markRavenHuntGun(follower.getUUID());
    }

    /** 雇佣兵：领袖不可成为通缉目标+从契约消失；≤4人时解锁帮助任意一方；全服广播 */
    private static void applyMercenary(ServerPlayer leader, ServerPlayer follower) {
        MercenaryPlayerComponent comp = MercenaryPlayerComponent.KEY.get(follower);
        if (comp.contractTargetUuid != null && comp.contractTargetUuid.equals(leader.getUUID())) {
            comp.contractTargetUuid = null;
            comp.contractTargetName = "";
            comp.contractActive = false;
        }
        if (comp.forcedTargetUuid != null && comp.forcedTargetUuid.equals(leader.getUUID())) {
            comp.forcedTargetUuid = null;
            comp.forcedTargetName = "";
        }
        if (comp.employerUuid != null && comp.employerUuid.equals(leader.getUUID())) {
            comp.employerUuid = null;
            comp.employerName = "";
        }
        comp.sync();
        MERCENARY_HELP_UNLOCKED.add(follower.getUUID());
        // 全服广播
        Component message = Component.translatable("message.noellesroles.leader.mercenary_unlocked");
        BroadcastMessageS2CPacket packet = new BroadcastMessageS2CPacket(message);
        for (ServerPlayer p : leader.serverLevel().getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    /** 黑白：领袖变熊猫形态（含药水效果）；黑白获胜时领袖获胜（didPlayerWin） */
    private static void applyMonokuma(ServerPlayer leader, ServerPlayer follower) {
        PandaComponent panda = PandaComponent.KEY.get(leader);
        panda.isPanda = true;
        panda.sync();
        MonokumaPlayerComponent monokuma = MonokumaPlayerComponent.KEY.get(follower);
        monokuma.phase = 3;
        monokuma.sync();
        // 与黑白熊猫形态一致：永久无敌 + 隐身（隐藏气泡）
        permanentEffect(leader, ModEffects.INVINCIBLE, 0);
        permanentEffect(leader, MobEffects.INVISIBILITY, 0);
        // 清除领袖背包中所有武器/道具（与黑白一致）
        for (int i = 0; i < leader.getInventory().getContainerSize(); i++) {
            leader.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        // 熊猫领袖金币光环（见 LeaderEventHandler.serverTick）
        LeaderEventHandler.markPandaLeader(leader.getUUID());
    }

    /** 红尘客：追随者 +1 限时 20 秒护盾；领袖一次性手枪 */
    private static void applyWayfarer(ServerPlayer leader, ServerPlayer follower) {
        SREArmorPlayerComponent.KEY.get(follower).addTimedArmor(1, 20 * 20, true);
        giveItem(leader, ModItems.ONCE_REVOLVER.getDefaultInstance());
    }

    /** 记录员：免疫记录错误死 + 开锁器；领袖开锁器 */
    private static void applyRecorder(ServerPlayer leader, ServerPlayer follower) {
        giveItem(follower, TMMItems.LOCKPICK.getDefaultInstance());
        giveItem(leader, TMMItems.LOCKPICK.getDefaultInstance());
        // 免疫记录错误死见 LeaderEventHandler
    }

    /** 教父：追随者一次性手枪、领袖制式左轮；领袖家族色；互不可伤 */
    private static void applyGodfather(ServerPlayer leader, ServerPlayer follower) {
        giveItem(follower, ModItems.ONCE_REVOLVER.getDefaultInstance());
        giveItem(leader, TMMItems.STANDARD_REVOLVER.getDefaultInstance());
        // 加入家族成员（家族透视显示家族色 + 互不可伤）
        GodfatherComponent comp = GodfatherComponent.KEY.get(follower);
        comp.familyMembers.add(leader.getUUID());
        comp.sync();
    }

    /** 年兽：追随者 +1 护盾试剂、永久夜视 */
    private static void applyNianShou(ServerPlayer leader, ServerPlayer follower) {
        NianShouPlayerComponent comp = NianShouPlayerComponent.KEY.get(follower);
        comp.addRedPacket();
        permanentEffect(follower, MobEffects.NIGHT_VISION, 0);
    }

    /** 宿命的罪人：随机死因立即死；领袖刀+一次性手枪，只能伤害罪人 */
    private static void applyDoomedSinner(ServerPlayer leader, ServerPlayer follower) {
        giveItem(leader, TMMItems.KNIFE.getDefaultInstance());
        giveItem(leader, ModItems.ONCE_REVOLVER.getDefaultInstance());
        LeaderEventHandler.markOnlyTargetKill(leader.getUUID(), follower.getUUID());
        // 随机死因立即死亡（交由 LeaderEventHandler 下一 tick 执行，避免死亡事件递归）
        LeaderEventHandler.scheduleDoomedSinnerKill(follower);
    }

    /** 推理师：罗盘立即完成一条未答的随机问题 */
    private static void applyReasoner(ServerPlayer leader, ServerPlayer follower) {
        ReasonerPlayerComponent comp = ReasonerPlayerComponent.KEY.get(follower);
        comp.forceCompleteRandomQuestion();
    }

    /** 小偷：开锁器、领袖制式左轮；领袖每杀 1 人给小偷 100 金币；小偷死→领袖 GUN_SHOT 死 */
    private static void applyThief(ServerPlayer leader, ServerPlayer follower) {
        giveItem(follower, TMMItems.LOCKPICK.getDefaultInstance());
        giveItem(leader, TMMItems.STANDARD_REVOLVER.getDefaultInstance());
        // 击杀金币 / 小偷死亡联动见 LeaderEventHandler
    }

    /** 鹈鹕：技能 40% 不进入冷却；游戏 <2 分钟全服发光至结束 */
    private static void applyPelican(ServerPlayer leader, ServerPlayer follower) {
        // 40% 不冷却 + 全服发光见 LeaderEventHandler
        PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(follower);
        comp.sync();
    }

    /** 初学者：其它初学者也自动成为追随者（由技能释放逻辑处理）；不再考核失败死；转型→领袖死；领袖得刀 */
    private static void applyInitiate(ServerPlayer leader, ServerPlayer follower) {
        giveItem(leader, TMMItems.KNIFE.getDefaultInstance());
        // 其它联动逻辑见 LeaderEventHandler / 技能释放处（其它初学者加入追随者）
    }

    /** 失忆患者：随机变成一名杀手（berandomedbyotherroles 职业除外）；领袖得刀；领袖随杀手获胜 */
    private static void applyAmnesiac(ServerPlayer leader, ServerPlayer follower) {
        ArrayList<SRERole> killerRoles = new ArrayList<>(Noellesroles.getEnableKillerRoles());
        if (killerRoles.isEmpty()) {
            killerRoles.add(TMMRoles.KILLER);
        }
        Collections.shuffle(killerRoles);
        RoleUtils.changeRole(follower, killerRoles.getFirst());
        RoleUtils.sendWelcomeAnnouncement(follower);
        giveItem(leader, TMMItems.KNIFE.getDefaultInstance());
    }

    /** 纵火犯：打火机目标 -2 人 */
    private static void applyArsonist(ServerPlayer leader, ServerPlayer follower) {
        // 目标 -2 见 LeaderEventHandler / LighterItem 联动（LeaderFollowerEffects 提供静态判定）
    }

    /** 森近霖之助 / 河城荷取：金币依附（无即时效果，didPlayerWin 判定） */
    private static void applyCoinDependent(ServerPlayer leader, ServerPlayer follower) {
        // 无即时效果
    }

    /** 芙兰朵露：追随者制式左轮、领袖得刀 */
    private static void applyFurandoru(ServerPlayer leader, ServerPlayer follower) {
        giveItem(follower, TMMItems.STANDARD_REVOLVER.getDefaultInstance());
        giveItem(leader, TMMItems.KNIFE.getDefaultInstance());
    }

    /** 其它非杀手方中立：追随者永久速度 1；双方各得 150 金币 */
    private static void applyGeneric(ServerPlayer leader, ServerPlayer follower) {
        permanentEffect(follower, MobEffects.MOVEMENT_SPEED, 0);
        SREPlayerShopComponent.KEY.get(leader).addToBalance(150);
        SREPlayerShopComponent.KEY.get(follower).addToBalance(150);
    }

    // ==================== 工具方法 ====================

    private static boolean followerRoleIs(ServerPlayer follower, String path) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(follower.level());
        SRERole role = game.getRole(follower);
        return role != null && role.identifier().getPath().equals(path);
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        RoleUtils.insertStackInFreeSlot(player, stack);
    }

    /** 永久效果（隐藏气泡） */
    private static void permanentEffect(ServerPlayer player, Holder<MobEffect> effect, int amp) {
        player.addEffect(new MobEffectInstance(effect, -1, amp, true, false, false));
    }

    /**
     * 纵火犯点燃所需被泼油人数：追随者纵火犯 -2 人，下限 1 人。
     */
    public static int arsonistRequiredDoused(int baseRequired, ServerPlayer arsonist) {
        if (isFollowerOfLeader(arsonist)) {
            return Math.max(1, baseRequired - 2);
        }
        return baseRequired;
    }

    /**
     * 领袖技能释放：将目标招募为追随者。
     *
     * @param leader 领袖
     * @param target 目标（RoleSkill 准星目标）
     * @return 是否释放成功
     */
    public static boolean tryRecruit(ServerPlayer leader, ServerPlayer target) {
        LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, leader);
        if (data == null || data.skillUsed) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(leader.level());
        if (!game.isRunning() || target == null || target.equals(leader)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SRERole targetRole = game.getRole(target);
        if (targetRole == null) {
            return false;
        }
        String path = targetRole.identifier().getPath();

        // 禁止：亡命徒 / 超级亡命徒 / 领袖自身
        if (path.equals("loose_end") || path.equals("super_loose_end") || path.equals("leader")) {
            return false;
        }

        // 教父：仅可对 isMafiaTeam 为 true 的教父释放
        if (targetRole.isMafiaTeam()) {
            if (!path.equals("godfather")) {
                return false;
            }
        } else {
            if (!targetRole.isNeutrals() || targetRole.isNeutralForKiller()) {
                return false;
            }
        }

        // 释放成功：标记技能已用
        data.markSkillUsed();

        // 全场播放音效（MASTER 类型）
        for (ServerPlayer p : leader.serverLevel().getServer().getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, SoundSource.MASTER, 1.0F, 1.0F);
        }

        // 招募目标为追随者
        data.addFollower(target, path);
        notifyFollower(leader, target);

        // 初学者联动：场上其它初学者也自动成为追随者
        if (path.equals("initiate")) {
            for (ServerPlayer p : leader.serverLevel().getServer().getPlayerList().getPlayers()) {
                if (p.equals(target)) {
                    continue;
                }
                SRERole r = game.getRole(p);
                if (r != null && r.identifier().getPath().equals("initiate")
                        && GameUtils.isPlayerAliveAndSurvival(p)) {
                    data.addFollower(p, "initiate");
                    notifyFollower(leader, p);
                }
            }
        }

        // 施加追随者效果
        applyEffect(leader, target);
        return true;
    }

    /** 通知追随者：<职业名><玩家id>已成为你的追随者 */
    private static void notifyFollower(ServerPlayer leader, ServerPlayer follower) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(follower.level());
        SRERole fr = game.getRole(follower);
        Component roleName = fr != null ? RoleUtils.getRoleName(fr) : Component.literal("?");
        Component msg = Component.translatable("message.noellesroles.leader.follower_joined", roleName,
                follower.getScoreboardName());
        ServerPlayNetworking.send(follower, new BroadcastMessageS2CPacket(msg));
    }

    /** 是否存在鹈鹕追随者（全服发光联动） */
    public static boolean hasPelicanFollower(ServerPlayer any) {
        for (ServerPlayer p : any.serverLevel().getServer().getPlayerList().getPlayers()) {
            LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, p);
            if (data == null) {
                continue;
            }
            for (UUID fid : data.followers) {
                ServerPlayer follower = any.serverLevel().getServer().getPlayerList().getPlayer(fid);
                if (follower != null && SREGameWorldComponent.KEY.get(any.level()).isRole(follower, ModRoles.PELICAN)) {
                    return true;
                }
            }
        }
        return false;
    }
}
