package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
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

    // ==================== 庇护效果 ====================
    /** 庇护来源UUID（免疫来自该玩家的下一次伤害），null表示无庇护 */
    public UUID protectionSource = null;

    // ==================== 会议状态 ====================
    /** 当前是否正在塔罗会中 */
    public boolean inMeeting = false;

    /** 会议结束时间 */
    public long meetingEndTick = 0;

    /** 会议参与者的原始位置（UUID -> 坐标，服务端维护） */
    public transient Map<UUID, double[]> meetingOriginalPositions = new HashMap<>();

    /** 会议中的傀儡实体ID（UUID -> entityId） */
    public transient Map<UUID, Integer> meetingPuppetIds = new HashMap<>();

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

    @Override
    public void init() {
        tarotMembers.clear();
        executionerBullets = 1;
        tarotCooldownEndTick = 0;
        hereticTarget = null;
        hereticEndTick = 0;
        protectionSource = null;
        inMeeting = false;
        meetingEndTick = 0;
        meetingOriginalPositions.clear();
        meetingPuppetIds.clear();
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
     */
    public void addTarotMember(UUID playerUuid) {
        tarotMembers.add(playerUuid);
        this.sync();
    }

    /**
     * 检查异端效果是否有效
     */
    public boolean hasActiveHeretic(long currentTick) {
        return hereticTarget != null && currentTick < hereticEndTick;
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

    /**
     * 设置庇护来源
     */
    public void setProtection(UUID source) {
        this.protectionSource = source;
        this.sync();
    }

    /**
     * 消耗庇护效果
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

        // 祷告
        isPraying = tag.getBoolean("is_praying");

        // 灵性斗篷
        cloakCooldownEndTick = tag.getLong("cloak_cooldown_end");
        cloakActive = tag.getBoolean("cloak_active");
        cloakEndTick = tag.getLong("cloak_end_tick");
    }
}
