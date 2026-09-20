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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 占卜家（Diviner）数据。
 *
 * <p>
 * 玩法：背包界面里点选一名玩家作为「占卜目标」；随后用晶球右键一具尸体开始 3 秒占卜，
 * 完成后传送到目标的身边（目标存活→其位置；目标死亡→其尸体位置；死亡且无尸体→失败，不进入冷却也不破坏晶球）。
 * 传送后 10 秒内再次右键晶球可传送回原位置并使晶球进入 50 秒冷却；超过 10 秒未回传则自动进入冷却。
 * 每具尸体只能被同一名占卜家占卜一次。占卜成功后有 50% 概率破坏晶球（保留原有行为）。
 */
public class DivinerRoleData extends SimpleRoleData {

    private static final int SECOND = 20;

    /** 占卜施法时长（秒）。 */
    private static final int CHANNEL_SECONDS = 3;
    private static final int CHANNEL_TICKS = CHANNEL_SECONDS * SECOND;

    /** 传送后可回传原位置的时间窗口（秒）。 */
    private static final int RETURN_WINDOW_SECONDS = 10;
    private static final int RETURN_WINDOW_TICKS = RETURN_WINDOW_SECONDS * SECOND;

    /** 晶球破坏概率（保留原有行为）。 */
    private static final float BREAK_CHANCE = 0.5f;

    /** 晶球商店售价（金币，写死）。 */
    public static final int CRYSTAL_BALL_PRICE = 150;

    /** 晶球冷却时长（秒，写死）。 */
    public static final int COOLDOWN_SECONDS = 50;

    // ==================== 状态字段 ====================

    /** 背包界面里选中的占卜目标玩家 UUID。 */
    private UUID target;

    /** 已占卜过的尸体实体 UUID（每具尸体只能占卜一次）。 */
    private final Set<UUID> divinedCorpses = new HashSet<>();

    /** 是否已发放开局晶球。 */
    private boolean gaveItem = false;

    // --- 施法状态 ---
    private boolean divining = false;
    private int divineTicks = 0;
    /** 正在占卜的尸体实体 UUID。 */
    private UUID divineBodyId;

    // --- 回传窗口 ---
    private boolean awaitingReturn = false;
    private int returnWindowTicks = 0;
    /** 传送前的位置（回传目标）。 */
    private Vec3 returnPos;
    private ResourceKey<Level> returnDim;

    public DivinerRoleData(RoleDataContext context) {
        super(context);
    }

    // ==================== 生命周期 ====================

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    @Override
    public void init() {
        this.target = null;
        this.divinedCorpses.clear();
        this.gaveItem = false;
        this.divining = false;
        this.divineTicks = 0;
        this.divineBodyId = null;
        this.awaitingReturn = false;
        this.returnWindowTicks = 0;
        this.returnPos = null;
        this.returnDim = null;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    // ==================== 选中目标 ====================

    /** 由选人界面（C2S 包）调用：设置占卜目标。 */
    public void setTarget(UUID target) {
        this.target = target;
        sync();
    }

    public UUID getTarget() {
        return this.target;
    }

    public boolean isAwaitingReturn() {
        return this.awaitingReturn;
    }

    // ==================== 右键入口（由 CrystalBallItem 调用） ====================

    /**
     * 右键一具尸体开始占卜。
     *
     * @return 是否成功开始（true 才在调用方记回放）
     */
    public boolean startDivination(ServerPlayer sp, Entity targetEntity) {
        // 冷却中
        if (sp.getCooldowns().isOnCooldown(ModItems.CRYSTAL_BALL)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.cooldown",
                    (getCooldownSec(sp) + 1)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (divining || awaitingReturn) {
            return false;
        }

        // 未选中目标玩家
        if (target == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.no_target")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        // 必须对着尸体
        if (!(targetEntity instanceof PlayerBodyEntity body)
                || DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.no_corpse")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        UUID bodyId = body.getUUID();
        if (divinedCorpses.contains(bodyId)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.already")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        this.divining = true;
        this.divineTicks = 0;
        this.divineBodyId = bodyId;

        playChannelStartFx(sp);
        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.channel_start",
                CHANNEL_SECONDS).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** 回传窗口内再次右键晶球：传送回原位置并使晶球进入冷却。 */
    public boolean returnToOrigin(ServerPlayer sp) {
        if (!awaitingReturn) {
            return false;
        }
        this.awaitingReturn = false;
        this.returnWindowTicks = 0;

        if (returnPos != null) {
            ServerLevel level = returnDim != null && sp.getServer() != null
                    ? sp.getServer().getLevel(returnDim)
                    : sp.serverLevel();
            if (level != null) {
                teleport(sp, level, returnPos);
            }
        }
        setCooldown(sp);
        playCompleteFx(sp);
        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.returned")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    // ==================== serverTick ====================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(sp.level());
        if (!gw.isRunning() || !gw.isRole(sp, ModRoles.DIVINER)) {
            return;
        }

        // 开局发放晶球
        if (!gaveItem && GameUtils.isPlayerAliveAndSurvival(sp)) {
            sp.addItem(ModItems.CRYSTAL_BALL.getDefaultInstance().copy());
            gaveItem = true;
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.intro",
                    COOLDOWN_SECONDS, CRYSTAL_BALL_PRICE), false);
        }

        tickDivination(sp);
        tickReturnWindow(sp);
    }

    // ==================== 施法 / 传送 ====================

    private void tickDivination(ServerPlayer sp) {
        if (!divining) {
            return;
        }
        divineTicks++;
        if (divineTicks % SECOND == 0) {
            playChannelTickFx(sp);
        }
        if (divineTicks >= CHANNEL_TICKS) {
            completeDivination(sp);
        }
    }

    /** 施法完成：解析目标位置并传送；失败（找不到目标位置）时不冷却也不破坏晶球。 */
    private void completeDivination(ServerPlayer sp) {
        this.divining = false;
        this.divineTicks = 0;
        UUID bodyId = this.divineBodyId;
        this.divineBodyId = null;

        // 施法用的尸体可能已消失
        if (!(findBodyById(sp, bodyId) instanceof PlayerBodyEntity)) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.corpse_gone")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 解析目标位置：存活→玩家位置；死亡/离线→尸体位置；都没有→失败
        ServerLevel destLevel = null;
        Vec3 destPos = null;
        Component targetName = resolveTargetName(sp);

        ServerPlayer targetPlayer = target == null || sp.getServer() == null
                ? null
                : sp.getServer().getPlayerList().getPlayer(target);
        if (targetPlayer != null && GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
            destLevel = targetPlayer.serverLevel();
            destPos = targetPlayer.position();
        } else {
            PlayerBodyEntity corpse = findCorpseByPlayer(sp, target);
            if (corpse != null && corpse.level() instanceof ServerLevel corpseLevel) {
                destLevel = corpseLevel;
                destPos = corpse.position();
            }
        }

        if (destLevel == null || destPos == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.target_no_body")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        // 该尸体标记为已占卜
        if (bodyId != null) {
            divinedCorpses.add(bodyId);
        }

        // 记录回传点
        this.returnPos = sp.position();
        this.returnDim = sp.serverLevel().dimension();

        teleport(sp, destLevel, destPos);
        playCompleteFx(sp);
        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.teleported", targetName)
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);

        // 保留原有行为：占卜成功后有概率破坏晶球
        breakCrystalBall(sp);

        // 开启回传窗口
        this.awaitingReturn = true;
        this.returnWindowTicks = 0;
    }

    private void tickReturnWindow(ServerPlayer sp) {
        if (!awaitingReturn) {
            return;
        }
        returnWindowTicks++;
        if (returnWindowTicks >= RETURN_WINDOW_TICKS) {
            awaitingReturn = false;
            returnWindowTicks = 0;
            setCooldown(sp);
            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.return_expired")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ==================== 辅助方法 ====================

    private static void teleport(ServerPlayer sp, ServerLevel level, Vec3 pos) {
        sp.teleportTo(level, pos.x, pos.y, pos.z, sp.getYRot(), sp.getXRot());
    }

    /** 根据实体 UUID 查找尸体（施法结束时重新获取）。 */
    private Entity findBodyById(ServerPlayer sp, UUID bodyId) {
        if (bodyId != null && sp.level() instanceof ServerLevel sl) {
            return sl.getEntity(bodyId);
        }
        return null;
    }

    /** 按「死者玩家 UUID」在全部维度里查找其尸体。 */
    private static PlayerBodyEntity findCorpseByPlayer(ServerPlayer sp, UUID playerUuid) {
        if (playerUuid == null || sp.getServer() == null) {
            return null;
        }
        for (ServerLevel level : sp.getServer().getAllLevels()) {
            AABB allWorld = new AABB(-30000000, level.getMinBuildHeight(), -30000000,
                    30000000, level.getMaxBuildHeight(), 30000000);
            for (PlayerBodyEntity body : level.getEntitiesOfClass(PlayerBodyEntity.class, allWorld)) {
                if (playerUuid.equals(body.getPlayerUuid())
                        && !DoomedSinnerBodyEntity.isDoomedSinnerBody(body)) {
                    return body;
                }
            }
        }
        return null;
    }

    /** 目标玩家的显示名（离线 / 找不到时回退占位文本）。 */
    private Component resolveTargetName(ServerPlayer sp) {
        if (target != null && sp.getServer() != null) {
            ServerPlayer t = sp.getServer().getPlayerList().getPlayer(target);
            if (t != null) {
                return t.getDisplayName();
            }
        }
        return Component.translatable("message.noellesroles.diviner.unknown");
    }

    /** 50% 概率破坏晶球（保留原有行为）。 */
    private void breakCrystalBall(ServerPlayer sp) {
        if (sp.level().random.nextFloat() < BREAK_CHANCE) {
            ItemStack held = sp.getMainHandItem();
            if (held.is(ModItems.CRYSTAL_BALL)) {
                held.shrink(1);
                sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.break")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** 设置晶球冷却。 */
    private void setCooldown(ServerPlayer sp) {
        sp.getCooldowns().addCooldown(ModItems.CRYSTAL_BALL, GameConstants.getInTicks(0, COOLDOWN_SECONDS));
    }

    /** 获取冷却剩余秒数。 */
    private int getCooldownSec(ServerPlayer sp) {
        ItemCooldowns cooldowns = sp.getCooldowns();
        ItemCooldowns.CooldownInstance cd = cooldowns.cooldowns.get(ModItems.CRYSTAL_BALL);
        if (cd == null) {
            return 0;
        }
        return Math.max(0, (cd.endTime - cooldowns.tickCount + 19) / 20);
    }

    // ==================== 特效 ====================

    private void playChannelStartFx(ServerPlayer sp) {
        // 占卜粒子与音效仅对占卜师本人显示 / 播放
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(sp, ParticleTypes.SOUL_FIRE_FLAME, true,
                    sp.getX(), sp.getY() + 1.5, sp.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            sp.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.9f);
        }
    }

    private void playChannelTickFx(ServerPlayer sp) {
        // 占卜粒子仅对占卜师本人显示
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(sp, ParticleTypes.ENCHANT, true,
                    sp.getX(), sp.getY() + 1.5, sp.getZ(), 12, 0.3, 0.6, 0.3, 0.1);
        }
    }

    private void playCompleteFx(ServerPlayer sp) {
        // 占卜粒子与音效仅对占卜师本人显示 / 播放
        if (sp.level() instanceof ServerLevel sl) {
            sl.sendParticles(sp, ParticleTypes.ENCHANT, true,
                    sp.getX(), sp.getY() + 1.2, sp.getZ(), 24, 0.4, 0.6, 0.4, 0.2);
            sp.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.target != null) {
            tag.putUUID("target", this.target);
        }

        net.minecraft.nbt.ListTag divinedList = new net.minecraft.nbt.ListTag();
        for (UUID id : this.divinedCorpses) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            divinedList.add(entry);
        }
        tag.put("divinedCorpses", divinedList);

        tag.putBoolean("gaveItem", this.gaveItem);
        tag.putBoolean("awaitingReturn", this.awaitingReturn);
        tag.putInt("returnWindowTicks", this.returnWindowTicks);
        if (this.returnPos != null) {
            tag.putDouble("returnX", this.returnPos.x);
            tag.putDouble("returnY", this.returnPos.y);
            tag.putDouble("returnZ", this.returnPos.z);
        }
        if (this.returnDim != null) {
            tag.putString("returnDim", this.returnDim.location().toString());
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.hasUUID("target") ? tag.getUUID("target") : null;

        this.divinedCorpses.clear();
        if (tag.contains("divinedCorpses")) {
            net.minecraft.nbt.ListTag divinedList = tag.getList("divinedCorpses", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < divinedList.size(); i++) {
                CompoundTag entry = divinedList.getCompound(i);
                if (entry.hasUUID("id")) {
                    this.divinedCorpses.add(entry.getUUID("id"));
                }
            }
        }

        this.gaveItem = tag.contains("gaveItem") && tag.getBoolean("gaveItem");
        this.awaitingReturn = tag.contains("awaitingReturn") && tag.getBoolean("awaitingReturn");
        this.returnWindowTicks = tag.contains("returnWindowTicks") ? tag.getInt("returnWindowTicks") : 0;
        if (tag.contains("returnX")) {
            this.returnPos = new Vec3(tag.getDouble("returnX"), tag.getDouble("returnY"),
                    tag.getDouble("returnZ"));
        }
        if (tag.contains("returnDim")) {
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation
                    .tryParse(tag.getString("returnDim"));
            this.returnDim = loc == null ? null : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, loc);
        }
    }
}
