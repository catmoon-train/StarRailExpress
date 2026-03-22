package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block.TrainDoorBlock;
import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * 警报陷阱物品
 * - 工程师商店物品（所有人可使用）
 * - 在商店以150金币购买
 * - 右键门：在门上放置警报陷阱
 * - 当撬棍使用时触发，发出响亮的警报声
 */
public class AlarmTrapItem extends Item implements AdventureUsable {

    public AlarmTrapItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);

        if (player == null)
            return InteractionResult.PASS;

        // 检查是否为门方块
        if (state.getBlock() instanceof SmallDoorBlock) {

            BlockPos lowerPos = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();

            if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity doorEntity) {
                // 检查门是否支持
                if (!(state.getBlock() instanceof TrainDoorBlock)) {
                    if (doorEntity.getKeyName().isEmpty()) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.engineer.not_support_door")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return InteractionResult.FAIL;
                    }
                }
                // 检查门是否已被破坏
                if (doorEntity.isBlasted()) {
                    if (!world.isClientSide) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.engineer.already_broken")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    return InteractionResult.FAIL;
                }

                // 检查门是否已有警报陷阱
                if (hasDoorAlarmTrap(doorEntity)) {
                    if (!world.isClientSide) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.engineer.already_trapped")
                                        .withStyle(ChatFormatting.YELLOW),
                                true);
                    }
                    return InteractionResult.FAIL;
                }

                // 放置警报陷阱
                setDoorAlarmTrap(doorEntity, true);

                if (!world.isClientSide) {
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                            BuiltInRegistries.ITEM.getKey(this));

                    world.playSound(null, lowerPos.getX() + 0.5, lowerPos.getY() + 1, lowerPos.getZ() + 0.5,
                            SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.7f, 1.2f);
                    player.displayClientMessage(Component.translatable("message.noellesroles.engineer.trap_placed")
                            .withStyle(ChatFormatting.GREEN), true);
                }

                // 消耗物品
                if (!player.isCreative()) {
                    context.getItemInHand().shrink(1);
                }

                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * 检查门是否有警报陷阱
     * 我们使用 keyName 字段来存储陷阱状态（如果包含 "alarmed:" 则表示有陷阱）
     */
    public static boolean hasDoorAlarmTrap(DoorBlockEntity doorEntity) {
        String keyName = doorEntity.getKeyName();
        return keyName != null && keyName.contains("alarmed:");
    }

    /**
     * 设置门的警报陷阱状态
     */
    public static void setDoorAlarmTrap(DoorBlockEntity doorEntity, boolean trapped) {
        String currentKeyName = doorEntity.getKeyName();
        if (currentKeyName == null)
            currentKeyName = "";

        if (trapped) {
            if (!hasDoorAlarmTrap(doorEntity)) {
                doorEntity.setKeyName("alarmed:" + currentKeyName);
            }
        } else {
            if (hasDoorAlarmTrap(doorEntity)) {
                // 只移除 "alarmed:" 前缀，而不是前8个字符
                int index = currentKeyName.indexOf("alarmed:");
                if (index != -1) {
                    doorEntity.setKeyName(currentKeyName.substring(0, index) + currentKeyName.substring(index + 8));
                }
            }
        }
    }

    /**
     * 触发警报陷阱（被撬棍使用时调用）
     * 
     * @return true 如果触发了警报
     */
    public static boolean triggerAlarmTrap(DoorBlockEntity doorEntity, Level world) {
        if (hasDoorAlarmTrap(doorEntity)) {
            // 移除陷阱（一次性）
            setDoorAlarmTrap(doorEntity, false);

            // 播放响亮的警报声
            if (!world.isClientSide) {
                BlockPos pos = doorEntity.getBlockPos();
                // 播放多个叠加的声音让警报更响亮
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 0.8f);
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 3.0f, 0.5f);
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        SoundEvents.WARDEN_ROAR, SoundSource.BLOCKS, 1.5f, 2.0f);
            }

            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.alarm_trap.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.alarm_trap.tooltip2")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}