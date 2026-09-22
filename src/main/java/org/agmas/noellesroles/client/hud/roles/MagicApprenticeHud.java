package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vigilante.MagicApprenticeRoleData;

public final class MagicApprenticeHud {
    private MagicApprenticeHud() {}

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MAGIC_APPRENTICE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()
                    || AdvancedCameraDirector.shouldOverride()) return;
            MagicApprenticeRoleData data = RoleData.getNullable(MagicApprenticeRoleData.class, client.player);
            if (data == null) return;
            Font font = client.font;
            Component spell = Component.translatable("hud.noellesroles.magic_apprentice.spell."
                    + data.selectedSpell.name().toLowerCase());
            Component text = Component.translatable("hud.noellesroles.magic_apprentice.status",
                    spell, Math.round(data.mana), Math.round(data.maxMana()));
            if (data.wandCooldownTicks > 0) text = text.copy().append(Component.translatable(
                    "hud.noellesroles.magic_apprentice.cooldown", (data.wandCooldownTicks + 19) / 20));
            int x = context.guiWidth() - 8 - font.width(text);
            int y = context.guiHeight() - 36;
            context.drawString(font, text, x, y, data.wandCooldownTicks > 0 ? 0xFFFF5555 : 0xFFE6F4FF);

            int barWidth = 120;
            int barX = context.guiWidth() / 2 - barWidth / 2;
            int barY = context.guiHeight() - 24;
            int fill = Mth.floor(barWidth * Mth.clamp(data.mana / data.maxMana(), 0f, 1f));
            context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 6, 0xAA000000);
            context.fill(barX, barY, barX + barWidth, barY + 4, 0x66334455);
            if (fill > 0) context.fill(barX, barY, barX + fill, barY + 4, 0xFF55B8FF);
        });
    }
}
