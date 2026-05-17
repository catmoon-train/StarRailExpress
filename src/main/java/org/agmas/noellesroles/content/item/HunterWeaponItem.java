package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.HunterAttackProfile;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket;

import java.util.List;

public class HunterWeaponItem extends Item {
    public HunterWeaponItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!RepairModeState.canUseHunterUtility(serverPlayer)
                    || ModComponents.REPAIR_ROLES.get(serverPlayer).carrying != null
                    || player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof ServerPlayer hunter) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        HunterAttackProfile profile = HunterAttackProfile.of(hunterComponent.activeRole, hunterComponent.activeAttackPlugin);
        int charged = getUseDuration(stack, hunter) - timeLeft;
        if (charged < profile.windupTicks() || !RepairModeState.canUseHunterUtility(hunter)
                || hunterComponent.carrying != null
                || hunter.getCooldowns().isOnCooldown(this)) {
            return;
        }

        hunter.swing(hunter.getUsedItemHand(), true);
        hunter.getCooldowns().addCooldown(this, profile.cooldownTicks());
        level.playSound(null, hunter.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.85F);
        RepairModeState.broadcastCombatFeedback(serverLevel, RepairCombatFeedbackS2CPacket.ATTACK, hunter,
                hunter.getX(), hunter.getY() + 1.0D, hunter.getZ(), 24.0D);

        ServerPlayer target = findTarget(hunter, serverLevel, profile.reach());
        hunterComponent.activeAttackPlugin = "";
        hunterComponent.sync();
        if (target == null) {
            level.playSound(null, hunter.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 0.7F);
            return;
        }

        if (profile.applyHit(hunter, target)) {
            if (!hunter.getAbilities().instabuild) {
                stack.hurtAndBreak(1, hunter, LivingEntity.getSlotForHand(hunter.getUsedItemHand()));
            }
        }
    }

    private ServerPlayer findTarget(ServerPlayer hunter, ServerLevel level, double reach) {
        Vec3 eye = hunter.getEyePosition();
        Vec3 look = hunter.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = hunter.getBoundingBox().expandTowards(look.scale(reach)).inflate(0.85D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(hunter, eye, end, box, this::isPotentialTarget, reach * reach);
        if (hit == null || !(hit.getEntity() instanceof ServerPlayer target)) {
            return null;
        }
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, hunter));
        if (blockHit.getType() == HitResult.Type.BLOCK
                && eye.distanceToSqr(blockHit.getLocation()) + 0.04D < eye.distanceToSqr(hit.getLocation())) {
            return null;
        }
        return isValidVictim(hunter, target) ? target : null;
    }

    private boolean isPotentialTarget(Entity entity) {
        return entity instanceof ServerPlayer player && !player.isSpectator() && player.isPickable();
    }

    private boolean isValidVictim(ServerPlayer hunter, ServerPlayer target) {
        if (hunter == target || RepairModeState.isHunter(target) || !RepairModeState.isNonHunterRepairPlayer(target)) {
            return false;
        }
        var targetComponent = ModComponents.REPAIR_ROLES.get(target);
        return !targetComponent.downed && targetComponent.carriedBy == null && !targetComponent.trialStand.present()
                && !target.getTags().contains(RepairModeState.ESCAPED_TAG);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.hunter_weapon.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
