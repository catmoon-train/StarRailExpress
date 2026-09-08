/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenTaskShare;

@Mixin(SREPlayerMinigameTaskComponent.class)
public abstract class TwinChildrenShareMinigameMixin {

    @Inject(method = "onMinigameBlockCompleted", at = @At("RETURN"))
    private void twinChildrenShareMinigame(ServerPlayer sp, BlockPos pos, int reward, String blockMinigameId,
            CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            TwinChildrenTaskShare.onTaskCompleted(sp);
        }
    }
}
