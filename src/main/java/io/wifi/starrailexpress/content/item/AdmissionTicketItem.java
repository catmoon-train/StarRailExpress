package io.wifi.starrailexpress.content.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

public class AdmissionTicketItem extends Item {
    public static final String TAG_TICKET_ID = "TicketId";
    public static final String TAG_USES = "Uses";

    public AdmissionTicketItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(UUID ticketId, String ticketName, int uses) {
        ItemStack stack = new ItemStack(io.wifi.starrailexpress.index.TMMItems.ADMISSION_TICKET);
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TICKET_ID, ticketId.toString());
        tag.putInt(TAG_USES, uses);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME, Component.literal(ticketName == null || ticketName.isBlank()
                ? Component.translatable("item.starrailexpress.admission_ticket").getString()
                : ticketName));
        return stack;
    }

    public static String getTicketId(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(TAG_TICKET_ID);
    }

    public static int getUses(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_USES) ? tag.getInt(TAG_USES) : 1;
    }

    public static boolean matches(ItemStack stack, String ticketId) {
        return stack.getItem() instanceof AdmissionTicketItem
                && ticketId != null
                && !ticketId.isBlank()
                && ticketId.equals(getTicketId(stack));
    }

    public static void consumeUse(ItemStack stack) {
        int uses = getUses(stack);
        if (uses < 0) {
            return;
        }
        if (uses <= 1) {
            stack.shrink(1);
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(TAG_USES, uses - 1);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip,
            TooltipFlag flag) {
        int uses = getUses(stack);
        tooltip.add(uses < 0
                ? Component.translatable("tooltip.starrailexpress.admission_ticket.infinite")
                : Component.translatable("tooltip.starrailexpress.admission_ticket.uses", uses));
    }
}
