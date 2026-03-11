package pro.fazeclan.river.stupid_express.client.mixin.modifier.lovers;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.client.StupidExpressClient;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

@Mixin(RoleNameRenderer.class)
public abstract class LoversHudMixin {

    @Shadow
    private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void loversHud(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {

        var clientPlayer = Minecraft.getInstance().player;
        var clientWorld = clientPlayer.level();

        var component = LoversComponent.KEY.get(clientPlayer);
        var config = StupidExpress.CONFIG;
        if (component.isLover()
                && !SREClient.isPlayerSpectatingOrCreative()) {
            context.pose().pushPose();

            var loverInfo = clientPlayer.connection.getPlayerInfo(component.getLover());
            if (loverInfo == null) return;

            var textYPos = context.guiHeight() - 12;
            var textXPos = 18;

            Component name;
            if (!config.modifiersSection.loversSection.loversKnowImmediately) {
                name = Component.translatable("hud.stupid_express.lovers.notification");
                textXPos -= 14;
            } else {
                name = Component.translatable("tip.stupid_express.lovers.partner", loverInfo.getProfile().getName());
            }

            var role = GameWorldComponent.KEY.get(clientWorld).getRole(clientPlayer);
            if (role != null) {
                if (GameWorldComponent.KEY.get(clientWorld).getRole(clientPlayer).identifier().equals(ResourceLocation.parse("noellesroles:executioner"))) {
                    textYPos -= 15;
                }
            }
            if (config.modifiersSection.loversSection.loversKnowImmediately) {
                PlayerFaceRenderer.draw(context,loverInfo.getSkin().texture(), 2, textYPos - 2,12);
            }
            context.drawString(renderer, name, textXPos, textYPos, SEModifiers.LOVERS.color());

            context.pose().popPose();
        }
    }

    @Inject(
            method = "renderHud",
            at = @At("TAIL")
    )
    private static void renderLovers(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        var loversComponent = LoversComponent.KEY.get(Minecraft.getInstance().player);
        if (StupidExpressClient.target == null) {
            return;
        }
        var component = LoversComponent.KEY.get(StupidExpressClient.target);
        if (!component.isLover()) {
            return;
        }
        var level = Minecraft.getInstance().level;
        var lover = level.getPlayerByUUID(component.getLover());
        if (lover == null) {
            return;
        }
        var config = StupidExpress.CONFIG;
        if (SREClient.isPlayerAliveAndInSurvival()
                && !config.modifiersSection.loversSection.loversKnowImmediately
                && loversComponent.isLover()) {
            stupidexpress$renderLoversHud(renderer, context, Component.translatable("hud.stupid_express.lovers.partner"));
        }
        if (SREClient.isPlayerSpectatingOrCreative()) {
            stupidexpress$renderLoversHud(renderer, context, Component.translatable(
                    "hud.stupid_express.lovers.in_love",
                    lover.getName()
            ));
        }
    }

    @Unique
    private static void stupidexpress$renderLoversHud(Font renderer, GuiGraphics context, Component component) {

        context.pose().pushPose();
        context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f - 35.0f, 0.0f);
        context.pose().scale(0.6f, 0.6f, 1.0f);

        context.drawString(
                renderer,
                component,
                -renderer.width(component) / 2,
                32,
                SEModifiers.LOVERS.color() | (int) (nametagAlpha * 255.0F) << 24
        );

        context.pose().popPose();
    }

}
