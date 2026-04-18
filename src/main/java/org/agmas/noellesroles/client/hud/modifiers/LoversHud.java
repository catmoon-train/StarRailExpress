package org.agmas.noellesroles.client.hud.modifiers;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

public abstract class LoversHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((context, deltaTracker) -> {
            final Minecraft client = Minecraft.getInstance();
            var clientPlayer = client.player;
            if (clientPlayer == null)
                return;
            final Font renderer = client.font;
            var component = LoversComponent.KEY.get(clientPlayer);
            var config = StupidExpress.CONFIG;
            if (component.isLover()
                    && !SREClient.isPlayerSpectatingOrCreative()) {
                context.pose().pushPose();

                var loverInfo = clientPlayer.connection.getPlayerInfo(component.getLover());
                if (loverInfo == null)
                    return;

                var textYPos = context.guiHeight() - 12;
                var textXPos = 18;

                Component name;
                if (!config.modifiersSection.loversSection.loversKnowImmediately) {
                    name = Component.translatable("hud.stupid_express.lovers.notification");
                    textXPos -= 14;
                } else {
                    name = Component.translatable("tip.stupid_express.lovers.partner",
                            loverInfo.getProfile().getName());
                }

                var role = SREClient.getCachedPlayerRole();
                if (role != null) {
                    if (role.identifier().equals(ModRoles.EXECUTIONER_ID)) {
                        textYPos -= 15;
                    }
                }
                if (config.modifiersSection.loversSection.loversKnowImmediately) {
                    context.drawPlayerFace(loverInfo.getSkin().texture(), 2,
                            textYPos - 2, 12);
                }
                context.drawString(renderer, name, textXPos, textYPos, SEModifiers.LOVERS.color());

                context.pose().popPose();
            }
        });
    }
}
