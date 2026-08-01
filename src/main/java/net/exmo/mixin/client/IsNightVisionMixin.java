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

package net.exmo.mixin.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.compat.IrisHelper;
import net.exmo.sre.EXSREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class IsNightVisionMixin {
    @Unique
    private static boolean starRailExpress$isCheckingInstinct;

    @Inject(at = @At("HEAD"), method = "hasEffect", cancellable = true)
    public void hasEffect$sre$inject(Holder<MobEffect> holder, CallbackInfoReturnable<Boolean> cir) {
        if (holder != MobEffects.NIGHT_VISION || !IrisHelper.isIrisShaderPackInUse())
            return;

        var player = (LivingEntity) (Object) this;
        if (player instanceof LocalPlayer localPlayer) {
            LocalPlayer player1 = Minecraft.getInstance().player;
            if (player1 == null)
                return;
            if (localPlayer.getUUID().equals(player1.getUUID()) && starRailExpress$isInstinctEnabledSafely()) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "getEffect", cancellable = true)
    public void getEffect$sre$inject(Holder<MobEffect> holder, CallbackInfoReturnable<MobEffectInstance> cir) {
        if (holder != MobEffects.NIGHT_VISION || !IrisHelper.isIrisShaderPackInUse())
            return;

        var player = (LivingEntity) (Object) this;
        if (player instanceof LocalPlayer localPlayer) {
            LocalPlayer player1 = Minecraft.getInstance().player;
            if (player1 == null)
                return;
            if (localPlayer.getUUID().equals(player1.getUUID()) && starRailExpress$isInstinctEnabledSafely()) {
                cir.setReturnValue(EXSREClient.night_vision_cache_);
                cir.cancel();
            }
        }
    }

    @Unique
    private static boolean starRailExpress$isInstinctEnabledSafely() {
        if (starRailExpress$isCheckingInstinct) {
            return false;
        }
        starRailExpress$isCheckingInstinct = true;
        try {
            return SREClient.isInstinctEnabled() && SREClient.hasInstinctNightVision();
        } finally {
            starRailExpress$isCheckingInstinct = false;
        }
    }
}
