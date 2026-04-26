package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;

/**
 * CUSTOM PENDING HUD Mixin
 * 
 * 显示当前可以打开UI选择职业的信息
 */
public class CustomPendingHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(SpecialGameModeRoles.CUSTOM_PENDING.identifier(),
                (context, deltaTracker) -> {
                    Minecraft client = Minecraft.getInstance();
                    if (SREClient.isPlayerSpectator())
                        return;
                    // "display.type.role."
                    // killer
                    // innocent
                    // neutral
                    // neutral_for_killer
                    // vigilantehud.custom_pending.already
                    var ccccca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(client.player);
                    int roleType = ccccca.getTeam();
                    Component teamName = Component.translatable("Unknown").withStyle(ChatFormatting.GRAY);
                    if (roleType == 1) {
                        teamName = Component.translatable("display.type.role.innocent").withStyle(ChatFormatting.GREEN);
                    } else if (roleType == 2) {
                        teamName = Component.translatable("display.type.role.neutral").withStyle(ChatFormatting.YELLOW);
                    } else if (roleType == 3) {
                        teamName = Component.translatable("display.type.role.neutral_for_killer")
                                .withStyle(ChatFormatting.LIGHT_PURPLE);
                    } else if (roleType == 4) {
                        teamName = Component.translatable("display.type.role.killer").withStyle(ChatFormatting.RED);
                    } else if (roleType == 5) {
                        teamName = Component.translatable("display.type.role.vigilante").withStyle(ChatFormatting.AQUA);
                    }
                    // 渲染位置 - 右下角
                    int screenWidth = client.getWindow().getGuiScaledWidth();
                    int screenHeight = client.getWindow().getGuiScaledHeight();
                    int x = screenWidth - 10; // 距离右边缘
                    int y = screenHeight - 10 - 15 * 4; // 距离底部4行

                    Font font = client.font;
                    if (ccccca.selected()) {
                        Component tip = Component
                                .translatable("hud.custom_pending.already")
                                .withStyle(ChatFormatting.GOLD);
                        context.drawString(font, tip, x - font.width(tip), y, 0xFFFFFFFF);
                    } else {

                        Component tip = Component
                                .translatable("hud.custom_pending.keybind",
                                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage())
                                .withStyle(ChatFormatting.GREEN);
                        context.drawString(font, tip, x - font.width(tip), y, 0xFFFFFFFF);
                    }

                    {
                        Component tip = Component.translatable("hud.custom_pending.myteam", teamName)
                                .withStyle(ChatFormatting.GREEN);
                        context.drawString(font, tip, x - font.width(tip), y + 15, 0xFFFFFFFF);
                    }
                    if (client.player.hasEffect(ModEffects.SAFE_TIME)) {
                        int roleDrawLeft = client.player.getEffect(ModEffects.SAFE_TIME).getDuration();
                        // 显示技能冷却
                        int cdSeconds = roleDrawLeft / 20;
                        Component cdText = Component.translatable("hud.custom_pending.tip",
                                String.format("%d", cdSeconds));

                        // 红色文字表示抽取间隔
                        context.drawString(font, cdText, x - font.width(cdText), y + 15 * 2, CommonColors.RED);

                        Component tip = Component.translatable("hud.custom_pending.tip_2")
                                .withStyle(ChatFormatting.YELLOW);
                        context.drawString(font, tip, x - font.width(tip), y + 15 * 3, 0xFFFFFFFF);
                    }

                });
    }
}