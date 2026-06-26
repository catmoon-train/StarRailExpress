package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import java.util.List;

/**
 * 格罗赛尔游记。
 * <p>
 * - 右键蓄力 1 秒（可配置），蓄满后将瞄准的目标玩家放逐进游记（配置坐标，默认 -100/50/21000）。
 * - 放逐成功后进入 75 秒冷却（可配置）。
 * - 放逐瞬间在双方位置播放粒子与声音。
 * </p>
 * 游记内规则（无法攻击 / 受伤、无法使用技能 / 物品、死亡改判、信标回归）由
 * {@link GroselleJourneyManager} 统一处理。
 */
public class GrosellTravelogItem extends Item {

    /** 蓄力动画的最长持有时间（远大于实际蓄力时长）。 */
    private static final int MAX_USE_DURATION = 72000;

    public GrosellTravelogItem(Properties settings) {
        super(settings);
    }

    private static int chargeTicks() {
        double seconds = NoellesRolesConfig.HANDLER.instance().grosellTravelogChargeSeconds;
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            if (!world.isClientSide) {
                player.displayClientMessage(Component
                        .translatable("item.noellesroles.grosell_travelog.on_cooldown")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 开始蓄力。
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return MAX_USE_DURATION;
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (!(world instanceof ServerLevel level) || !(user instanceof ServerPlayer player)) {
            return;
        }
        int elapsed = getUseDuration(stack, user) - remainingUseDuration;
        if (elapsed > chargeTicks()) {
            return;
        }
        // 蓄力中：在书前方冒出附魔光环粒子。
        Vec3 look = player.getViewVector(1.0f).normalize();
        Vec3 tip = player.getEyePosition().add(look.scale(0.7)).add(0, -0.2, 0);
        level.sendParticles(ParticleTypes.ENCHANT, tip.x, tip.y, tip.z, 4, 0.15, 0.15, 0.15, 0.02);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (world.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        int elapsed = getUseDuration(stack, user) - remainingUseTicks;
        if (elapsed < chargeTicks()) {
            // 蓄力不足，取消放逐。
            player.displayClientMessage(Component
                    .translatable("item.noellesroles.grosell_travelog.not_charged")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        ServerPlayer target = raycastTarget(player);
        if (target == null) {
            player.displayClientMessage(Component
                    .translatable("item.noellesroles.grosell_travelog.no_target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        if (!GroselleJourneyManager.banish(player, target)) {
            player.displayClientMessage(Component
                    .translatable("item.noellesroles.grosell_travelog.no_target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // 放逐成功：本人提示 + 冷却。
        player.displayClientMessage(Component
                .translatable("item.noellesroles.grosell_travelog.banish_success", target.getName())
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0f, 0.8f);

        int cooldown = NoellesRolesConfig.HANDLER.instance().grosellTravelogCooldownSeconds * 20;
        player.getCooldowns().addCooldown(this, cooldown);
    }

    /** 沿视线投射，返回瞄准的存活幸存玩家（被方块遮挡或超出距离则为 null）。 */
    private static ServerPlayer raycastTarget(ServerPlayer player) {
        double range = NoellesRolesConfig.HANDLER.instance().grosellTravelogRange;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
                e -> e instanceof Player p && p != player && GameUtils.isPlayerAliveAndSurvival(p),
                range);
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof ServerPlayer target) {
                return target;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        tooltip.add(Component.translatable("item.noellesroles.grosell_travelog.tooltip.use")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.noellesroles.grosell_travelog.tooltip.rules")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.grosell_travelog.tooltip.return")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.grosell_travelog.tooltip.cooldown",
                config.grosellTravelogCooldownSeconds).withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
