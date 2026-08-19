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

package io.wifi.starrailexpress.mixin.entity.item;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.BrokenGunDropUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Shadow
    public abstract @Nullable Entity getOwner();

    @Shadow
    private @Nullable UUID thrower;

    @Shadow
    public abstract ItemStack getItem();

    @WrapMethod(method = "playerTouch")
    public void tmm$preventGunPickup(Player player, Operation<Void> original) {
        if (BrokenGunDropUtils.isBrokenGun(this.getItem())) {
            return;
        }
        int murderGoldAmount = MurderTimeEventComponent.getMurderGoldAmount(this.getItem());
        if (murderGoldAmount > 0) {
            if (!GameUtils.isGameRunning(player)) {
                original.call(player);
                return;
            }
            if (player instanceof ServerPlayer serverPlayer && GameUtils.isGameRunning(player)) {
                SREPlayerShopComponent.KEY.get(player).addToBalance(murderGoldAmount);
                serverPlayer.displayClientMessage(Component.translatable("message.starrailexpress.murder_gold.pickup",
                        murderGoldAmount).withStyle(ChatFormatting.GOLD), true);
                ((ItemEntity) (Object) this).discard();
            }
            return;
        }
        if (player.isCreative() || SRE.isLobby) {
            original.call(player);
            return;
        }
        if (!GameUtils.isGameRunning(player)) {
            original.call(player);
            return;
        }

        TrueFalseResult result = RoleMethodDispatcher.callOnPickupItem(player,
                this.getItem());
        if (result == TrueFalseResult.FALSE) {
            return;
        } else if (result == TrueFalseResult.TRUE) {
            original.call(player);
            return;
        }

        // InteractionResult.PASS (默认)时走此逻辑
        if (!MCItemsUtils.hasHotbarFreeSlot(player)) {
            // 装不下了，不准继续~
            return;
        }
        // gun
        if (this.getItem().is(TMMItemTags.GUNS)) {
            // is gun
            if (SREGameWorldComponent.KEY.get(player.level()).canPickUpRevolver(player)
                    && !player.equals(this.getOwner())) {
                // can pick gun
                // 在拾取物品之前调用角色的onPickupItem方法
                if (SREItemUtils.hasItem(player, TMMItemTags.GUNS)) {
                    return;
                }
                // haven't gun can pick
                original.call(player);
                return;
            }
        } else {
            original.call(player);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tmm$tickBrokenGunEffects(CallbackInfo ci) {
        if (!BrokenGunDropUtils.isBrokenGun(this.getItem())) {
            return;
        }
        ItemEntity item = (ItemEntity) (Object) this;
        if (!(item.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (item.tickCount >= BrokenGunDropUtils.DESPAWN_TICKS) {
            item.discard();
            return;
        }
        // 只在落地后冒少量粒子，掉落途中不显示
        if (item.onGround() && item.tickCount % 8 == 0) {
            tmm$spawnBrokenGunParticles(serverLevel, item);
        }
    }

    @Unique
    private void tmm$spawnBrokenGunParticles(ServerLevel level, ItemEntity item) {
        level.sendParticles(ParticleTypes.SMOKE,
                item.getX(), item.getY() + 0.15D, item.getZ(),
                2, 0.08D, 0.05D, 0.08D, 0.01D);
        level.sendParticles(ParticleTypes.CRIT,
                item.getX(), item.getY() + 0.1D, item.getZ(),
                1, 0.05D, 0.03D, 0.05D, 0.02D);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void windChargeNoRemoveItem(DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        if (damageSource == null || damageSource.getDirectEntity() instanceof WindCharge) {
            cir.setReturnValue(false);
        }
    }
}
