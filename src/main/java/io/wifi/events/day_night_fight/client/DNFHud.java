package io.wifi.events.day_night_fight.client;

import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFClockItem;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.events.day_night_fight.DNFRoles;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class DNFHud {
    public static void register() {
        // 杀手HUD
        RoleHudRenderCallback.EVENT.register(DNFRoles.KILLER_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            DNFPlayerComponent component = DNFPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int x = 8;
            int y = context.guiHeight() - 42;
            Component blood = Component.translatable("hud.dnf.blood", component.getBlood());
            Component bodies = Component.translatable("hud.dnf.bodies", component.getBodiesEaten());
            context.drawString(font, blood, x, y, 0xFFFF5555, true);
            context.drawString(font, bodies, x, y + 10, 0xFFAA0000, true);
        });

        // 通用HUD - 时钟和衣物状态
        registerCommonHud((context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null || !DNF.isDayNightFightMode(client.level)) {
                return;
            }

            Font font = client.font;
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();
            DNFUnderworldComponent underworld = DNFUnderworldComponent.KEY.get(client.player);

            if (underworld.isWaitingRoom()) {
                Component hint = Component.translatable("hud.dnf.underworld.waiting_room")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE);
                int hintWidth = font.width(hint);
                context.drawString(font, hint, screenWidth / 2 - hintWidth / 2, screenHeight / 2 - 30, -1, true);
            } else if (underworld.isInUnderworld()) {
                int minutes = underworld.getRemainingMinutes();
                int seconds = underworld.getRemainingSeconds();
                Component timerText = Component.translatable("hud.dnf.underworld.timer", minutes,
                        String.format("%02d", seconds)).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE);
                int textWidth = font.width(timerText);
                int yTimer = screenHeight / 2 - 30;
                context.drawString(font, timerText, screenWidth / 2 - textWidth / 2, yTimer, -1, true);

                Component hint = Component.translatable("hud.dnf.underworld.hint")
                        .withStyle(net.minecraft.ChatFormatting.GRAY);
                int hintWidth = font.width(hint);
                context.drawString(font, hint, screenWidth / 2 - hintWidth / 2, yTimer + 12, -1, true);
            }
            
            // 检查是否手持时钟
            ItemStack mainHand = client.player.getMainHandItem();
            ItemStack offHand = client.player.getOffhandItem();
            boolean hasClock = mainHand.is(DNFItems.DNF_CLOCK) || offHand.is(DNFItems.DNF_CLOCK);
            
            int y = screenHeight - 60;
            
            // 如果手持时钟,显示天数和时间
            if (hasClock) {
                Component clockText = DNFClockItem.getClockDisplayText(client.player);
                int textWidth = font.width(clockText);
                int x = screenWidth / 2 - textWidth / 2;
                context.drawString(font, clockText, x, y, 0xFFFFFF, true);
                y += 12;
            }
            
            // 显示衣物肮脏程度
            float dirtiness = DNFItems.getClothingDirtiness(client.player);
            if (dirtiness > 0) {
                int dirtinessPercent = (int) (dirtiness * 100);
                Component clothesText;
                
                if (!DNFItems.isWearingDnfClothes(client.player)) {
                    clothesText = Component.translatable("hud.dnf.clothes.missing").withStyle(net.minecraft.ChatFormatting.RED);
                } else if (dirtinessPercent >= 60) {
                    clothesText = Component.translatable("hud.dnf.clothes.dirty", dirtinessPercent).withStyle(net.minecraft.ChatFormatting.DARK_RED);
                } else if (dirtinessPercent >= 30) {
                    clothesText = Component.translatable("hud.dnf.clothes.slightly_dirty", dirtinessPercent).withStyle(net.minecraft.ChatFormatting.YELLOW);
                } else {
                    clothesText = Component.translatable("hud.dnf.clothes.clean").withStyle(net.minecraft.ChatFormatting.GREEN);
                }
                
                int textWidth = font.width(clothesText);
                int x = screenWidth / 2 - textWidth / 2;
                context.drawString(font, clothesText, x, y, -1, true);
            }
        });
    }

    /**
     * 注册通用HUD渲染(不特定于某个职业)
     */
    private static void registerCommonHud(BiConsumer<FakeGuiGraphics, DeltaTracker> renderer) {
        // 为所有DNF职业注册
        RoleHudRenderCallback.EVENT.register(DNFRoles.KILLER_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.CIVILIAN_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.CHEF_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.SOLDIER_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.PSYCHOLOGIST_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.LOCKSMITH_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.MANIAC_ID, renderer::accept);
        RoleHudRenderCallback.EVENT.register(DNFRoles.DNF_ABYSS_ID, renderer::accept);
    }
}
