package org.agmas.noellesroles.content.block.scene;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.scene.SceneTaskManager;

/**
 * 运输点（场景任务「运输点任务」）：
 * 起点（end=false）：右键取货，获得运输物品。
 * 终点（end=true）：手持运输物品右键交货完成任务。
 * 起点使用绿色箭头木箱贴图，终点使用原版木桶贴图。
 */
public class TransportPointBlock extends Block {

    public static final BooleanProperty END = BooleanProperty.create("end");

    public TransportPointBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(END, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(END);
    }

    /** 终点：手持运输物品右键完成交货。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(END)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && stack.is(ModItems.TRANSPORT_PACKAGE)) {
            // Resync SceneTaskManager state if needed
            if (!SceneTaskManager.hasTransportTask(sp) && hasTransportTaskInComponent(sp)) {
                SceneTaskManager.assign(sp, SceneTaskManager.Type.TRANSPORT);
            }
            if (SceneTaskManager.hasTransportTask(sp)) {
                // 消耗一个运输物品
                removeAllTransportPackages(sp);
                SceneTaskManager.reportTransportDeliver(sp);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0,
                            pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
                    serverLevel.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
            } else {
                sp.displayClientMessage(Component.translatable("message.noellesroles.scene_task.transport_no_task"), true);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 起点：空手右键取货，获得运输物品。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (state.getValue(END)) {
            return InteractionResult.PASS;
        }
        // 确保玩家主手为空
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            // Resync: if the player has a TRANSPORT task in SREPlayerTaskComponent but
            // SceneTaskManager has lost it (e.g. after a component init / relog), auto-assign.
            if (!SceneTaskManager.hasTransportTask(sp) && hasTransportTaskInComponent(sp)) {
                SceneTaskManager.assign(sp, SceneTaskManager.Type.TRANSPORT);
            }
            if (SceneTaskManager.hasTransportTask(sp)) {
                // 如果背包中已存在运输物品，不允许重复取货
                if (hasTransportPackageInInventory(sp)) {
                    sp.displayClientMessage(Component.translatable("message.noellesroles.scene_task.transport_already_has"), true);
                    return InteractionResult.CONSUME;
                }
                SceneTaskManager.reportTransportPickup(sp);
                // 给予运输物品
                ItemStack pkg = new ItemStack(ModItems.TRANSPORT_PACKAGE);
                if (!sp.getInventory().add(pkg)) {
                    sp.drop(pkg, false);
                }
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0,
                            pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
                    serverLevel.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
            } else {
                sp.displayClientMessage(Component.translatable("message.noellesroles.scene_task.transport_no_task"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    /** Returns true if the player's SREPlayerTaskComponent has an active TRANSPORT task. */
    private static boolean hasTransportTaskInComponent(ServerPlayer sp) {
        SREPlayerTaskComponent comp = SREPlayerTaskComponent.KEY.get(sp);
        return comp != null && comp.tasks.containsKey(SREPlayerTaskComponent.Task.TRANSPORT);
    }

    private static boolean hasTransportPackageInInventory(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.TRANSPORT_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private static void removeAllTransportPackages(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.TRANSPORT_PACKAGE)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
