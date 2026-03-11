package pro.fazeclan.river.stupid_express.client.mixin.role.initiate;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.client.gui.StoreRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SERoles;

@Mixin(StoreRenderer.class)
public class InitiateCoinMixin {

    @Shadow
    public static StoreRenderer.MoneyNumberRenderer view;

    @Shadow
    public static float offsetDelta;

    @Inject(
            method = "renderHud",
            at = @At("HEAD")
    )
    private static void renderInitiateCoins(
            Font renderer,
            LocalPlayer player,
            GuiGraphics context,
            float delta,
            CallbackInfo ci
    ) {
        if (GameWorldComponent.KEY.get(player.level()).isRole(player, SERoles.INITIATE)) {
            int balance = PlayerShopComponent.KEY.get(player).balance;
            if (view.getTarget() != (float) balance) {
                offsetDelta = (float) balance > view.getTarget() ?  0.6f : -0.6f;
                view.setTarget((float) balance);
            }

            float r = offsetDelta > 0.0f ? 1.0f - offsetDelta : 1.0f;
            float g = offsetDelta < 0.0f ? 1.0f + offsetDelta : 1.0f;
            float b = 1.0f - Math.abs(offsetDelta);
            int color = Mth.color(r, g, b) | -16777216;
            context.pose().pushPose();
            context.pose().translate((float) (context.guiWidth() - 12), 6.0f, 0.0f);
            view.render(renderer, context, 0, 0, color, delta);
            context.pose().popPose();
            offsetDelta = Mth.lerp(delta / 16.0f, offsetDelta, 0.0f);
        }
    }

}
