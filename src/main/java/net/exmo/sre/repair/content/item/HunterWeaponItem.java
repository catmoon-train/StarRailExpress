package net.exmo.sre.repair.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
import net.minecraft.world.phys.*;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.HunterAttackProfile;
import net.exmo.sre.repair.state.RepairModeState;
import net.exmo.sre.repair.network.RepairCombatFeedbackS2CPacket;

import java.util.List;

public class HunterWeaponItem extends Item {
    private final String weaponId;
    // 三把武器各自的冷却时间（tick）
    private final int weaponCooldownTicks;
    // 钩镰武器标志
    private final boolean isHook;

    public HunterWeaponItem(Properties properties) {
        this("blade", properties);
    }

    public HunterWeaponItem(String weaponId, Properties properties) {
        super(properties);
        this.weaponId = weaponId;
        this.isHook = "hook".equals(weaponId);
        // 不同武器有不同的基础冷却
        this.weaponCooldownTicks = switch (weaponId) {
            case "hammer" -> 64;   // 重锤：长冷却
            case "hook" -> 75;    // 钩镰：长冷却（独立）
            default -> 20;         // 利刃：短冷却
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            var hunterComponent = ModComponents.REPAIR_ROLES.get(serverPlayer);
            // 使用共享冷却池检查，并检查攻击命中后的20秒冷却
            if (!RepairModeState.canUseHunterUtility(serverPlayer)
                    || hunterComponent.carrying != null
                    || hunterComponent.hunterWeaponCooldownEndTick > level.getGameTime()) {
                return InteractionResultHolder.fail(stack);
            }
        }

        if (!(player instanceof ServerPlayer hunter) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.consume(stack);
        }
        // 原版物品冷却必须与共享冷却池一致：物品冷却更长时 ServerPlayerGameMode.useItem
        // 会在调用 use() 之前直接 PASS，玩家右键完全没有反馈（HUD 读的是共享池，会显示"可用"）。
        player.getCooldowns().addCooldown(stack.getItem(), weaponCooldownTicks);
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        HunterAttackProfile profile = HunterAttackProfile.of(hunterComponent.activeRole,
                hunterComponent.activeAttackPlugin, weaponId);
        // 写入共享冷却池，使用当前武器的冷却时间
        hunterComponent.hunterWeaponCooldownEndTick = serverLevel.getGameTime() + weaponCooldownTicks;
        hunterComponent.sync();
        // 前摇期间猎人原地锁定
        hunter.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                profile.windupTicks() + 4, 9, false, false, true));
        hunter.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.JUMP,
                profile.windupTicks() + 4, -5, false, false, true)); // 禁跳
        level.playSound(null, hunter.blockPosition(), windupSound(), SoundSource.PLAYERS, 0.8F, 0.8F);
        // 广播攻击反馈，只让攻击者播放动画
        RepairModeState.broadcastCombatFeedback(serverLevel, RepairCombatFeedbackS2CPacket.ATTACK, hunter,
                hunter.getX(), hunter.getY() + 1.0D, hunter.getZ(), 24.0D, weaponId);
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(profile.windupTicks(), () ->
                executeDelayedAttack(hunter, stack, profile)));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private void executeDelayedAttack(ServerPlayer hunter, ItemStack stack, HunterAttackProfile profile) {
        if (!(hunter.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if (!RepairModeState.canUseHunterUtility(hunter) || hunterComponent.carrying != null) {
            // 取消攻击时清理减速/禁跳效果
            hunter.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            hunter.removeEffect(MobEffects.JUMP);
            return;
        }
        hunter.swing(hunter.getUsedItemHand(), true);
        serverLevel.playSound(null, hunter.blockPosition(), releaseSound(), SoundSource.PLAYERS, 0.95F, 0.85F);
        // 钩镰使用更窄更长的判定范围
        ServerPlayer target = findTarget(hunter, serverLevel, profile.reach());
        String plugin = hunterComponent.activeAttackPlugin;
        hunterComponent.activeAttackPlugin = "";
        hunterComponent.sync();
        if (target == null) {
            serverLevel.playSound(null, hunter.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS,
                    0.8F, 0.7F);
            // 释放后清理减速/禁跳效果
            hunter.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            hunter.removeEffect(MobEffects.JUMP);
            return;
        }
        boolean hit = profile.applyHit(hunter, target);
        if ("hammer".equals(weaponId)) {
            for (ServerPlayer splash : serverLevel.players()) {
                if (splash != target && splash.distanceToSqr(target) <= 2.4D * 2.4D && isValidVictim(hunter, splash)) {
                    profile.applyHit(hunter, splash);
                }
            }
        } else if ("hook".equals(weaponId)) {
            Vec3 pull = hunter.position().subtract(target.position()).normalize();
            target.push(pull.x * 1.05D, 0.10D, pull.z * 1.05D);
            target.hurtMarked = true;
            // 钩镰增加链条粒子
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 0.9D, target.getZ(),
                    18, 0.35D, 0.35D, 0.35D, 0.02D);
            // 拉拽反馈和标记音效
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS,
                    0.9F, 1.25F);
        }
        if (hit && !hunter.getAbilities().instabuild) {
            stack.hurtAndBreak(1, hunter, LivingEntity.getSlotForHand(hunter.getUsedItemHand()));
        }
        // 释放后清理减速/禁跳效果
        hunter.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        hunter.removeEffect(MobEffects.JUMP);
    }

    private net.minecraft.sounds.SoundEvent windupSound() {
        return switch (weaponId) {
            case "hammer" -> SoundEvents.ANVIL_PLACE;
            case "hook" -> SoundEvents.CHAIN_PLACE;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
    }

    private net.minecraft.sounds.SoundEvent releaseSound() {
        return switch (weaponId) {
            case "hammer" -> SoundEvents.ANVIL_LAND;
            case "hook" -> SoundEvents.CHAIN_BREAK;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
    }

    private ServerPlayer findTarget(ServerPlayer hunter, ServerLevel level, double reach) {
        Vec3 eye = hunter.getEyePosition();
        Vec3 look = hunter.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        // 钩镰使用更窄的判定范围（更窄更长）
        double inflate = isHook ? 0.32D : 0.85D;
        AABB box = hunter.getBoundingBox().expandTowards(look.scale(reach)).inflate(inflate, 0.45D, inflate);
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
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        // 显示冷却时间
        tooltip.add(Component.translatable("item.noellesroles.hunter_weapon_cooldown", weaponCooldownTicks / 20)
                .withStyle(ChatFormatting.YELLOW));
    }
}
