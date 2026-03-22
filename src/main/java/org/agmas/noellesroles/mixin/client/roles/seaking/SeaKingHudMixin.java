package org.agmas.noellesroles.mixin.client.roles.seaking;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class SeaKingHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderSeaKingHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            return;
        }

        if (SREClient.gameComponent == null) {
            return;
        }
        if (!SREClient.gameComponent.isRole(player, ModRoles.SEA_KING)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability == null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 120;
        int y = screenHeight - 40;
        Font font = client.font;

        if (ability.cooldown > 0) {
            int seconds = (ability.cooldown + 19) / 20;
            Component cooldownText = Component.translatable("hud.noellesroles.sea_king.skill_cooldown", seconds)
                    .withStyle(ChatFormatting.RED);
            guiGraphics.drawString(font, cooldownText, x, y, 0xFFFFFF);
        } else {
            Component readyText = Component.translatable("hud.noellesroles.sea_king.skill_ready")
                    .withStyle(ChatFormatting.GREEN);
            guiGraphics.drawString(font, readyText, x, y, 0xFFFFFF);
        }
    }
}
