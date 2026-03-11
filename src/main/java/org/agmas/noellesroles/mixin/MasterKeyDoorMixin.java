package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({SmallDoorBlock.class})
public abstract class MasterKeyDoorMixin {
    @Redirect(
            method = {"useWithoutItem(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z",
                    ordinal = 0
            )
    )
    private boolean attendant(ItemStack instance, Item item, BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (instance.is(ModItems.MASTER_KEY_P)) {
            if (!player.isCreative()) {
                instance.hurtAndBreak(1, player, player.getEquipmentSlotForItem(instance));
            }

            return true;
        } else {
            return instance.is(TMMItems.LOCKPICK);
        }
    }
}
