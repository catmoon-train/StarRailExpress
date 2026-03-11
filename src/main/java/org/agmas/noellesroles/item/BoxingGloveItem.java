package org.agmas.noellesroles.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class BoxingGloveItem extends Item {
    public BoxingGloveItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        player.startUsingItem(usedHand);
        // 蓄力时没有声音
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        if (!(livingEntity instanceof Player)) {
            return;
        }
        Player player = (Player) livingEntity;

        int duration = this.getUseDuration(stack, livingEntity) - timeCharged;
        // 蓄力时间为0.4秒 (8 ticks)
        if (duration < 8) {
            return;
        }

        if (!level.isClientSide) {
            // 射线检测
            double reachDistance = 3.5;
            Vec3 eyePosition = player.getEyePosition();
            Vec3 viewVector = player.getViewVector(1.0F);
            Vec3 reachVector = eyePosition.add(viewVector.x * reachDistance, viewVector.y * reachDistance, viewVector.z * reachDistance);
            AABB aabb = player.getBoundingBox().expandTowards(viewVector.scale(reachDistance)).inflate(1.0D);
            
            EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player, 
                eyePosition, 
                reachVector, 
                aabb, 
                (entity) -> !entity.isSpectator() && entity.isPickable(), 
                reachDistance * reachDistance
            );

            if (hitResult != null) {
                Entity entity = hitResult.getEntity();
                if (entity instanceof Player) {
                    Player target = (Player) entity;
                    // 命中玩家后播放格挡的声音
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    
                    // 进入120秒冷却 (2400 ticks)
                    player.getCooldowns().addCooldown(this, 2400);
                    
                    // 被命中的玩家会被给予3.5s缓慢10效果 (等级9)
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 9));
                    target.knockback(
                            (float) Math.sin(player.getYRot() * ((float) Math.PI / 180F))
                            ,0
                            ,
                            (float) (-Math.cos(player.getYRot() * ((float) Math.PI / 180F)))
                    );
                    target.hurt(player.damageSources().playerAttack(player), 1.0F);
                    
                    // 使身上所有没有冷却的物品进入1.5s冷却
                    ItemCooldowns cooldowns = target.getCooldowns();
                    
                    // 检查主手
                    applyCooldownToItem(target, target.getMainHandItem(), cooldowns);
                    // 检查副手
                    applyCooldownToItem(target, target.getOffhandItem(), cooldowns);
                    // 检查物品栏
                    for (ItemStack invStack : target.getInventory().items) {
                        applyCooldownToItem(target, invStack, cooldowns);
                    }
                    
                    // 发送消息提示
                    player.displayClientMessage(Component.translatable("message.noellesroles.boxing_glove.hit", target.getName()).withStyle(ChatFormatting.GREEN), true);
                    target.displayClientMessage(Component.translatable("message.noellesroles.boxing_glove.hit_by", player.getName()).withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    private void applyCooldownToItem(Player player, ItemStack stack, ItemCooldowns cooldowns) {
        if (!stack.isEmpty() && !cooldowns.isOnCooldown(stack.getItem())) {
            cooldowns.addCooldown(stack.getItem(), 30);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }
}