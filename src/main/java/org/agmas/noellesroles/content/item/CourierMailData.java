package org.agmas.noellesroles.content.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** 信使信封物品数据访问器 */
public final class CourierMailData {
    private static final String TAG_MESSAGE = "CourierMessage";
    private static final String TAG_EFFECT = "CourierEffect";
    private static final String TAG_ATTACHED = "CourierAttached";
    private static final String TAG_ATTACH_NAME = "CourierAttachName";
    private static final String TAG_SENDER = "CourierSender";
    private static final String TAG_REPLY = "CourierIsReply";

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        return tag;
    }

    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String getMessage(ItemStack stack) {
        return getOrCreateTag(stack).getString(TAG_MESSAGE);
    }

    public static void setMessage(ItemStack stack, String message) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putString(TAG_MESSAGE, message);
        saveTag(stack, tag);
    }

    public static int getEffect(ItemStack stack) {
        return getOrCreateTag(stack).getInt(TAG_EFFECT);
    }

    public static void setEffect(ItemStack stack, int effect) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt(TAG_EFFECT, effect);
        saveTag(stack, tag);
    }

    public static boolean hasAttached(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_ATTACHED);
    }

    public static void setAttached(ItemStack stack, boolean v) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putBoolean(TAG_ATTACHED, v);
        saveTag(stack, tag);
    }

    public static String getSender(ItemStack stack) {
        return getOrCreateTag(stack).getString(TAG_SENDER);
    }

    public static void setSender(ItemStack stack, String name) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putString(TAG_SENDER, name);
        saveTag(stack, tag);
    }

    public static String getAttachmentName(ItemStack stack) {
        return getOrCreateTag(stack).getString(TAG_ATTACH_NAME);
    }

    public static void setAttachmentName(ItemStack stack, String name) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putString(TAG_ATTACH_NAME, name);
        saveTag(stack, tag);
    }

    public static boolean isReply(ItemStack stack) {
        return getOrCreateTag(stack).getBoolean(TAG_REPLY);
    }

    public static void setReply(ItemStack stack, boolean v) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putBoolean(TAG_REPLY, v);
        saveTag(stack, tag);
    }

    public static void appendTooltip(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        String msg = getMessage(stack);
        if (!msg.isEmpty()) {
            tooltip.add(Component.literal("\u2709 " + msg.substring(0, Math.min(msg.length(), 30)) + (msg.length() > 30 ? "..." : "")));
        }
        int ef = getEffect(stack);
        if (ef == 1) tooltip.add(Component.translatable("item.noellesroles.courier_mail.effect.san"));
        else if (ef == 2) tooltip.add(Component.translatable("item.noellesroles.courier_mail.effect.speed"));
        else if (ef == 3) tooltip.add(Component.translatable("item.noellesroles.courier_mail.effect.disguise"));
        if (hasAttached(stack)) tooltip.add(Component.translatable("item.noellesroles.courier_mail.has_item"));
        String sender = getSender(stack);
        if (!sender.isEmpty()) tooltip.add(Component.translatable("item.noellesroles.courier_mail.from", sender));
    }
}
