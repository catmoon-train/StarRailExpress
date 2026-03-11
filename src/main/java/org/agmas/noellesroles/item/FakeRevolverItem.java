package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.item.RevolverItem;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

public class FakeRevolverItem extends RevolverItem {
    public FakeRevolverItem(Properties settings) {
        super(settings);
    }
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        if (hand==InteractionHand.OFF_HAND)return InteractionResultHolder.pass(user.getItemInHand(hand));
        if (user.getItemInHand(hand).getDamageValue() < user.getItemInHand(hand).getMaxDamage()) {
            user.getItemInHand(hand).hurtAndBreak(1,user, EquipmentSlot.MAINHAND);
            if (world.isClientSide) {
                HitResult collision = getGunTarget(user);
                if (collision instanceof EntityHitResult) {
                    EntityHitResult entityHitResult = (EntityHitResult) collision;
                    Entity target = entityHitResult.getEntity();
                    ClientPlayNetworking.send(new GunShootPayload(target.getId()));
                } else {
                    ClientPlayNetworking.send(new GunShootPayload(-1));
                }

                user.setXRot(user.getXRot() - 4.0F);
                spawnHandParticle();
            }
        }
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }
}
