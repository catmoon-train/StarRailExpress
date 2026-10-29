package org.agmas.noellesroles.client.hud.roles;

import java.util.Locale;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.PaperDollRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 纸人受击反应窗口：屏幕中央上方的大字救命提示，只显示给本人。 */
public final class PaperDollHud {
    private PaperDollHud() {
    }

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PAPER_DOLL_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.player.isSpectator() || client.options.hideGui) {
                return;
            }
            PaperDollRoleData data = RoleData.getNullable(PaperDollRoleData.class, client.player);
            int left = data == null ? 0 : data.reactionTicksLeft();
            if (left <= 0) {
                return;
            }
            var font = client.font;
            boolean decoySelected = io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(client.player)
                    .getSelectedSkill() == 0;
            Component warning = (decoySelected
                    ? Component.translatable("hud.noellesroles.paper_doll.react",
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                    : Component.translatable("hud.noellesroles.paper_doll.react_switch",
                            NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage(),
                            NoellesrolesClient.abilityBind.getTranslatedKeyMessage())).withStyle(ChatFormatting.BOLD);
            Component timer = Component.translatable("hud.noellesroles.paper_doll.countdown",
                    String.format(Locale.ROOT, "%.2f", left / 20.0F));
            int center = graphics.guiWidth() / 2;
            int y = graphics.guiHeight() / 2 - 50;
            float scale = Math.min(3.0F, (graphics.guiWidth() - 24.0F) / Math.max(1, font.width(warning)));
            int height = (int) Math.ceil(font.lineHeight * scale);
            graphics.fill(8, y - 8, graphics.guiWidth() - 8, y + height + 26, 0xD81A1008);
            graphics.fill(8, y - 8, graphics.guiWidth() - 8, y - 6, 0xFFE06B65);
            graphics.pose().pushPose();
            graphics.pose().translate(center, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, warning, -font.width(warning) / 2, 0, 0xFFFFE8C0, true);
            graphics.pose().popPose();
            graphics.drawCenteredString(font, timer, center, y + height + 4, 0xFFFF8A80);
            int width = Math.min(220, graphics.guiWidth() - 40);
            int barY = y + height + 18;
            graphics.fill(center - width / 2, barY, center + width / 2, barY + 3, 0xFF48201D);
            graphics.fill(center - width / 2, barY,
                    center - width / 2 + width * left / PaperDollRoleData.REACTION_TICKS, barY + 3, 0xFFE06B65);
        });
    }
}
