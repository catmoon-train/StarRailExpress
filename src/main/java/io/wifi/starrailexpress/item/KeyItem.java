package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.SRE;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class KeyItem extends Item implements AdventureUsable {
    public KeyItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);

        if (state.getBlock() instanceof SmallDoorBlock) {
            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
                ItemStack mainHandStack = player.getMainHandItem();
                ItemLore loreComponent = mainHandStack.get(DataComponents.LORE);
                if (loreComponent != null) {
                    List<Component> lines = loreComponent.lines();
                    if (lines == null || lines.isEmpty()) {
                        return InteractionResult.PASS;
                    }

                    // Sneaking creative player with key sets the door to require a key with the same name
                    if (player.isCreative() && player.isShiftKeyDown()) {
                        String roomName = lines.getFirst().getString();
                        entity.setKeyName(roomName);
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }
}
