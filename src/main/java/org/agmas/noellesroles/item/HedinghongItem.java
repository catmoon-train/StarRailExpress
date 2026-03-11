package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.repack.HSRConstants;
import org.agmas.noellesroles.repack.HSRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 鹤顶红
 * - 一次性道具
 * - 对目标使用后使目标中毒（参考toxin）
 */
public class HedinghongItem extends Item {
    public HedinghongItem(Item.Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }
    
    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!user.isSpectator()) {
            if (remainingUseTicks < this.getUseDuration(stack, user) - 6 && user instanceof Player) {
                Player attacker = (Player) user;
                if (!world.isClientSide) {
                    HitResult collision = getHedinghongTarget(attacker);
                    if (collision instanceof EntityHitResult) {
                        EntityHitResult entityHitResult = (EntityHitResult) collision;
                        Entity target1 = entityHitResult.getEntity();
                        if (user instanceof ServerPlayer player) {
                            if (target1 instanceof Player target) {
                                ((PlayerPoisonComponent) PlayerPoisonComponent.KEY.get(target))
                                        .setPoisonTicks(HSRConstants.toxinPoisonTime, player.getUUID());
                                player.playSound(HSRSounds.ITEM_SYRINGE_STAB, 0.15F, 1.0F);
                                player.swing(InteractionHand.MAIN_HAND);
                                if (!player.isCreative()) {
                                    player.getMainHandItem().shrink(1);
                                    if (player.level() instanceof ServerLevel slevel) {
                                        var gameComponent = GameWorldComponent.KEY.get(player.level());
                                        slevel.players().forEach((pl) -> {
                                            if (pl.distanceToSqr(player) <= 100) {
                                                if (gameComponent.isRole(pl, ModRoles.DOCTOR)) {
                                                    pl.displayClientMessage(Component
                                                            .translatable("message.noellesroles.doctor.someone_toxin")
                                                            .withStyle(ChatFormatting.YELLOW), true);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    public static HitResult getHedinghongTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            if (entity instanceof Player player) {
                if (GameFunctions.isPlayerAliveAndSurvival(player)) {
                    return true;
                }
            }
            return false;
        }, 15.0F);
    }
    
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }
    
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }
}
