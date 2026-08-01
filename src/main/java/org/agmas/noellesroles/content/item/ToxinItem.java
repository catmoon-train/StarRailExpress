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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.game.GameUtils;
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
import org.agmas.noellesroles.init.HSRConstants;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class ToxinItem extends Item {
    public ToxinItem(Item.Properties settings) {
        super(settings);
    }

    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        // 耐久耗尽的毒针无法使用（需回商店补满）。/ A depleted toxin cannot be used until refilled at the shop.
        if (!world.isClientSide) {
            if (ToxinDurability.isDepleted(itemStack)) {
                user.displayClientMessage(
                        Component.translatable("message.noellesroles.toxin.depleted").withStyle(ChatFormatting.DARK_RED),
                        true);
                return InteractionResultHolder.fail(itemStack);
            }
        } else if (itemStack.isDamageableItem() && itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
            return InteractionResultHolder.fail(itemStack);
        }
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!user.isSpectator()) {
            if (remainingUseTicks < this.getUseDuration(stack, user) - 6 && user instanceof Player) {
                Player attacker = (Player) user;
                if (!world.isClientSide) {
                    HitResult collision = getToxinTarget(attacker);
                    if (collision instanceof EntityHitResult) {
                        EntityHitResult entityHitResult = (EntityHitResult) collision;
                        Entity target1 = entityHitResult.getEntity();
                        if (user instanceof ServerPlayer player) {
                            if (target1 instanceof Player target) {
                                ((SREPlayerPoisonComponent) SREPlayerPoisonComponent.KEY.get(target))
                                        .setPoisonTicks(HSRConstants.toxinPoisonTime, player.getUUID());
                                player.playSound(NRSounds.SYRINGE_STAB, 0.15F, 1.0F);
                                player.swing(InteractionHand.MAIN_HAND);
                                // 只要成功命中玩家就扣 1 点耐久（含创造模式），扣的是实际使用的那支毒针。
                                // 不再消耗整支毒针，耗尽后保留物品，可回商店补满。
                                if (ToxinDurability.consumeOne(stack)) {
                                    player.displayClientMessage(Component
                                            .translatable("message.noellesroles.toxin.depleted")
                                            .withStyle(ChatFormatting.DARK_RED), true);
                                }
                                if (!player.isCreative()) {
                                    if (player.level() instanceof ServerLevel slevel) {
                                        var gameComponent = SREGameWorldComponent.KEY.get(player.level());
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
                                    player.getCooldowns().addCooldown(ModItems.TOXIN,
                                            (Integer) ModItems.ITEM_COOLDOWNS.get(ModItems.TOXIN));
                                }
                            }
                        }
                    }

                    return;
                }
            }

        }
    }

    public static HitResult getToxinTarget(Player user) {
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
        }, (double) 15.0F);
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }
}
