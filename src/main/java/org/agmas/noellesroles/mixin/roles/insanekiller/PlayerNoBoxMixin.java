package org.agmas.noellesroles.mixin.roles.insanekiller;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class PlayerNoBoxMixin {
    // @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    // public void getBoundingBox(CallbackInfoReturnable<AABB> cir) {
    //     // Entity entity = (Entity) (Object) this;
    //     // if (entity instanceof ServerPlayer serverPlayer){
    //     //     if (GameWorldComponent.KEY.get(serverPlayer.level()).isRole(serverPlayer, ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
    //     //         final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(serverPlayer);
    //     //         if (insaneKillerPlayerComponent.isActive) {
    //     //             if (InsaneKillerPlayerComponent.skipPD) {
    //     //                 InsaneKillerPlayerComponent.skipPD = false;
    //     //             } else {

    //     //                 cir.setReturnValue(AABB.ofSize(serverPlayer.position().add(0,0.3,0), 1, 0.3, 1));
    //     //             }
    //     //         }
    //     //     }
    //     // }
    // }
}
