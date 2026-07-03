package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class SpellbreakerPotionItem extends Item {
    public SpellbreakerPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 32;
    }
    public static HitResult getTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity ->{
//            if (entity instanceof PuppeteerBodyEntity puppeteerBodyEntity){
//                var owner = puppeteerBodyEntity.getOwner();
//                return owner != null && GameUtils.isPlayerAliveAndSurvival(owner);
//            }
                    return entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player);

                }, 14f);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
                
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            if (!gameWorld.isRole(player, ModRoles.SPELLBREAKER)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.spellbreaker.item_only")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResultHolder.consume(itemInHand);
            }
                
            // 使用 getTarget 获取目标
            HitResult hitResult = getTarget(player);
            if (hitResult instanceof EntityHitResult entityHitResult) {
                if (entityHitResult.getEntity() instanceof Player targetPlayer) {
                    // 对目标玩家使用效果
                    targetPlayer.addEffect(new MobEffectInstance(ModEffects.NEXT_SKILL_BANED,80*20,0,false,false,false));
                    itemInHand.consume(1, player);
                        
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.spellbreaker.potion_success")
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.spellbreaker.no_target")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.spellbreaker.no_target")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
            
        return InteractionResultHolder.consume(itemInHand);
    }

//    @Override
//    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity user) {
//
//        return stack;
//    }
}
