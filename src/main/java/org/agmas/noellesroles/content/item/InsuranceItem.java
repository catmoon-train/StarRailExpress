package org.agmas.noellesroles.content.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.insurance.InsuranceReason;
import org.agmas.noellesroles.packet.InsuranceOpenS2CPacket;
import org.agmas.noellesroles.utils.MoneyUtils;

import java.util.List;

/** The same item represents an unsigned, signed, or active insurance policy. */
public class InsuranceItem extends Item {
    public static final String STATE = "InsuranceState";
    public static final String REASON = "InsuranceReason";
    public static final int UNSIGNED = 0;
    public static final int SIGNED = 1;
    public static final int ACTIVE = 2;

    public InsuranceItem(Properties properties) {
        super(properties);
    }

    public static int state(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(STATE);
    }

    public static InsuranceReason reason(ItemStack stack) {
        return InsuranceReason.fromId(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString(REASON));
    }

    public static boolean isSigned(ItemStack stack) {
        return state(stack) >= SIGNED && reason(stack) != null;
    }

    public static boolean isActive(ItemStack stack) {
        return state(stack) == ACTIVE && reason(stack) != null;
    }

    public static void sign(ItemStack stack, InsuranceReason reason) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(STATE, SIGNED);
        tag.putString(REASON, reason.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void activate(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(STATE, ACTIVE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.fail(stack);

        if (state(stack) == UNSIGNED) {
            ServerPlayNetworking.send(player, new InsuranceOpenS2CPacket(hand));
            return InteractionResultHolder.consume(stack);
        }
        if (isActive(stack)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.insurance.already_active")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!isSigned(stack)) return InteractionResultHolder.fail(stack);
        if (!MoneyUtils.cost(player, 25)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(player, 25);
            return InteractionResultHolder.fail(stack);
        }
        activate(stack);
        player.displayClientMessage(Component.translatable("message.noellesroles.insurance.activated")
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isActive(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String stateKey = switch (state(stack)) {
            case SIGNED -> "signed";
            case ACTIVE -> "active";
            default -> "unsigned";
        };
        tooltip.add(Component.translatable("item.noellesroles.insurance.status." + stateKey)
                .withStyle(ChatFormatting.GRAY));
        InsuranceReason reason = reason(stack);
        if (reason != null) {
            tooltip.add(Component.translatable("item.noellesroles.insurance.reason." + reason.id())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
