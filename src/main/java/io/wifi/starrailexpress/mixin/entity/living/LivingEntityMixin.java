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

package io.wifi.starrailexpress.mixin.entity.living;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.mixin.entity.EntityMixin;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin {
    @Unique
    private static final AttributeModifier KNIFE_KNOCKBACK_MODIFIER = new AttributeModifier(
            SRE.id("knife_knockback_modifier"), 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    @Shadow
    protected boolean jumping;

    @Shadow
    public abstract void makeSound(@Nullable SoundEvent sound);

    @Shadow
    public abstract @Nullable AttributeInstance getAttribute(Holder<Attribute> attribute);

    @Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void sre$decreaseAirSupply(int currentAir, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Player player) {
            if (player.hasEffect(ModEffects.SAFE_TIME)) {
                cir.setReturnValue(currentAir);
                return;
            }
            if (SREGameWorldComponent.isKillerTeamStatic(player)) {
                if (AreasWorldComponent.KEY.get(player.level()).areasSettings.killerNoDrowing) {
                    cir.setReturnValue(currentAir);
                    return;
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tmm$addKnockbackWithKnife(CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            AttributeModifier v = new AttributeModifier(SRE.id("knife_knockback_modifier"), .5f,
                    AttributeModifier.Operation.ADD_VALUE);
            updateAttribute(player.getAttribute(Attributes.ATTACK_KNOCKBACK), v,
                    player.getMainHandItem().is(TMMItems.KNIFE));
        }
    }

    @Unique
    private static void updateAttribute(AttributeInstance attribute, AttributeModifier modifier, boolean addOrKeep) {
        if (attribute != null) {
            boolean alreadyHasModifier = attribute.hasModifier(modifier.id());
            if (addOrKeep && !alreadyHasModifier) {
                attribute.addPermanentModifier(modifier);
            } else if (!addOrKeep && alreadyHasModifier) {
                attribute.removeModifier(modifier);
            }
        }
    }
}
