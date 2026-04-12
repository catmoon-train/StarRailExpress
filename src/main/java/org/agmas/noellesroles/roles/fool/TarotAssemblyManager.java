package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.CloseUiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 塔罗会管理器 - 处理塔罗会的召开、传送、投票、结算逻辑
 */
public class TarotAssemblyManager {

    /** 塔罗会冷却：存活5分钟，死亡6分钟 */
    public static final int COOLDOWN_ALIVE_TICKS = 5 * 60 * 20; // 5分钟
    public static final int COOLDOWN_DEAD_TICKS = 6 * 60 * 20; // 6分钟

    /** 会议持续时间上限（1分钟） */
    public static final int MEETING_DURATION_TICKS = 60 * 20;

    /** 投票持续时间 */
    public static final int VOTE_DURATION_TICKS = 15 * 20;

    /** 异端效果持续时间（60秒） */
    public static final int HERETIC_DURATION_TICKS = 60 * 20;

    /** 传送到的会议室Y坐标（使用高空虚空区域） */
    public static final double MEETING_Y = 200;
    public static final double MEETING_X = 0.0;
    public static final double MEETING_Z = 10000.0;

    private static final int BLINDNESS_DURATION_TICKS = MEETING_DURATION_TICKS + VOTE_DURATION_TICKS + 60;
    private static final int MANUAL_ADVANCE_LOCK_TICKS = 20;

    /**
     * 愚者按G键召开塔罗会
     */
    public static void startAssembly(ServerPlayer fool) {
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        long currentTick = fool.level().getGameTime();

        // G键再次使用：直接提前结束并结算当前投票结果
        if (comp.inMeeting) {
            if (currentTick < comp.meetingStartTick + MANUAL_ADVANCE_LOCK_TICKS) {
                return;
            }
            finalizeVotingAndEndMeeting(fool);
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
        comp.meetingStartTick = currentTick;
        comp.voteInProgress = false;
        comp.meetingEndTick = currentTick + MEETING_DURATION_TICKS;
        comp.voteEndTick = 0;
        comp.meetingOriginalPositions.clear();
        comp.meetingPuppetIds.clear();
        comp.voteEligibleParticipants.clear();
        comp.meetingVotes.clear();

        ServerLevel serverLevel = (ServerLevel) fool.level();
        ensureMeetingScene(serverLevel);

        // 向所有塔罗会成员发送Title提示
        for (UUID memberUuid : comp.tarotMembers) {
            ServerPlayer member = serverLevel.getServer().getPlayerList().getPlayer(memberUuid);
            if (member != null) {
                sendTarotInvitation(member);
            }
        }

        teleportToMeeting(fool, comp, serverLevel);
        comp.voteInProgress = true;
        comp.voteEndTick = comp.meetingEndTick;
        refreshVoteParticipants(serverLevel, comp);

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
        long currentTick = serverLevel.getGameTime();
        if (!comp.inMeeting) return;
        if (currentTick >= comp.meetingEndTick) return;
        if (!comp.isTarotMember(member.getUUID())) return;

        // 避免重复加入
        if (comp.meetingOriginalPositions.containsKey(member.getUUID())) return;

        teleportToMeeting(member, comp, serverLevel);
        refreshVoteParticipants(serverLevel, comp);
        comp.sync();
    }

    /**
     * 传送玩家到会议室，并在原位生成傀儡
     */
    private static void teleportToMeeting(ServerPlayer player, FoolPlayerComponent foolComp,
            ServerLevel serverLevel) {
        foolComp.meetingOriginalPositions.put(player.getUUID(),
            new double[] { player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot() });

        syncParticipantMeetingState(player, true, foolComp.meetingEndTick, foolComp.voteInProgress, foolComp.voteEndTick);

        player.addEffect(new MobEffectInstance(ModEffects.BLACK_MONITOR,20*1,0,false,false,false));
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            spawnMeetingPuppet(player, foolComp, serverLevel);
        }

        int index = Math.max(0, foolComp.meetingOriginalPositions.size() - 1);
        double angle = (Math.PI * 2.0D * index) / 8.0D;
        double radius = 5.0D + (index / 8) * 2.0D;
        double targetX = MEETING_X + Math.cos(angle) * radius;
        double targetZ = MEETING_Z + Math.sin(angle) * radius;
        float yaw = (float) (Math.atan2(MEETING_Z - targetZ, MEETING_X - targetX) * 180.0D / Math.PI) - 90.0F;

        player.teleportTo(serverLevel, targetX, MEETING_Y + 1.0D, targetZ, Set.of(), yaw, 0.0F);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_DURATION_TICKS, 0, false, false,
            false));
        if (foolComp.getPlayer()!=player) {
            player.addEffect(new MobEffectInstance(ModEffects.TAROT_ASSEMBLY, BLINDNESS_DURATION_TICKS, 0, false, false,
                    false));
        }
        ServerPlayNetworking.send(player, new CloseUiPayload());
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
        if (!comp.inMeeting) return;

        if (member.getUUID().equals(fool.getUUID())) {
            finalizeVotingAndEndMeeting(fool);
            return;
        }

        comp.meetingVotes.remove(member.getUUID());
        comp.meetingVotes.entrySet().removeIf(entry -> entry.getValue().equals(member.getUUID()));
        teleportBack(member, comp, serverLevel);
        refreshVoteParticipants(serverLevel, comp);
        comp.sync();
    }

    /**
     * 结束会议并将玩家送回原位
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
        comp.meetingStartTick = 0;
        comp.voteInProgress = false;
        comp.meetingEndTick = 0;
        comp.voteEndTick = 0;
        comp.meetingOriginalPositions.clear();
        comp.meetingPuppetIds.clear();
        comp.voteEligibleParticipants.clear();
        comp.meetingVotes.clear();
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
                    Set.of(), (float) pos[3], (float) pos[4]);
        }

        syncParticipantMeetingState(player, false, 0, false, 0);

        // 移除傀儡
        Integer puppetId = foolComp.meetingPuppetIds.remove(player.getUUID());
        if (puppetId != null) {
            var entity = serverLevel.getEntity(puppetId);
            if (entity != null) {
                entity.discard();
            }
        }

        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(ModEffects.TAROT_ASSEMBLY);


        player.displayClientMessage(
                Component.translatable("message.noellesroles.fool.left_meeting").withStyle(ChatFormatting.GRAY),
                true);
    }

    /**
     * 处理投票结果
     *
     * @param votes 投票映射：投票者UUID -> 被投票者UUID
     */
    public static void processVoteResults(ServerPlayer fool, Map<UUID, UUID> votes, Set<UUID> eligibleTargets) {
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        ServerLevel serverLevel = (ServerLevel) fool.level();
        long currentTick = serverLevel.getGameTime();

        // 统计票数
        Map<UUID, Integer> voteCount = new HashMap<>();
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            UUID voter = entry.getKey();
            UUID votedFor = entry.getValue();
            if (!eligibleTargets.contains(voter)) continue;
            if (!eligibleTargets.contains(votedFor)) continue;
            if (voter.equals(votedFor)) continue;
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
        if (tie || voteCount.isEmpty()) {
            // 无产生异端（平票或无人投票或投到死人）
            // 愚者获得一把"一次性手枪"
            if (GameUtils.isPlayerAliveAndSurvival(fool)) {
                net.minecraft.world.item.ItemStack onceRevolver = new net.minecraft.world.item.ItemStack(ModItems.ONCE_REVOLVER);
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
            comp.clearHeretic();
        }

        // 检查灵性斗篷效果是否过期
        if (comp.cloakActive && currentTick >= comp.cloakEndTick) {
            comp.cloakActive = false;
            comp.sync();
        }

        if (!comp.inMeeting) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        if (currentTick % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, MEETING_X, MEETING_Y + 2.5D, MEETING_Z, 18,
                    7.0D, 1.5D, 7.0D, 0.01D);
        }

        if (currentTick >= comp.meetingEndTick) {
            finalizeVotingAndEndMeeting(player);
        }
    }

    public static void submitVote(ServerPlayer player, UUID votedFor) {
        ServerLevel serverLevel = (ServerLevel) player.level();
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverLevel);
        ServerPlayer fool = findFoolPlayer(serverLevel, gameComponent);
        if (fool == null) return;

        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        if (!comp.inMeeting || !comp.voteInProgress) return;
        if (!comp.canVote(player.getUUID())) return;
        if (player.getUUID().equals(votedFor)) return;
        if (!comp.voteEligibleParticipants.contains(votedFor)) return;

        comp.meetingVotes.put(player.getUUID(), votedFor);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.fool.vote_cast").withStyle(ChatFormatting.GREEN),
                true);
        comp.sync();
    }

    public static void requestVoteScreen(ServerPlayer player) {
        ServerLevel serverLevel = (ServerLevel) player.level();
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverLevel);
        ServerPlayer fool = findFoolPlayer(serverLevel, gameComponent);
        if (fool == null) return;

        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        long currentTick = serverLevel.getGameTime();
        if (!comp.inMeeting) return;
        if (currentTick >= comp.meetingEndTick) return;
        if (!comp.meetingOriginalPositions.containsKey(player.getUUID())) return;

        openVoteScreenForPlayer(player, comp, currentTick);
    }

    private static void finalizeVotingAndEndMeeting(ServerPlayer fool) {
        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(fool);
        Set<UUID> eligibleTargets = new HashSet<>(comp.voteEligibleParticipants);
        Map<UUID, UUID> votes = new HashMap<>(comp.meetingVotes);
        processVoteResults(fool, votes, eligibleTargets);
        endMeeting(fool);
    }

    private static Set<UUID> collectAliveParticipants(ServerLevel serverLevel, FoolPlayerComponent comp) {
        Set<UUID> aliveParticipants = new HashSet<>();
        for (UUID participantUuid : comp.meetingOriginalPositions.keySet()) {
            ServerPlayer participant = serverLevel.getServer().getPlayerList().getPlayer(participantUuid);
            if (participant != null && GameUtils.isPlayerAliveAndSurvival(participant)) {
                aliveParticipants.add(participantUuid);
            }
        }
        return aliveParticipants;
    }

    private static void refreshVoteParticipants(ServerLevel serverLevel, FoolPlayerComponent comp) {
        Set<UUID> aliveParticipants = collectAliveParticipants(serverLevel, comp);
        comp.voteEligibleParticipants.clear();
        comp.voteEligibleParticipants.addAll(aliveParticipants);
        comp.meetingVotes.entrySet().removeIf(entry ->
                !aliveParticipants.contains(entry.getKey()) || !aliveParticipants.contains(entry.getValue()));
    }

    private static void openVoteScreenForPlayer(ServerPlayer voter, FoolPlayerComponent comp, long currentTick) {
        if (!comp.canVote(voter.getUUID())) return;

        List<UUID> candidates = new ArrayList<>();
        for (UUID candidateUuid : comp.voteEligibleParticipants) {
            if (!candidateUuid.equals(voter.getUUID())) {
                candidates.add(candidateUuid);
            }
        }

        if (candidates.isEmpty()) return;
        int remainingSeconds = Math.max(1, (int) Math.ceil((comp.meetingEndTick - currentTick) / 20.0D));
        ServerPlayNetworking.send(voter, new FoolOpenTarotVoteS2CPacket(candidates, remainingSeconds));
        voter.displayClientMessage(
            Component.translatable("message.noellesroles.fool.vote_started")
                .withStyle(ChatFormatting.GOLD),
            false);
    }

    private static void syncParticipantMeetingState(ServerPlayer player, boolean inMeeting, long meetingEndTick,
            boolean voteInProgress, long voteEndTick) {
        FoolPlayerComponent participantComp = FoolPlayerComponent.KEY.get(player);
        participantComp.inMeeting = inMeeting;
        participantComp.meetingEndTick = meetingEndTick;
        participantComp.voteInProgress = voteInProgress;
        participantComp.voteEndTick = voteEndTick;
        if (!inMeeting) {
            participantComp.meetingStartTick = 0;
        }
        participantComp.sync();
    }

    private static void spawnMeetingPuppet(ServerPlayer player, FoolPlayerComponent foolComp, ServerLevel serverLevel) {
        PuppeteerBodyEntity puppet = new PuppeteerBodyEntity(ModEntities.PUPPETEER_BODY, serverLevel);
        puppet.setPos(player.getX(), player.getY(), player.getZ());
        puppet.setYRot(player.getYRot());
        puppet.setCustomName(player.getDisplayName());
        puppet.setCustomNameVisible(false);
        puppet.setOwner(player);
        puppet.addTag("fool_meeting_puppet");
        puppet.addTag("puppet_owner_" + player.getUUID());
        serverLevel.addFreshEntity(puppet);
        foolComp.meetingPuppetIds.put(player.getUUID(), puppet.getId());
    }

    private static void ensureMeetingScene(ServerLevel serverLevel) {
        new TarotAssemblySceneBuilder(serverLevel).build(new BlockPos((int) MEETING_X, (int) MEETING_Y, (int) MEETING_Z));
    }
}
