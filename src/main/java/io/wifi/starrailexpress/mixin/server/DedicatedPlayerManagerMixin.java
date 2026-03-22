package io.wifi.starrailexpress.mixin.server;

import net.minecraft.server.dedicated.DedicatedPlayerList;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DedicatedPlayerList.class)
public class DedicatedPlayerManagerMixin {
//    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/DedicatedPlayerList;setViewDistance(I)V"))
//    public void tmm$forceServerViewDistance(DedicatedPlayerList instance, int i, Operation<Void> original) {
//        original.call(instance, 8);
//    }
}