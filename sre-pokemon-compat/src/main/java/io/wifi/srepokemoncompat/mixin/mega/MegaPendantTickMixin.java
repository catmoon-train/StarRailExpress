package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 吊坠到期会消耗物品并生成太乐巴戈斯；关闭时冻结倒计时和生成。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.item.custom.tera.LikosPendant", remap = false)
public abstract class MegaPendantTickMixin {
    // 上游重写原版方法：明确支持 Mojmap 开发名及 Fabric 发行包 intermediary 名。
    @Inject(method = {"inventoryTick", "method_7888"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$pausePendant(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}
