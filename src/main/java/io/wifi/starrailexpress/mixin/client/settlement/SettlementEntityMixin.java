package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 不让任何真实实体、职业假身、名字或影子进入舞台；独立 PlayerModel 不经过此处。 */
@Mixin(value = EntityRenderDispatcher.class, priority = 2500)
public abstract class SettlementEntityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void settlement$entities(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }
}
