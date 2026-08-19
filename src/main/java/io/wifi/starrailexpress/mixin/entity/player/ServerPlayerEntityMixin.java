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

package io.wifi.starrailexpress.mixin.entity.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropAndClearItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.ServerDropManager;
import io.wifi.starrailexpress.util.SkinUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerEntityMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    public void onDrop(boolean dropAll, CallbackInfoReturnable<Boolean> cir) {
        if (ServerDropManager.onDrop((ServerPlayer) (Object) this, dropAll)) {
            return;
        }
        cir.setReturnValue(false);
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    public void onDropItem(ItemStack itemStack, boolean bl, boolean bl2, CallbackInfoReturnable<ItemEntity> cir) {
        if (itemStack.getItem() instanceof DropAndClearItem) {
            cir.setReturnValue(null);
        }
    }

    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    public void tmm$disableSleepMessage(ServerPlayer instance, Component message, boolean overlay,
            Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(instance, message, overlay);
        }
    }

    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FZZ)V"))
    public void tmm$disableSetSpawnpoint(ServerPlayer instance, ResourceKey<Level> dimension, @Nullable BlockPos pos,
            float angle, boolean forced, boolean sendMessage, Operation<Void> original) {
        if (SRE.isLobby) {
            original.call(dimension, pos, angle, forced, sendMessage); // 传递正确的参数
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    public void attack(Entity ctarget, CallbackInfo ci) {
        if (SRE.isLobby) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.isSpectator()) {
            return;
        }
        Entity target = ctarget;
        if (!self.isCreative()) {
            if (target instanceof WheelchairEntity wc) {
                if (wc.getRider() != null) {
                    target = wc.getRider();
                }
            }
        }
        var mainhandItem = self.getMainHandItem();
        if (!self.getCooldowns().isOnCooldown(TMMItems.BAT) && mainhandItem.is(TMMItems.BAT)
                && self.getAttackStrengthScale(0.75F) >= 1f) {
            if (target instanceof ServerPlayer playerTarget) {
                GameUtils.killPlayer(playerTarget, true, self, GameConstants.DeathReasons.BAT);
            }
            if (target instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                puppeteerBodyEntity.playerHurt(self, GameConstants.DeathReasons.BAT);
            }
            CrosshairaddonsCompat.onAttack(target);
            self.level().playSound(null, self.blockPosition(), TMMSounds.ITEM_BAT_HIT, SoundSource.PLAYERS, 3f, 1f);
            ci.cancel();
            return;
        } else if (mainhandItem.getItem() instanceof SREItemProperties.LeftClickHurtable htit) {
            boolean original = true;
            // var result = htit.onTryHurt(self, target, self.getMainHandItem());
            // if (result.equals(InteractionResult.CONSUME) ||
            // result.equals(InteractionResult.FAIL)) {
            // ci.cancel();
            // return;
            // }
            if (target instanceof ServerPlayer playerTarget) {
                original = htit.onServerAttack(self, playerTarget, mainhandItem);
            }
            if (target instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                puppeteerBodyEntity.playerHurt(self, SkinUtils.getItemTypeResourceLocation(mainhandItem));
            }
            // self.level().playSound(null, self.blockPosition(), TMMSounds.ITEM_BAT_HIT,
            // SoundSource.PLAYERS, 3f, 1f);
            CrosshairaddonsCompat.onAttack(target);
            if (!original) {
                ci.cancel();
            }
            return;
        }

        // 双节棍左键和Shift+左键攻击处理
        if (mainhandItem.is(TMMItems.NUNCHUCK) && target instanceof ServerPlayer playerTarget
                && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(playerTarget)
                && self instanceof ServerPlayer spself) {
            boolean isShiftLeftClick = self.isShiftKeyDown();
            int direction = isShiftLeftClick ? 2 : 1; // Shift+左键=2(向后), 左键=1(向右)
            io.wifi.starrailexpress.network.original.NunchuckHitPayload
                    .onHurt(spself, playerTarget, direction);
            CrosshairaddonsCompat.onAttack(target);
            ci.cancel();
            return;
        }
    }
}