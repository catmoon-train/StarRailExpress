package org.agmas.noellesroles.mixin.client.roles.waterghost;

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
import org.agmas.noellesroles.component.WaterGhostPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class WaterGhostHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderWaterGhostHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        // 检查是否是水鬼角色
        if (SREClient.gameComponent == null) {
            return;
        }
        if (!SREClient.gameComponent.isRole(player, ModRoles.WATER_GHOST)) {
            return;
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        WaterGhostPlayerComponent component = WaterGhostPlayerComponent.KEY.get(player);

        // 渲染位置 - 右下角（往右靠）
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 120;  // 距离右边缘（更靠近右边）
        int y = screenHeight - 40;  // 距离底部

        Font font = client.font;

        // 显示干涸死亡倒计时（在上）
        int dryDeathRemaining = component.getDryDeathRemaining();
        if (dryDeathRemaining > 0) {
            // 根据剩余时间选择颜色
            ChatFormatting color = ChatFormatting.WHITE;
            if (dryDeathRemaining <= 30) {
                color = ChatFormatting.RED;
            } else if (dryDeathRemaining <= 60) {
                color = ChatFormatting.YELLOW;
            }

            Component dryDeathText = Component.translatable("hud.noellesroles.water_ghost.dry_death", dryDeathRemaining)
                    .withStyle(color);
            guiGraphics.drawString(font, dryDeathText, x, y, 0xFFFFFF);
        }

        // 显示技能冷却时间（在下）
        int cooldownRemaining = component.getSkillCooldownRemaining();
        if (cooldownRemaining > 0) {
            Component cooldownText = Component.translatable("hud.noellesroles.water_ghost.skill_cooldown", cooldownRemaining)
                    .withStyle(ChatFormatting.RED);
            guiGraphics.drawString(font, cooldownText, x, y + 12, 0xFFFFFF);
        } else {
            // 技能可用
            Component readyText = Component.translatable("hud.noellesroles.water_ghost.skill_ready")
                    .withStyle(ChatFormatting.GREEN);
            guiGraphics.drawString(font, readyText, x, y + 12, 0xFFFFFF);
        }
    }
}
