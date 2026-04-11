package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.ModRoles;

import java.util.*;

/**
 * 塔罗会管理器 - 处理塔罗会的召开、传送、投票、结算逻辑
 */
public class TarotAssemblyManager {

    /** 塔罗会冷却：存活5分钟，死亡6分钟 */
    public static final int COOLDOWN_ALIVE_TICKS = 5 * 60 * 20; // 5分钟
    public static final int COOLDOWN_DEAD_TICKS = 6 * 60 * 20; // 6分钟

    /** 会议持续时间（45~60秒），使用50秒作为默认 */
    public static final int MEETING_DURATION_TICKS = 50 * 20;

    /** 异端效果持续时间（60秒） */
    public static final int HERETIC_DURATION_TICKS = 60 * 20;

    /** 传送到的会议室Y坐标（使用高空虚空区域） */
    public static final double MEETING_Y = 300.0;
    public static final double MEETING_X = 0.0;
    public static final double MEETING_Z = 10000.0;

    /**
     * 愚者按G键召开塔罗会
     */
    public static void startAssembly(ServerPlayer fool) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(fool.level());
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        long currentTick = fool.level().getGameTime();

        // 如果已在会议中——提前结束
        if (comp.inMeeting) {
            endMeeting(fool);
            return;
        }

        // 检查冷却
        if (currentTick < comp.tarotCooldownEndTick) {
            long remaining = (comp.tarotCooldownEndTick - currentTick) / 20;
            fool.displayClientMessage(
                    Component.translatable("message.noellesroles.fool.tarot_cooldown", remaining)
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 设置冷却（根据是否存活）
        boolean isAlive = GameUtils.isPlayerAliveAndSurvival(fool);
        int cooldownTicks = isAlive ? COOLDOWN_ALIVE_TICKS : COOLDOWN_DEAD_TICKS;
        comp.tarotCooldownEndTick = currentTick + cooldownTicks;

        // 标记进入会议
        comp.inMeeting = true;
        comp.meetingEndTick = currentTick + MEETING_DURATION_TICKS;
        comp.meetingOriginalPositions.clear();
        comp.meetingPuppetIds.clear();

        // 向所有塔罗会成员发送Title提示
        ServerLevel serverLevel = (ServerLevel) fool.level();
        for (UUID memberUuid : comp.tarotMembers) {
            ServerPlayer member = serverLevel.getServer().getPlayerList().getPlayer(memberUuid);
            if (member != null) {
                sendTarotInvitation(member);
            }
        }

        // 如果愚者自己存活，也传送自己
        if (isAlive) {
            teleportToMeeting(fool, comp, serverLevel);
        }

        comp.sync();

        fool.displayClientMessage(
                Component.translatable("message.noellesroles.fool.tarot_started").withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 发送塔罗会邀请Title
     */
    private static void sendTarotInvitation(ServerPlayer player) {
        Component title = Component.translatable("message.noellesroles.fool.tarot_invite_title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.fool.tarot_invite_subtitle")
                .withStyle(ChatFormatting.YELLOW);

        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));

        player.displayClientMessage(
                Component.translatable("message.noellesroles.fool.tarot_invite_chat")
                        .withStyle(ChatFormatting.GOLD),
                false);
    }

    /**
     * 塔罗会成员按V键接受邀请并进入会议
     */
    public static void memberJoinMeeting(ServerPlayer member) {
        ServerLevel serverLevel = (ServerLevel) member.level();
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

        // 查找愚者
        ServerPlayer fool = findFoolPlayer(serverLevel, gameComponent);
        if (fool == null) return;

        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        if (!comp.inMeeting) return;
        if (!comp.isTarotMember(member.getUUID())) return;

        // 避免重复加入
        if (comp.meetingOriginalPositions.containsKey(member.getUUID())) return;

        teleportToMeeting(member, comp, serverLevel);
    }

    /**
     * 传送玩家到会议室，并在原位生成傀儡
     */
    private static void teleportToMeeting(ServerPlayer player, FoolPlayerComponent foolComp,
            ServerLevel serverLevel) {
        // 记录原始位置
        foolComp.meetingOriginalPositions.put(player.getUUID(),
                new double[] { player.getX(), player.getY(), player.getZ(),
                        player.getYRot(), player.getXRot() });

        // 在原地生成傀儡（带发光效果的ArmorStand）
        ArmorStand puppet = new ArmorStand(EntityType.ARMOR_STAND, serverLevel);
        puppet.setPos(player.getX(), player.getY(), player.getZ());
        puppet.setYRot(player.getYRot());
        puppet.setCustomName(player.getDisplayName());
        puppet.setCustomNameVisible(true);
        puppet.setInvulnerable(true);
        puppet.setNoGravity(true);
        puppet.setGlowingTag(true);
        puppet.addTag("fool_meeting_puppet");
        puppet.addTag("puppet_owner_" + player.getUUID());
        serverLevel.addFreshEntity(puppet);
        foolComp.meetingPuppetIds.put(player.getUUID(), puppet.getId());

        // 传送玩家到会议室
        int index = foolComp.meetingOriginalPositions.size();
        double offsetX = (index % 4) * 3.0;
        double offsetZ = (index / 4) * 3.0;
        player.teleportTo(serverLevel, MEETING_X + offsetX, MEETING_Y, MEETING_Z + offsetZ,
                Set.of(), 0, 0, true);

        player.displayClientMessage(
                Component.translatable("message.noellesroles.fool.entered_meeting")
                        .withStyle(ChatFormatting.GOLD),
                true);
    }

    /**
     * 玩家退出会议（按ESC或右键）
     */
    public static void memberLeaveMeeting(ServerPlayer member) {
        ServerLevel serverLevel = (ServerLevel) member.level();
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverLevel);

        ServerPlayer fool = findFoolPlayer(serverLevel, gameComponent);
        if (fool == null) return;

        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        teleportBack(member, comp, serverLevel);
    }

    /**
     * 结束会议（愚者按G键或时间到）
     */
    public static void endMeeting(ServerPlayer fool) {
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        if (!comp.inMeeting) return;

        ServerLevel serverLevel = (ServerLevel) fool.level();

        // 将所有参与者传送回原位
        for (Map.Entry<UUID, double[]> entry : new HashMap<>(comp.meetingOriginalPositions).entrySet()) {
            ServerPlayer participant = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (participant != null) {
                teleportBack(participant, comp, serverLevel);
            }
        }

        comp.inMeeting = false;
        comp.meetingEndTick = 0;
        comp.meetingOriginalPositions.clear();
        comp.meetingPuppetIds.clear();
        comp.sync();
    }

    /**
     * 传送玩家回原位并移除傀儡
     */
    private static void teleportBack(ServerPlayer player, FoolPlayerComponent foolComp,
            ServerLevel serverLevel) {
        double[] pos = foolComp.meetingOriginalPositions.remove(player.getUUID());
        if (pos != null) {
            player.teleportTo(serverLevel, pos[0], pos[1], pos[2],
                    Set.of(), (float) pos[3], (float) pos[4], true);
        }

        // 移除傀儡
        Integer puppetId = foolComp.meetingPuppetIds.remove(player.getUUID());
        if (puppetId != null) {
            var entity = serverLevel.getEntity(puppetId);
            if (entity != null) {
                entity.discard();
            }
        }

        player.displayClientMessage(
                Component.translatable("message.noellesroles.fool.left_meeting").withStyle(ChatFormatting.GRAY),
                true);
    }

    /**
     * 处理投票结果
     *
     * @param votes 投票映射：投票者UUID -> 被投票者UUID
     */
    public static void processVoteResults(ServerPlayer fool, Map<UUID, UUID> votes) {
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        ServerLevel serverLevel = (ServerLevel) fool.level();
        long currentTick = serverLevel.getGameTime();

        // 统计票数
        Map<UUID, Integer> voteCount = new HashMap<>();
        for (UUID votedFor : votes.values()) {
            voteCount.merge(votedFor, 1, Integer::sum);
        }

        // 找到最高票数
        UUID hereticUuid = null;
        int maxVotes = 0;
        boolean tie = false;

        for (Map.Entry<UUID, Integer> entry : voteCount.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                hereticUuid = entry.getKey();
                tie = false;
            } else if (entry.getValue() == maxVotes) {
                tie = true;
            }
        }

        // 检查是否投给了死人
        boolean votedForDead = false;
        if (hereticUuid != null) {
            ServerPlayer hereticPlayer = serverLevel.getServer().getPlayerList().getPlayer(hereticUuid);
            if (hereticPlayer != null && !GameUtils.isPlayerAliveAndSurvival(hereticPlayer)) {
                votedForDead = true;
            }
        }

        if (tie || votes.isEmpty() || votedForDead) {
            // 无产生异端（平票或无人投票或投到死人）
            // 愚者获得一把"一次性手枪"
            if (GameUtils.isPlayerAliveAndSurvival(fool)) {
                net.minecraft.world.item.ItemStack onceRevolver = new net.minecraft.world.item.ItemStack(org.agmas.noellesroles.init.ModItems.ONCE_REVOLVER);
                fool.getInventory().add(onceRevolver);
                fool.displayClientMessage(
                        Component.translatable("message.noellesroles.fool.vote_no_heretic")
                                .withStyle(ChatFormatting.YELLOW),
                        false);
            }
        } else if (hereticUuid != null) {
            // 产生异端
            comp.setHeretic(hereticUuid, currentTick + HERETIC_DURATION_TICKS);

            // 检查处刑者手枪子弹
            if (comp.executionerBullets < 1 && GameUtils.isPlayerAliveAndSurvival(fool)) {
                comp.executionerBullets = 1;
                comp.sync();
                fool.displayClientMessage(
                        Component.translatable("message.noellesroles.fool.bullet_replenished")
                                .withStyle(ChatFormatting.GOLD),
                        false);
            } else if (comp.executionerBullets >= 1 && GameUtils.isPlayerAliveAndSurvival(fool)) {
                fool.displayClientMessage(
                        Component.translatable("message.noellesroles.fool.bullet_full")
                                .withStyle(ChatFormatting.YELLOW),
                        false);
            }

            // 通知异端玩家
            ServerPlayer hereticPlayer = serverLevel.getServer().getPlayerList().getPlayer(hereticUuid);
            if (hereticPlayer != null) {
                hereticPlayer.setGlowingTag(true);
            }

            fool.displayClientMessage(
                    Component.translatable("message.noellesroles.fool.heretic_found",
                            hereticPlayer != null ? hereticPlayer.getName().getString() : "???")
                            .withStyle(ChatFormatting.RED),
                    false);
        }

        comp.sync();
    }

    /**
     * 查找当前游戏中的愚者玩家
     */
    public static ServerPlayer findFoolPlayer(ServerLevel level, SREGameWorldComponent gameComponent) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (gameComponent.isRole(player, ModRoles.THE_FOOL)) {
                return player;
            }
        }
        return null;
    }

    /**
     * 服务端Tick处理
     */
    public static void serverTick(ServerPlayer player, SREGameWorldComponent gameComponent) {
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(player);
        long currentTick = player.level().getGameTime();

        // 检查异端效果是否过期
        if (comp.hereticTarget != null && currentTick >= comp.hereticEndTick) {
            // 清除异端发光
            ServerLevel serverLevel = (ServerLevel) player.level();
            ServerPlayer hereticPlayer = serverLevel.getServer().getPlayerList()
                    .getPlayer(comp.hereticTarget);
            if (hereticPlayer != null) {
                hereticPlayer.setGlowingTag(false);
            }
            comp.clearHeretic();
        }

        // 检查灵性斗篷效果是否过期
        if (comp.cloakActive && currentTick >= comp.cloakEndTick) {
            comp.cloakActive = false;
            comp.sync();
        }

        // 检查会议是否超时
        if (comp.inMeeting && currentTick >= comp.meetingEndTick) {
            // 开始投票阶段（通知所有参与者打开投票GUI）
            // 暂时直接结束会议
            endMeeting(player);
        }
    }
}
