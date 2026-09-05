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

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.KillerKnifeDurability;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

public class KnifeItem extends SkinableItem implements TrainWeapon {
    public KnifeItem(Properties settings) {
        super(settings);
    }

    /**
     * (target, killer)
     */
    // public static BiConsumer<ServerPlayer, ServerPlayer> PlayerKilledPlayer;
    public static final ResourceLocation ITEM_ID = SRE.TMMId("knife");

    /**
     * 是否允许进入右键 {@code startUsingItem} 蓄力。
     * 子类可拦截（例如冷却中、形态不允许）。
     */
    public boolean canStartKnifeCharge(Level world, Player user, InteractionHand hand, ItemStack stack) {
        return true;
    }

    /**
     * 已经 {@code startUsingItem} 之后调用（客户端与服务端都会到）。
     */
    public void onKnifeChargeStarted(Level world, Player user, InteractionHand hand, ItemStack stack) {
    }

    /**
     * 松开或蓄满时调用（客户端与服务端都会到）。
     *
     * @param usedTicks 已蓄力时长
     * @return true 表示已处理，不再走默认刺杀
     */
    public boolean onKnifeChargeReleased(ItemStack stack, Level world, Player attacker, int usedTicks) {
        return false;
    }

    /**
     * 达到可释放的最小蓄力 tick。默认与 {@code KnifeChargeableItem} 一致。
     */
    public int getMinKnifeChargeTicks(ItemStack stack, LivingEntity user) {
        return user.hasEffect(org.agmas.noellesroles.init.ModEffects.TWO_DIMENSIONAL_CAMERA) ? 4 : 8;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (!world.isClientSide) {
            boolean durabilityKnife = KillerKnifeDurability.isDurabilityModeEnabled(user.level())
                    && KillerKnifeDurability.isMarkedKnife(itemStack);
            if (durabilityKnife && KillerKnifeDurability.isDepleted(itemStack)) {
                user.displayClientMessage(
                        Component.translatable("message.sre.knife.depleted").withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResultHolder.fail(itemStack);
            }
        } else {
            if (itemStack.getMaxDamage() > 0 && itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                return InteractionResultHolder.fail(itemStack);
            }
        }
        if (!canStartKnifeCharge(world, user, hand, itemStack)) {
            return InteractionResultHolder.fail(itemStack);
        }
        user.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0f, 1.0f);
        user.startUsingItem(hand);
        onKnifeChargeStarted(world, user, hand, itemStack);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (!(user instanceof Player attacker)) {
            return;
        }
        int usedTicks = this.getUseDuration(stack, user) - remainingUseTicks;
        if (onKnifeChargeReleased(stack, world, attacker, usedTicks)) {
            return;
        }
        int chargeTicks = getMinKnifeChargeTicks(stack, user);
        if (remainingUseTicks >= this.getUseDuration(stack, user) - chargeTicks || !world.isClientSide)
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        final var role = game.getRole(attacker);
        if (role != null) {
            if (!role.onUseKnife(attacker)) {
                return;
            }
        }
        HitResult collision = getKnifeTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack(target);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        this.releaseUsing(stack, world, user, 0);
        return stack;
    }

    public static HitResult getKnifeTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> {
                    // if (entity instanceof PuppeteerBodyEntity puppeteerBodyEntity){
                    // var owner = puppeteerBodyEntity.getOwner();
                    // return owner != null && GameUtils.isPlayerAliveAndSurvival(owner);
                    // }
                    return entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player);

                }, 4f);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 110;
    }

    @Override
    public String getItemSkinType() {
        return "knife";
    }
}