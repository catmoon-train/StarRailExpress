package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.vigilante.MagicApprenticeRoleData;

public final class MagicApprenticeHud {
    private static final ResourceLocation MANA_ICON = Noellesroles.id("stamina/mana_potion_icon");
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 4;
    private static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 4;

    private MagicApprenticeHud() {}

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.MAGIC_APPRENTICE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()
                    || AdvancedCameraDirector.shouldOverride()) return;
            MagicApprenticeRoleData data = RoleData.getNullable(MagicApprenticeRoleData.class, client.player);
            if (data == null) return;
            Font font = client.font;
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();

            // 与巫师完全共用魔力条的位置、图标和紫色填充效果。
            int barX = screenWidth / 2 - BAR_WIDTH / 2;
            int barY = screenHeight - 47;
            int iconX = barX - ICON_SIZE - ICON_GAP;
            int iconY = barY - ICON_SIZE / 2 + BAR_HEIGHT / 2;
            context.blitSprite(MANA_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE);
            float maxMana = Math.max(1f, data.maxMana());
            int fillWidth = Math.round(BAR_WIDTH * Mth.clamp(data.mana / maxMana, 0f, 1f));
            context.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xAA000000);
            context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x66000000);
            if (fillWidth > 0) {
                context.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, 0xFF8A4DFF);
            }

            Component spell = Component.translatable("hud.noellesroles.magic_apprentice.spell."
                    + data.selectedSpell.name().toLowerCase());
            Component text = Component.translatable("hud.noellesroles.magic_apprentice.status",
                    spell, Math.round(data.mana), Math.round(maxMana));
            if (data.wandCooldownTicks > 0) text = text.copy().append(Component.translatable(
                    "hud.noellesroles.magic_apprentice.cooldown", (data.wandCooldownTicks + 19) / 20));

            int textRight = screenWidth - 8;
            Component toggleText = Component.translatable(
                    "hud.noellesroles.magic_apprentice.toggle_spell",
                    NoellesrolesClient.nextAbilityBind.getTranslatedKeyMessage());
            context.drawString(font, toggleText, textRight - font.width(toggleText), screenHeight - 46, 0xFFAAAAAA);
            int x = textRight - font.width(text);
            int y = screenHeight - 35;
            context.drawString(font, text, x, y, data.wandCooldownTicks > 0 ? 0xFFFF5555 : 0xFFE6F4FF);
        });
    }
}
