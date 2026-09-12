/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import io.wifi.starrailexpress.content.block.ToiletBlock;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

/**
 * Toilet sitting is copied out of {@link io.wifi.starrailexpress.content.block.MountableBlock}
 * and must use the walking twin as well.
 */
@Mixin(ToiletBlock.class)
public abstract class TwinChildrenToiletMixin {

    @ModifyVariable(method = "useWithoutItem", at = @At("HEAD"), argsOnly = true)
    private Player stupidExpress$sitWithLowerTwin(Player player) {
        return TwinChildrenHandler.stackMover(player);
    }
}
