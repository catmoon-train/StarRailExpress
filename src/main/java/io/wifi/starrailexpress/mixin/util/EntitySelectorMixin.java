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

package io.wifi.starrailexpress.mixin.util;

import io.wifi.starrailexpress.rules.CollisionRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin {
    @Inject(method = "pushableBy", at = @At("TAIL"), cancellable = true)
    private static void pushableBy(Entity entity, CallbackInfoReturnable<Predicate<Entity>> cir) {
        Predicate<Entity> originalPredicate = cir.getReturnValue();
        Predicate<Entity> additionalPredicate = e -> {
            if (!CollisionRules.cantPushableBy.isEmpty()) {
                return !CollisionRules.cantPushableBy.stream()
                        .anyMatch(predicate -> predicate.test(e) || predicate.test(entity));
            }
            return true;
        };

        cir.setReturnValue(originalPredicate.and(additionalPredicate));
    }
}
