package io.wifi.starrailexpress.mixin.util;

import io.wifi.starrailexpress.SRE;
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
            if (!SRE.cantPushableBy.isEmpty())
                return !SRE.cantPushableBy.stream()
                        .anyMatch(predicate -> predicate.test(e) || predicate.test(entity));
            return true;
        };


        cir.setReturnValue(originalPredicate.and(additionalPredicate));
    }
}
