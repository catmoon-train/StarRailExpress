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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EmbalmerRoleData extends SimpleRoleData {


    public static final int MASQUERADE_COOLDOWN = 150 * 20;
    public static final int MASQUERADE_INITIAL_COOLDOWN = 150 * 20; // 开局2分半冷却
    public static final int MASQUERADE_DURATION = 30 * 20;
    public static final int PITCH_MIN = 70;
    public static final int PITCH_MAX = 130;

    /** 变身派对的技能 ID（与 ModRolesInitialEventRegister 注册处保持一致）。 */
    public static final ResourceLocation MASQUERADE_SKILL_ID = SRE.id("embalmer_masquerade");

    public boolean masqueradeActive;
    public int masqueradeTicksLeft;
    /** 语音互换（背包 Screen）的独立冷却，避免与统一技能系统的冷却字段冲突。 */
    public int voiceSwapCooldown;
    // Sync: swap map + pitch map as UUID-string pairs
    public Map<UUID, UUID> skinSwaps = new HashMap<>();
    public Map<UUID, Float> voicePitches = new HashMap<>();

    // 用于检测激活状态边界，避免每秒同步
    private boolean prevMasqueradeActive = false;

    public EmbalmerRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        masqueradeActive = false;
        masqueradeTicksLeft = 0;
        voiceSwapCooldown = 0;
        skinSwaps.clear();
        voicePitches.clear();
        prevMasqueradeActive = false;
        // 变身派对冷却改由统一技能系统管理：开局自带150秒冷却，
        // 由技能自带 HUD 展示（此时 SREAbilityPlayerComponent 已初始化过，顺序见 SRERole#onInit）。
        if (!player.level().isClientSide()) {
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
            if (ability != null) {
                ability.setSkillCooldown(MASQUERADE_SKILL_ID, MASQUERADE_INITIAL_COOLDOWN);
            }
        }
        sync();
    }

    @Override
    public void clear() {
        init();
    }


    public boolean isActive() {
        if (player == null || player.level().isClientSide())
            return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.EMBALMER);
    }

    @Override
    public void serverTick() {
        if (!isActive())
            return;
        boolean changed = false;
        if (voiceSwapCooldown > 0) {
            voiceSwapCooldown--;
            if (voiceSwapCooldown == 0)
                changed = true;
        }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
            if (masqueradeTicksLeft <= 0) {
                masqueradeActive = false;
                skinSwaps.clear();
                voicePitches.clear();
                // 发送清除数据包到所有客户端，重置皮肤和音调
                if (player instanceof ServerPlayer sp) {
                    for (ServerPlayer p : sp.serverLevel().getPlayers(p2 -> true)) {
                        ServerPlayNetworking.send(p, org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket.clear());
                    }
                }
                changed = true; // 激活结束边界
            }
        }
        // 仅在状态边界同步（激活开始/结束）；变身派对冷却由统一技能系统自行同步。
        if (masqueradeActive != prevMasqueradeActive)
            changed = true;
        if (changed)
            sync();
        prevMasqueradeActive = masqueradeActive;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("masqueradeActive", masqueradeActive);
        tag.putInt("masqueradeTicksLeft", masqueradeTicksLeft);
        tag.putInt("voiceSwapCooldown", voiceSwapCooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        masqueradeActive = tag.getBoolean("masqueradeActive");
        masqueradeTicksLeft = tag.getInt("masqueradeTicksLeft");
        voiceSwapCooldown = tag.getInt("voiceSwapCooldown");
    }

    /**
     * Get voice pitch for a player during active masquerade. Returns 1.0F if not
     * active or not found.
     */
    public static float getVoicePitch(Player player) {
        if (player == null || player.level().isClientSide())
            return 1.0F;
        var comp = RoleData.getNullable(EmbalmerRoleData.class, player);
        if (!RoleData.isAttached(comp) || !comp.masqueradeActive)
            return 1.0F;
        return comp.voicePitches.getOrDefault(player.getUUID(), 1.0F);
    }



    @Override
    public void clientTick() {
        if (voiceSwapCooldown > 0) {
            voiceSwapCooldown--;
        }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
        }
    }

    /**
     * 技能一「变身派对」：全场玩家互相交换皮肤并随机改变音调。
     *
     * <p>原实现位于 {@code ModPacketsReciever} 的 {@code EmbalmerC2SPacket} 接收器，
     * 为了同时支持「统一技能系统（G 键 / V 键切换）」抽出为静态方法复用。
     *
     * <p>冷却由统一技能系统管理：G 键入口会在调用本方法前检查就绪状态，
     * 成功发动后由 {@code markSkillUsed} 置上 150 秒冷却并同步给技能 HUD。
     *
     * @return 是否成功发动（冷却中或人数不足时返回 false）
     */
    public static boolean triggerMasquerade(ServerPlayer player) {
        var comp = RoleData.getNullable(EmbalmerRoleData.class, player);
        if (comp == null) {
            return false;
        }
        // 防御其他调用路径（如遗留的 EmbalmerC2SPacket 接收器）：冷却中就拦下并提示
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability == null) {
            return false;
        }
        if (!ability.canUseSkill(MASQUERADE_SKILL_ID)) {
            ability.showUnavailableMessage(MASQUERADE_SKILL_ID);
            return false;
        }

        java.util.List<ServerPlayer> players = player.serverLevel().getPlayers(
                p2 -> !p2.isSpectator()
                        && !org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(p2.level())
                                .isModifier(p2, pro.fazeclan.river.stupid_express.constants.SEModifiers.JEB_));
        if (players.size() < 2) {
            player.displayClientMessage(Component.literal("Need at least 2 players."), true);
            return false;
        }

        java.util.Map<UUID, UUID> swaps = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, Float> pitches = new java.util.HashMap<>();
        java.util.List<UUID> uuids = new java.util.ArrayList<>();
        for (ServerPlayer p : players) {
            uuids.add(p.getUUID());
        }
        java.util.Collections.shuffle(uuids, new java.util.Random());
        for (int i = 0; i < uuids.size(); i++) {
            UUID from = players.get(i).getUUID();
            UUID to = uuids.get(i);
            if (from.equals(to)) {
                to = uuids.get((i + 1) % uuids.size());
            }
            swaps.put(from, to);
            pitches.put(from, 0.7F + (new java.util.Random().nextFloat() * 0.6F));
        }
        comp.skinSwaps = swaps;
        comp.voicePitches = pitches;
        comp.masqueradeActive = true;
        comp.masqueradeTicksLeft = MASQUERADE_DURATION;
        comp.sync();

        // 全场播放音效（遍历所有玩家，绕过距离衰减）
        for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
            p.playNotifySound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.MASTER, 1.0F, 1.0F);
        }
        // 广播皮肤交换数据给所有玩家
        org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket swapPacket =
                new org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket(swaps, pitches, MASQUERADE_DURATION);
        for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> true)) {
            ServerPlayNetworking.send(p, swapPacket);
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.embalmer.activated").withStyle(ChatFormatting.DARK_PURPLE),
                true);
        return true;
    }
}
