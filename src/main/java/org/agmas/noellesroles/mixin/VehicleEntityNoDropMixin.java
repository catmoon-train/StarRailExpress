package org.agmas.noellesroles.mixin;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;

@Mixin(VehicleEntity.class)
public abstract class VehicleEntityNoDropMixin {
    @WrapOperation(method = "destroy(Lnet/minecraft/world/item/Item;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;spawnAtLocation(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity destroy(VehicleEntity instance, ItemStack stack, Operation<ItemEntity> original) {
        if (SREConfig.instance().vehicleEntityNoItemDrops) {
            return null;
        }
        return original.call(stack);
    }
}
