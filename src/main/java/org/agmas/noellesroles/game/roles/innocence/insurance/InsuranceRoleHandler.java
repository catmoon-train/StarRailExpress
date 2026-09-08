package org.agmas.noellesroles.game.roles.innocence.insurance;

import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.InsuranceItem;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

/** Server-side signing and one-hit protection logic for insurance policies. */
public final class InsuranceRoleHandler {
    private static boolean registered;

    private InsuranceRoleHandler() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        AllowPlayerDeath.EVENT.register(InsuranceRoleHandler::allowDeath);
    }

    /** Used by both the client drop-key guard and the server inventory-drop guard. */
    public static boolean isInsuranceClerk(net.minecraft.world.entity.player.Player player) {
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.INSURANCE);
    }

    public static boolean canDrop(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        return !stack.is(ModItems.INSURANCE) || isInsuranceClerk(player);
    }

    public static void submit(ServerPlayer player, InteractionHand hand, String reasonId) {
        ItemStack stack = player.getItemInHand(hand);
        InsuranceReason reason = InsuranceReason.fromId(reasonId);
        if (!stack.is(ModItems.INSURANCE) || reason == null || InsuranceItem.isSigned(stack)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.insurance.invalid")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        InsuranceItem.sign(stack, reason);
        player.displayClientMessage(Component.translatable("message.noellesroles.insurance.signed")
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static boolean allowDeath(net.minecraft.world.entity.player.Player victim, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer player)) return true;
        if (deathReason == null || deathReason.equals(GameConstants.DeathReasons.SHOT_INNOCENT)) return true;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ModItems.INSURANCE) || !InsuranceItem.isActive(stack)) continue;
            InsuranceReason insuredReason = InsuranceItem.reason(stack);
            if (insuredReason == null || !insuredReason.matches(deathReason)) continue;

            stack.shrink(1);
            MoneyUtils.addToBalance(player, 25);
            player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.noellesroles.insurance.protected")
                    .withStyle(ChatFormatting.GREEN), true);
            return false;
        }
        return true;
    }
}
