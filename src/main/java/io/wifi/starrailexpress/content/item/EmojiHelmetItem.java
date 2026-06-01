package io.wifi.starrailexpress.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class EmojiHelmetItem extends Item implements Equipable {
    public static final int EMOJI_COUNT = 5;
    private static final String EMOJI_INDEX_KEY = "sre_emoji_index";

    public EmojiHelmetItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide) {
                int index = (getEmojiIndex(stack) + 1) % EMOJI_COUNT;
                setEmojiIndex(stack, index);
                player.displayClientMessage(
                        Component.translatable("message.starrailexpress.emoji_helmet.selected", index + 1),
                        true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.trainmurdermystery.emoji_helmet.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.trainmurdermystery.emoji_helmet.tooltip2", getEmojiIndex(stack) + 1)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static int getEmojiIndex(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        int index = customData.copyTag().getInt(EMOJI_INDEX_KEY);
        return Math.floorMod(index, EMOJI_COUNT);
    }

    private static void setEmojiIndex(ItemStack stack, int index) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(EMOJI_INDEX_KEY, Math.floorMod(index, EMOJI_COUNT));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
