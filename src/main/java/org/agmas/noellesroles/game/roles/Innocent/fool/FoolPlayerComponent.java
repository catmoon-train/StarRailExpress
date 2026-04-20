package org.agmas.noellesroles.game.roles.Innocent.fool;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowSpectatorPlayerInAreas;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.*;

/**
 * 愚者组件 - 存储愚者职业的所有运行时状态
 *
 * 功能：
 * - 塔罗会成员列表（全局，哪些玩家拥有"塔罗会成员"标签）
 * - 处刑者手枪子弹数
 * - 塔罗会冷却时间
 * - 异端效果目标及持续时间
 * - 庇护效果（免疫来自特定玩家的下一次伤害）
 * - 愚者死亡状态
 */
public class FoolPlayerComponent implements RoleComponent {

    public static final ComponentKey<FoolPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fool"),
            FoolPlayerComponent.class);

    private final Player player;

    // ==================== 塔罗会成员列表（存储在愚者身上） ====================
    /** 所有拥有"塔罗会成员"标签的玩家UUID */
    public Set<UUID> tarotMembers = new HashSet<>();

    /** 塔罗会最大成员数量 */
    public static final int MAX_TAROT_MEMBERS = 12;

    // ==================== 处刑者手枪 ====================
    /** 处刑者手枪当前子弹数（最大1） */
    public int executionerBullets = 1;

    // ==================== 塔罗会冷却 ====================
    /** 塔罗会冷却结束的游戏时间（server tick），0表示可用 */
    public long tarotCooldownEndTick = 0;

    // ==================== 异端效果 ====================
    /** 当前被标记为异端的玩家UUID，null表示无异端 */
    public UUID hereticTarget = null;

    /** 异端效果结束的游戏时间（server tick） */
    public long hereticEndTick = 0;

    /** 本局是否已经补发过开局处刑者手枪 */
    public boolean starterGunGranted = false;

    // ==================== 庇护效果 ====================
    /** 庇护来源UUID（免疫来自该玩家的下一次伤害），null表示无庇护 */
    public UUID protectionSource = null;

    // ==================== 会议状态 ====================
    /** 当前是否正在塔罗会中 */
    public boolean inMeeting = false;

    /** 会议开始时间，用于避免同一次按键导致立即结束 */
    public long meetingStartTick = 0;

    /** 会议结束时间 */
    public long meetingEndTick = 0;

    /** 当前是否处于匿名投票阶段 */
    public boolean voteInProgress = false;

    /** 投票阶段结束时间 */
    public long voteEndTick = 0;

    /** 会议参与者的原始位置（UUID -> 坐标，服务端维护） */
    public transient Map<UUID, double[]> meetingOriginalPositions = new LinkedHashMap<>();

    /** 会议中的傀儡实体ID（UUID -> entityId） */
    public transient Map<UUID, Integer> meetingPuppetIds = new HashMap<>();

    /** 当前投票阶段的合格投票人 */
    public transient Set<UUID> voteEligibleParticipants = new LinkedHashSet<>();

    /** 当前投票记录（投票人 -> 目标） */
    public transient Map<UUID, UUID> meetingVotes = new HashMap<>();

    // ==================== 祷告读条 ====================
    /** 当前是否正在祷告读条（客户端/服务端） */
    public boolean isPraying = false;

    /** 祷告开始的tick */
    public long prayStartTick = 0;

    /** 祷告时的位置（用于检测移动打断） */
    public double prayX, prayY, prayZ;

    // ==================== 灵性斗篷冷却 ====================
    /** 灵性斗篷冷却结束的游戏时间 */
    public long cloakCooldownEndTick = 0;

    /** 灵性斗篷效果是否激活 */
    public boolean cloakActive = false;

    /** 灵性斗篷效果结束tick */
    public long cloakEndTick = 0;

    public FoolPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public static void useSkill(RoleSkillContext context) {
        ServerPlayer player = context.player();
        org.agmas.noellesroles.game.roles.Innocent.fool.TarotAssemblyManager.startAssembly(player);
    }

    static {
        AllowSpectatorPlayerInAreas.EVENT.register((p) -> {
            if (TarotAssemblyManager.havingMeeting) {
                final double PADDING = 10;
                double x_min = TarotAssemblyManager.MEETING_X - TarotAssemblySceneBuilder.HALL_HALF_WIDTH - PADDING;
                double x_max = TarotAssemblyManager.MEETING_X + TarotAssemblySceneBuilder.HALL_HALF_WIDTH + PADDING;
                double y_min = TarotAssemblyManager.MEETING_Y - TarotAssemblySceneBuilder.ROOM_HEIGHT - PADDING;
                double y_max = TarotAssemblyManager.MEETING_Y + TarotAssemblySceneBuilder.ROOM_HEIGHT + PADDING;
                double z_min = TarotAssemblyManager.MEETING_Z - TarotAssemblySceneBuilder.HALL_HALF_LENGTH - PADDING;
                double z_max = TarotAssemblyManager.MEETING_Z + TarotAssemblySceneBuilder.HALL_HALF_LENGTH + PADDING;
                return new AABB(new Vec3(x_min, y_min, z_min), new Vec3(x_max, y_max, z_max)).contains(p.position());
            }
            return false;
        });
        RoleSkill.register(ModRoles.THE_FOOL, FoolPlayerComponent::useSkill);
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, deathReason) -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
            if (gameWorld.isRole(player, ModRoles.THE_FOOL)) {
                FoolPlayerComponent foolPlayerComponent = KEY.get(player);
                if (foolPlayerComponent.protectionSource == null)
                    return true;
                if (foolPlayerComponent.protectionSource.equals(killer.getUUID())) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer
                                .sendSystemMessage(Component.translatable("message.noellesroles.fool.protection_immune",
                                        killer.getDisplayName().getString()), true);
                        serverPlayer.playNotifySound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0f,
                                1.0f);
                        ((ServerPlayer) killer).playNotifySound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                                SoundSource.PLAYERS, 1.0f, 1.0f);
                        ((ServerPlayer) killer)
                                .sendSystemMessage(Component.translatable("message.noellesroles.fool.protection_immune",
                                        player.getDisplayName().getString()), true);
                    }
                    foolPlayerComponent.protectionSource = null;
                    return false;
                }
            }
            return true;
        });
    }

    @Override
    public void init() {
        tarotMembers.clear();
        executionerBullets = 1;
        tarotCooldownEndTick = 0;
        hereticTarget = null;
        hereticEndTick = 0;
        starterGunGranted = false;
        protectionSource = null;
        inMeeting = false;
        meetingStartTick = 0;
        meetingEndTick = 0;
        voteInProgress = false;
        voteEndTick = 0;
        meetingOriginalPositions.clear();
        meetingPuppetIds.clear();
        voteEligibleParticipants.clear();
        meetingVotes.clear();
        isPraying = false;
        prayStartTick = 0;
        cloakCooldownEndTick = 0;
        cloakActive = false;
        cloakEndTick = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 检查指定玩家是否为塔罗会成员
     */
    public boolean isTarotMember(UUID playerUuid) {
        return tarotMembers.contains(playerUuid);
    }

    /**
     * 添加塔罗会成员
     * 
     * @return true 如果添加成功，false 如果已达到最大成员数
     */
    public boolean addTarotMember(UUID playerUuid) {
        if (tarotMembers.size() >= MAX_TAROT_MEMBERS) {
            return false;
        }
        tarotMembers.add(playerUuid);
        this.sync();
        return true;
    }

    /**
     * 检查异端效果是否有效
     */
    public boolean hasActiveHeretic(long currentTick) {
        return hereticTarget != null;
    }

    /**
     * 设置异端目标
     */
    public void setHeretic(UUID target, long endTick) {
        this.hereticTarget = target;
        this.hereticEndTick = endTick;
        this.sync();
    }

    /**
     * 清除异端效果
     */
    public void clearHeretic() {
        this.hereticTarget = null;
        this.hereticEndTick = 0;
        this.sync();
    }

    public void clearProtection() {
        this.protectionSource = null;
        this.sync();
    }

    public void beginVotePhase(Collection<UUID> participants, long endTick) {
        this.voteInProgress = true;
        this.voteEndTick = endTick;
        this.voteEligibleParticipants.clear();
        this.voteEligibleParticipants.addAll(participants);
        this.meetingVotes.clear();
        this.sync();
    }

    public void clearVotePhase() {
        this.voteInProgress = false;
        this.voteEndTick = 0;
        this.voteEligibleParticipants.clear();
        this.meetingVotes.clear();
        this.sync();
    }

    public boolean canVote(UUID playerUuid) {
        return voteEligibleParticipants.contains(playerUuid);
    }

    /**
     * 设置庇护来源
     */
    public void setProtection(UUID source) {
        this.protectionSource = source;
        this.sync();
    }

    /**
     * 消耗庇护效果
     *
     * @return true 如果庇护被消耗
     */
    public boolean consumeProtection(UUID attackerUuid) {
        if (protectionSource != null && protectionSource.equals(attackerUuid)) {
            protectionSource = null;
            this.sync();
            return true;
        }
        return false;
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 塔罗会成员
        ListTag memberList = new ListTag();
        for (UUID uuid : tarotMembers) {
            memberList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("tarot_members", memberList);

        // 子弹数
        tag.putInt("executioner_bullets", executionerBullets);

        // 冷却
        tag.putLong("tarot_cooldown_end", tarotCooldownEndTick);

        // 异端
        if (hereticTarget != null) {
            tag.putString("heretic_target", hereticTarget.toString());
            tag.putLong("heretic_end_tick", hereticEndTick);
        }

        // 庇护
        if (protectionSource != null) {
            tag.putString("protection_source", protectionSource.toString());
        }

        // 会议状态
        tag.putBoolean("in_meeting", inMeeting);
        tag.putLong("meeting_end_tick", meetingEndTick);
        tag.putBoolean("vote_in_progress", voteInProgress);
        tag.putLong("vote_end_tick", voteEndTick);

        // 祷告
        tag.putBoolean("is_praying", isPraying);

        // 灵性斗篷
        tag.putLong("cloak_cooldown_end", cloakCooldownEndTick);
        tag.putBoolean("cloak_active", cloakActive);
        tag.putLong("cloak_end_tick", cloakEndTick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 塔罗会成员
        tarotMembers.clear();
        if (tag.contains("tarot_members")) {
            ListTag memberList = tag.getList("tarot_members", Tag.TAG_STRING);
            for (int i = 0; i < memberList.size(); i++) {
                try {
                    tarotMembers.add(UUID.fromString(memberList.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // 子弹数
        executionerBullets = tag.getInt("executioner_bullets");

        // 冷却
        tarotCooldownEndTick = tag.getLong("tarot_cooldown_end");

        // 异端
        if (tag.contains("heretic_target")) {
            try {
                hereticTarget = UUID.fromString(tag.getString("heretic_target"));
                hereticEndTick = tag.getLong("heretic_end_tick");
            } catch (IllegalArgumentException e) {
                hereticTarget = null;
                hereticEndTick = 0;
            }
        } else {
            hereticTarget = null;
            hereticEndTick = 0;
        }

        // 庇护
        if (tag.contains("protection_source")) {
            try {
                protectionSource = UUID.fromString(tag.getString("protection_source"));
            } catch (IllegalArgumentException e) {
                protectionSource = null;
            }
        } else {
            protectionSource = null;
        }

        // 会议状态
        inMeeting = tag.getBoolean("in_meeting");
        meetingEndTick = tag.getLong("meeting_end_tick");
        voteInProgress = tag.getBoolean("vote_in_progress");
        voteEndTick = tag.getLong("vote_end_tick");

        // 祷告
        isPraying = tag.getBoolean("is_praying");

        // 灵性斗篷
        cloakCooldownEndTick = tag.getLong("cloak_cooldown_end");
        cloakActive = tag.getBoolean("cloak_active");
        cloakEndTick = tag.getLong("cloak_end_tick");
    }
}
