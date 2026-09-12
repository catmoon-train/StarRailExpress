/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import io.wifi.starrailexpress.content.block.MountableBlock;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

/**
 * Chair blocks refuse a player who already has a vehicle. Sit the walking twin
 * so the stacked pair can use seats.
 */
@Mixin(MountableBlock.class)
public abstract class TwinChildrenMountableMixin {

    @ModifyVariable(method = "useWithoutItem", at = @At("HEAD"), argsOnly = true)
    private Player stupidExpress$sitWithLowerTwin(Player player) {
        return TwinChildrenHandler.stackMover(player);
    }
}
