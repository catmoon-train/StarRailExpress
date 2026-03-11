package io.wifi.starrailexpress.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DedicatedPlayerList.class)
public class DedicatedPlayerManagerMixin {
//    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/DedicatedPlayerList;setViewDistance(I)V"))
//    public void tmm$forceServerViewDistance(DedicatedPlayerList instance, int i, Operation<Void> original) {
//        original.call(instance, 8);
//    }
}