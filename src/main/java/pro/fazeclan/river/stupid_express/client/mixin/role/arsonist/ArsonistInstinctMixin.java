package pro.fazeclan.river.stupid_express.client.mixin.role.arsonist;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import pro.fazeclan.river.stupid_express.constants.SERoles;

/**
 * 杀手本能 Mixin
 *
 * 处理以下功能：
 * 1. 跟踪者：杀手透视时显示跟踪者颜色（跟踪者本身是杀手，无需额外处理 isKiller）
 * 2. 爱慕者：类似小丑，能使用本能侦查，也能被杀手本能侦查到
 * 3. 傀儡师：操控假人时可以使用本能，显示渐变色
 * 4. 杀手本能颜色改为渐变色效果
 */
@Mixin(SREClient.class)
public class ArsonistInstinctMixin {

    @Shadow
    public static KeyMapping instinctKeybind;
    /**
     * 让爱慕者和傀儡师操控假人时可以使用杀手本能（类似小丑）
     */
    @Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
    private static void admirerAndPuppeteerCanUseInstinct(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.player.level());

        // 爱慕者可以使用本能侦查（类似小丑）
        if (gameWorld.isRole(client.player, SERoles.ARSONIST)) {
            if (instinctKeybind.isDown()) {
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }
    }
}