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

package org.agmas.noellesroles.game.roles.neutral.voice_changer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.BroadcastMessageS2CPacket;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 变声怪杰玩家组件
 *
 * 状态：
 * - markedTargets：被标记的目标（不会收到任何提示，可同时标记多人）
 * - currentVoiceType：当前选择的变声种类索引（0..13，对应 14 种变声效果）
 * - currentVoiceLevel：当前选择的变声等级（0..4，代码中 0 级 = 实际 1 级）
 *
 * 技能（见 ModRolesInitialEventRegister 注册）：
 * - 蹲下+技能键(G)：标记准星玩家（每次标记冷却 20 秒，由技能系统处理）
 * - 技能键(G)：对全部被标记目标施加当前选择的变声效果（持续 60 秒，冷却 60 秒）
 * - 技能切换键(Y)：切换变声种类
 * - 蹲下+技能切换键(Y)：切换变声等级
 */
public class VoiceChangerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    public static final ComponentKey<VoiceChangerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "voice_changer"),
            VoiceChangerPlayerComponent.class);

    // /**
    //  * 版本兼容
    //  */
    // public boolean isRequiredOnClient() {
    //     return false;
    // }

    /** 变声效果顺序（与技能切换循环一致），共 14 种。 */
    public static final List<Holder<MobEffect>> VOICE_EFFECTS = List.of(
            ModEffects.VOICE_HELIUM,
            ModEffects.VOICE_ECHO,
            ModEffects.HEAVY_METAL_VOICE,
            ModEffects.VOICE_BEEP,
            ModEffects.VOICE_CHORUS,
            ModEffects.VOICE_DISTORTION,
            ModEffects.VOICE_HELMET,
            ModEffects.VOICE_REVERB,
            ModEffects.VOICE_REVERSE,
            ModEffects.VOICE_ROBOT,
            ModEffects.VOICE_STUTTER,
            ModEffects.VOICE_SYNTH,
            ModEffects.VOICE_TREMOLO,
            ModEffects.VOICE_UNDERWATER);

    private final Player player;

    /** 被标记的目标（UUID 集合）。 */
    public Set<UUID> markedTargets = new HashSet<>();
    /** 当前选择的变声种类索引（0..13）。 */
    public int currentVoiceType = 0;
    /** 当前选择的变声等级（0..4，0 = 实际 1 级）。 */
    public int currentVoiceLevel = 0;

    public VoiceChangerPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.markedTargets.clear();
        this.currentVoiceType = 0;
        this.currentVoiceLevel = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 标记准星玩家（蹲下+技能键触发）。
     * 被标记者不会收到任何提示；可同时标记多人；每次标记冷却由技能系统处理。
     *
     * @return 是否成功标记（false 时不进入冷却）
     */
    public boolean markTarget() {
        return markTarget(null);
    }

    /**
     * 标记目标。优先使用客户端发来的准星目标 UUID（context.target()），
     * 避免旧的"最近玩家"逻辑在多人靠近时标记错人。
     *
     * @param crosshairTarget 客户端准星目标 UUID，可为 null（回退到服务端最近玩家判定）
     */
    public boolean markTarget(UUID crosshairTarget) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Player target = crosshairTarget != null ? findPlayerByUuid(crosshairTarget) : null;
        if (target == null) {
            target = getLookedAtPlayer();
        }
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.voice_changer.no_target")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false; // 失败不进入冷却
        }
        if (GameUtils.isPlayerEliminated(target)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.voice_changer.target_eliminated")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (markedTargets.contains(target.getUUID())) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.voice_changer.already_marked", target.getName())
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        markedTargets.add(target.getUUID());
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.voice_changer.marked", target.getName())
                        .withStyle(ChatFormatting.GREEN),
                true);
        this.sync();
        return true;
    }

    /**
     * 对全部被标记目标施加当前选择的变声效果（持续 60 秒）。
     * 被施加者会收到与广播员一致的广播通知。施加后清空标记列表。
     *
     * @return 是否成功施加（false 时不进入冷却）
     */
    public boolean applyVoice() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (markedTargets.isEmpty()) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.voice_changer.no_marked")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false; // 失败不进入冷却
        }
        Holder<MobEffect> effect = VOICE_EFFECTS.get(currentVoiceType);
        int appliedCount = 0;
        for (UUID uuid : markedTargets) {
            ServerPlayer target = findPlayerByUuid(uuid);
            if (target == null || GameUtils.isPlayerEliminated(target)) {
                continue;
            }
            // 施加变声药水效果：持续 60 秒，等级 = currentVoiceLevel（0 = 1 级），不显示药水粒子
            target.addEffect(ModEffects.of(effect, 60 * 20, currentVoiceLevel, false, false, true));
            // 与广播员一致的广播通知（仅通知被施加者自身）
            ServerPlayNetworking.send(target, new BroadcastMessageS2CPacket(
                    Component.translatable("message.noellesroles.voice_changer.applied_to_target",
                            Component.translatable(effect.value().getDescriptionId()))
                            .withStyle(ChatFormatting.LIGHT_PURPLE)));
            appliedCount++;
        }
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.voice_changer.applied",
                        Component.translatable(effect.value().getDescriptionId()), appliedCount)
                        .withStyle(ChatFormatting.GREEN),
                true);
        markedTargets.clear();
        this.sync();
        return true;
    }

    /** 切换变声种类（技能切换键，非蹲下）。 */
    public void switchVoiceType() {
        currentVoiceType = (currentVoiceType + 1) % VOICE_EFFECTS.size();
        if (player instanceof ServerPlayer serverPlayer) {
            Holder<MobEffect> effect = VOICE_EFFECTS.get(currentVoiceType);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.voice_changer.switched_type",
                            Component.translatable(effect.value().getDescriptionId()))
                            .withStyle(ChatFormatting.AQUA),
                    true);
        }
        this.sync();
    }

    /** 切换变声等级（蹲下+技能切换键，0..4 循环，0 = 实际 1 级）。 */
    public void switchVoiceLevel() {
        currentVoiceLevel = (currentVoiceLevel + 1) % 5;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.voice_changer.switched_level",
                            currentVoiceLevel + 1)
                            .withStyle(ChatFormatting.AQUA),
                    true);
        }
        this.sync();
    }

    /** 获取当前准星（有视线）最近的其他存活玩家。 */
    private Player getLookedAtPlayer() {
        double maxDistance = 5.0;
        Player closest = null;
        double closestDistance = maxDistance;
        for (Player other : player.level().players()) {
            if (other == player) {
                continue;
            }
            if (GameUtils.isPlayerEliminated(other)) {
                continue;
            }
            double distance = player.distanceTo(other);
            if (distance < closestDistance && player.hasLineOfSight(other)) {
                closestDistance = distance;
                closest = other;
            }
        }
        return closest;
    }

    private ServerPlayer findPlayerByUuid(UUID uuid) {
        for (Player p : player.level().players()) {
            if (p.getUUID().equals(uuid) && p instanceof ServerPlayer sp) {
                return sp;
            }
        }
        return null;
    }

    @Override
    public void serverTick() {
        // 冷却由统一技能系统管理，无需在此处理
    }

    @Override
    public void clientTick() {
        // 无需客户端 tick 逻辑
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putInt("VoiceType", currentVoiceType);
        tag.putInt("VoiceLevel", currentVoiceLevel);
        ListTag list = new ListTag();
        for (UUID uuid : markedTargets) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("MarkedTargets", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.currentVoiceType = tag.getInt("VoiceType");
        this.currentVoiceLevel = tag.getInt("VoiceLevel");
        this.markedTargets.clear();
        if (tag.contains("MarkedTargets")) {
            ListTag list = tag.getList("MarkedTargets", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                this.markedTargets.add(UUID.fromString(list.getString(i)));
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
