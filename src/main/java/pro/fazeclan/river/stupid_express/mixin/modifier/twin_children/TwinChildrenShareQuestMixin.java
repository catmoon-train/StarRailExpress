/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenTaskShare;

@Mixin(io.wifi.starrailexpress.api.RoleMethodDispatcher.class)
public abstract class TwinChildrenShareQuestMixin {

    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V", at = @At("TAIL"))
    private static void twinChildrenShareQuest(Player player, String quest, int taskStreak, boolean isParallelTask,
            CallbackInfo ci) {
        TwinChildrenTaskShare.onTaskCompleted(player);
    }
}
