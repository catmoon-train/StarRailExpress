package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 解毒剂
 * - 治愈中毒玩家
 * - 治愈感染玩家
 */
public class AntidoteItem extends Item {
    public AntidoteItem(Properties settings) {
        super(settings);
    }

    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!user.isSpectator()) {
            if (remainingUseTicks < this.getUseDuration(stack, user) - 10 && user instanceof Player) {
                Player attacker = (Player)user;
                HitResult collision = getAntidoteTarget(attacker);
                if (collision instanceof EntityHitResult) {
                    EntityHitResult entityHitResult = (EntityHitResult) collision;
                    Entity target = entityHitResult.getEntity();
                    if (attacker instanceof ServerPlayer player) {
                        if (!((double)target.distanceTo(player) > (double)3.0F)) {
                            // 清除中毒状态
                            final var playerPoisonComponent = SREPlayerPoisonComponent.KEY.get(target);
                            ((SREPlayerPoisonComponent) playerPoisonComponent).init();
                            playerPoisonComponent.sync();
                            
                            // 清除感染状态
                            InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(target);
                            infectedComponent.cure();
                            
                            // 播放音效 - 附近所有人都能听到
                            final var blockPos = target.blockPosition();
                            ((ServerLevel) world).playSound(null, blockPos.getX(), blockPos.getY(), blockPos.getZ(), 
                                NRSounds.SYRINGE_STAB, SoundSource.PLAYERS, 1.0F, 1.0F);
                            player.swing(InteractionHand.MAIN_HAND);
                            if (!player.isCreative()) {
                                int cd = (Integer) ModItems.ITEM_COOLDOWNS.get(ModItems.ANTIDOTE);
                                // 如果疫使在场，解药冷却减少40%
                                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
                                for (ServerPlayer sp : ((ServerLevel) world).players()) {
                                    if (gameWorld.isRole(sp, ModRoles.INFECTED)) {
                                        cd = (int) (cd * 0.6);
                                        break;
                                    }
                                }
                                player.getCooldowns().addCooldown(ModItems.ANTIDOTE, cd);
                            }
                        }
                    }
                }
            }
        }
    }

    public static HitResult getAntidoteTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            boolean var10000;
            if (entity instanceof Player player) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    var10000 = true;
                    return var10000;
                }
            }

            var10000 = false;
            return var10000;
        }, (double)3.0F);
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }
}
