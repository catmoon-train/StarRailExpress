package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.innocence.tailor.TailorClothesHandler;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 裁缝（平民阵营）的职业数据与「编织」规则实现。
 *
 * <ul>
 * <li>用缝纫针右键一名玩家开始编织，<b>长按 5 秒</b>为其编织一件「裁缝的衣服」；</li>
 * <li>编织期间目标必须保持存活、在 {@link #WEAVE_RANGE} 格内、可见且大致被瞄准，否则中断；</li>
 * <li>完成时消耗裁缝快捷栏里的一根毛线球；衣服优先给目标穿上（胸甲栏为空），
 * 否则进入目标空余的快捷栏；两者都不可行则编织失败（不消耗毛线球）。</li>
 * </ul>
 */
public class TailorRoleData extends SimpleRoleData {

    /** 编织一件衣服需要长按右键的 tick 数（5 秒）。 */
    public static final int WEAVE_TICKS = 100;
    /** 编织允许的最大距离（格）。 */
    public static final double WEAVE_RANGE = 4.5D;
    /** 编织允许的最大距离平方。 */
    public static final double WEAVE_RANGE_SQR = WEAVE_RANGE * WEAVE_RANGE;
    /** 面向容差（视线与指向目标向量的点积，约 53°），低于该值视为不再瞄准。 */
    public static final double AIM_DOT = 0.6D;
    /** 编织特效的发出间隔（tick）。 */
    private static final int PARTICLE_INTERVAL = 10;

    /** 正在编织的目标；{@code null} 表示当前没有在编织。 */
    public UUID weaveTargetId;

    public TailorRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        return serverPlayer == this.player;
    }

    // ==================== 编织流程 ====================

    /**
     * 开始（或切换目标重新开始）对目标编织。
     *
     * @param tailor 裁缝
     * @param target 被编织的玩家
     * @param hand   持有缝纫针的手（切换目标时用于重置使用计时）
     * @return 是否成功开始（false = 调用方应结束交互）
     */
    public boolean beginWeave(ServerPlayer tailor, ServerPlayer target, InteractionHand hand) {
        if (tailor == target || !GameUtils.isPlayerAliveAndSurvival(tailor)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        if (tailor.distanceToSqr(target) > WEAVE_RANGE_SQR) {
            tailor.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.weave_too_far").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!TailorClothesHandler.canPlaceOn(target)) {
            tailor.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.no_space").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (TailorClothesHandler.findYarnBallInHotbar(tailor).isEmpty()) {
            // 软提示：5 秒后才会真正检查并消耗毛线球
            tailor.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.no_yarn").withStyle(ChatFormatting.GRAY),
                    true);
        }
        if (!target.getUUID().equals(this.weaveTargetId)) {
            // 切换目标：结束旧编织（releaseUsing 会清掉旧目标）再重新计时
            tailor.stopUsingItem();
            this.weaveTargetId = target.getUUID();
            tailor.startUsingItem(hand);
        }
        sync();
        return true;
    }

    /** 每 tick 推进编织（由缝纫针 {@code onUseTick} 在服务端调用）。 */
    public void tickWeave(ServerPlayer tailor, int elapsedTicks) {
        if (this.weaveTargetId == null) {
            return;
        }
        ServerPlayer target = tailor.server.getPlayerList().getPlayer(this.weaveTargetId);
        if (!isWeaveValid(tailor, target)) {
            this.weaveTargetId = null;
            sync();
            tailor.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.weave_interrupted").withStyle(ChatFormatting.RED),
                    true);
            tailor.stopUsingItem();
            return;
        }
        if (elapsedTicks % PARTICLE_INTERVAL == 0) {
            spawnThreadParticles(tailor, target);
        }
        if (elapsedTicks >= WEAVE_TICKS) {
            completeWeave(tailor, target);
            tailor.stopUsingItem();
        }
    }

    /** 中断当前编织（松手 / 切换目标 / 目标失效），不产生消耗。 */
    public void cancelWeave() {
        if (this.weaveTargetId != null) {
            this.weaveTargetId = null;
            sync();
        }
    }

    /** 编织完成：消耗一根毛线球，把「裁缝的衣服」给目标穿上。 */
    private void completeWeave(ServerPlayer tailor, ServerPlayer target) {
        this.weaveTargetId = null;
        sync();
        if (!TailorClothesHandler.canPlaceOn(target)) {
            tailor.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.no_space").withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        ItemStack yarn = TailorClothesHandler.findYarnBallInHotbar(tailor);
        if (yarn.isEmpty()) {
            tailor.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.no_yarn").withStyle(ChatFormatting.RED),
                    true);
            return;
        }
        yarn.shrink(1);
        ItemStack clothes = TailorClothesHandler.createTailorClothes();
        TailorClothesHandler.placeOn(target, clothes);

        tailor.displayClientMessage(
                Component.translatable("message.noellesroles.tailor.weave_done_self", target.getDisplayName())
                        .withStyle(ChatFormatting.GREEN),
                true);
        target.displayClientMessage(
                Component.translatable("message.noellesroles.tailor.weave_done_target", tailor.getDisplayName())
                        .withStyle(ChatFormatting.AQUA),
                true);
        if (tailor.level() instanceof ServerLevel level) {
            level.playSound(null, target.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        // 回放记录：裁缝为某人编织了一件裁缝的衣服
        SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.event.tailor.weave",
                GameReplayUtils.getReplayPlayerDisplayText(tailor, true),
                GameReplayUtils.getReplayPlayerDisplayText(target, true)));
    }

    // ==================== 工具方法 ====================

    /** 编织是否仍然有效（目标存活、在范围内、可见且被瞄准）。 */
    private static boolean isWeaveValid(ServerPlayer tailor, ServerPlayer target) {
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(tailor)
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        if (tailor.distanceToSqr(target) > WEAVE_RANGE_SQR || !tailor.hasLineOfSight(target)) {
            return false;
        }
        Vec3 look = tailor.getViewVector(1.0F).normalize();
        Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(tailor.getEyePosition()).normalize();
        return look.dot(toTarget) >= AIM_DOT;
    }

    /** 在裁缝与目标之间冒出「线头」粒子（单个发包）。 */
    private static void spawnThreadParticles(ServerPlayer tailor, ServerPlayer target) {
        if (!(tailor.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = tailor.getEyePosition();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        Vec3 mid = from.add(to.subtract(from).scale(0.5D));
        level.sendParticles(ParticleTypes.END_ROD,
                mid.x, mid.y, mid.z, 2, 0.15D, 0.15D, 0.15D, 0.01D);
    }

    // ==================== 同步 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        if (this.weaveTargetId != null) {
            tag.putUUID("weaveTarget", this.weaveTargetId);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.weaveTargetId = tag.hasUUID("weaveTarget") ? tag.getUUID("weaveTarget") : null;
    }
}
