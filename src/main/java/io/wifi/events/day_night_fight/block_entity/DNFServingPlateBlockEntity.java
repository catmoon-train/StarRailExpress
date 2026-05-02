package io.wifi.events.day_night_fight.block_entity;

import io.wifi.starrailexpress.content.block_entity.BeveragePlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class DNFServingPlateBlockEntity extends BeveragePlateBlockEntity {
    public DNFServingPlateBlockEntity(BlockPos pos, BlockState state) {
        super(DNFBlockEntities.SERVING_PLATE, pos, state);
        setDrink(false);
    }

    public boolean hasFood() {
        return !getStoredItems().isEmpty();
    }

    public ItemStack takeFood() {
        if (getStoredItems().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = getStoredItems().getFirst().copy();
        stack.setCount(1);
        clearItems();
        setPoisoner(null);
        setArmorer(null);
        return stack;
    }
}
