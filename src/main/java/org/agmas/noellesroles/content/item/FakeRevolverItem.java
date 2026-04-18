package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.RevolverItem;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class FakeRevolverItem extends RevolverItem {
    public FakeRevolverItem(Properties settings) {
        super(settings);
    }

    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND)
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        if (user.getItemInHand(hand).getDamageValue() < user.getItemInHand(hand).getMaxDamage()) {
            user.getItemInHand(hand).hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            if (world.isClientSide) {
                ClientPlayNetworking.send(new GunShootPayload(-1));
                user.setXRot(user.getXRot() - 4.0F);
                spawnHandParticle();
            }
        }
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }
}
