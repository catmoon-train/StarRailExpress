package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.neutral.lender.LenderRoleHandler;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.List;
import java.util.UUID;

/** A signed, single-use loan contract. */
public class LoanContractItem extends Item {
    public static final String PRINCIPAL = "LoanPrincipal";
    public static final String INTEREST = "LoanInterest";
    public static final String CREATED_AT = "LoanCreatedAt";
    public static final String LENDER = "LoanLender";
    public static final String LAST_REMINDER = "LoanLastReminder";

    public LoanContractItem(Properties properties) {
        super(properties);
    }

    public static void initialize(ItemStack stack, int principal, UUID lender, long createdAt) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(PRINCIPAL, principal);
        tag.putInt(INTEREST, 0);
        tag.putLong(CREATED_AT, createdAt);
        tag.putString(LENDER, lender.toString());
        tag.putLong(LAST_REMINDER, 0L);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int principal(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(PRINCIPAL);
    }

    public static long createdAt(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(CREATED_AT);
    }

    public static UUID lender(ItemStack stack) {
        String value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(LENDER);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static int interestAt(ItemStack stack, long gameTime) {
        long elapsed = Math.max(0L, gameTime - createdAt(stack));
        return Math.max(0, (int) (elapsed / LenderRoleHandler.INTEREST_INTERVAL_TICKS) * LenderRoleHandler.INTEREST_STEP);
    }

    public static int totalDue(ItemStack stack, long gameTime) {
        return Math.max(0, principal(stack) + interestAt(stack, gameTime));
    }

    public static void updateInterest(ItemStack stack, long gameTime) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(INTEREST, interestAt(stack, gameTime));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static long lastReminder(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(LAST_REMINDER);
    }

    public static void markReminder(ItemStack stack, long reminderIndex) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong(LAST_REMINDER, reminderIndex);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(user instanceof ServerPlayer borrower)) {
            return InteractionResultHolder.fail(stack);
        }
        int due = totalDue(stack, level.getGameTime());
        if (due <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        if (!MoneyUtils.cost(borrower, due)) {
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.insufficient", due)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        LenderRoleHandler.payLender(borrower, lender(stack), due);
        stack.shrink(1);
        borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.repaid", due)
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int principal = principal(stack);
        if (principal > 0) {
            tooltip.add(Component.translatable("item.noellesroles.loan_contract.amount", principal)
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("item.noellesroles.loan_contract.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
