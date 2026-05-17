package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public class RepairPresetWandItem extends Item {
    public RepairPresetWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        if (player.isShiftKeyDown()) {
            putPos(tag, "Anchor", pos);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_anchor",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD), true);
        } else {
            ListTag points = tag.getList("Points", Tag.TAG_COMPOUND);
            CompoundTag point = new CompoundTag();
            putPos(point, "Pos", pos);
            points.add(point);
            tag.put("Points", points);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_point",
                    points.size(), pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.YELLOW), true);
        }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                CustomData.EMPTY).copyTag();
        ListTag points = tag.getList("Points", Tag.TAG_COMPOUND);
        if (serverPlayer.isShiftKeyDown()) {
            int removed = points.size();
            tag.remove("Points");
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_clear_all",
                    removed).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(stack);
        }
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
            tag.put("Points", points);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_undo",
                    points.size()).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.success(stack);
        }
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.preset_empty")
                .withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.success(stack);
    }

    private static void putPos(CompoundTag tag, String key, BlockPos pos) {
        tag.putInt(key + "X", pos.getX());
        tag.putInt(key + "Y", pos.getY());
        tag.putInt(key + "Z", pos.getZ());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.repair_preset_wand.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
