package io.wifi.starrailexpress.content.item;

import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.SlabType;

public class BreakingBridgeToolItem extends Item {
    private Block block = null;

    public BreakingBridgeToolItem(Properties properties) {
        super(properties);
    }

    public BreakingBridgeToolItem(Properties properties, Block block) {
        super(properties);
        this.block = block;
    }

    public InteractionResult useOn(UseOnContext useOnContext) {
        var pos = useOnContext.getClickedPos();
        var player = useOnContext.getPlayer();
        var level = player.level();
        if (block == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (level.getBlockState(pos).getBlock() instanceof BreakingBridgeBlock) {
            if (player.isShiftKeyDown()) {
                if (player.getOffhandItem().is(Items.DEBUG_STICK)) {
                    var entity = level.getBlockEntity(pos);
                    if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                        bbbe.displayState = null;
                        bbbe.blockEntityTag = null;
                        bbbe.sync();
                        player.displayClientMessage(
                                Component.translatable("item.starrailexpress.fake_block_tool.clear"),
                                true);
                    }
                } else {
                    var entity = level.getBlockEntity(pos);
                    if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                        if (bbbe.displayState != null) {
                            var originalTag = bbbe.blockEntityTag;
                            level.setBlockAndUpdate(pos, bbbe.displayState);
                            if (originalTag != null) {
                                var newBlockEntity = level.getBlockEntity(pos);
                                if (newBlockEntity != null) {
                                    newBlockEntity.loadCustomOnly(originalTag, level.registryAccess());
                                }
                            }
                            player.displayClientMessage(
                                    Component.translatable("item.starrailexpress.fake_block_tool.restore",
                                            bbbe.displayState.getBlock().getName()),
                                    true);
                        }
                    }
                }
            }
        } else {
            var targetState = level.getBlockState(pos);
            var fromEntity = level.getBlockEntity(pos);
            CompoundTag entityTag = null;
            if (fromEntity != null) {
                entityTag = fromEntity.saveCustomOnly(level.registryAccess());
            }
            if (targetState == null) {
                return InteractionResult.FAIL;
            }
            level.setBlockAndUpdate(pos, block.getStateForPlacement(new BlockPlaceContext(useOnContext))
                    .setValue(BreakingBridgeBlock.TYPE, SlabType.DOUBLE));
            if (level.getBlockEntity(pos) instanceof BreakingBridgeBlockEntity bbbe) {
                bbbe.displayState = targetState;
                bbbe.blockEntityTag = entityTag;
                bbbe.sync();
                player.displayClientMessage(
                        Component.translatable("block.noellesroles.breaking_bridge.info_tool",
                                block.getName(),
                                bbbe.displayState.getBlock().getName(),
                                bbbe.breakingStage, bbbe.breakingTime, bbbe.restoringTime),
                        true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
