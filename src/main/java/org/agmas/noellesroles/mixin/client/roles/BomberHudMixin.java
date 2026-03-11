package org.agmas.noellesroles.mixin.client.roles;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.item.BombItem;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class BomberHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderBombTimer(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;
        if (client.player.isSpectator())
            return;
        if (SREClient.gameComponent == null) {
            return;
        }

        ItemStack mainHandItem = player.getMainHandItem();

        if (mainHandItem.is(ModItems.BOMB)) {
            if (!SREClient.gameComponent.isKillerTeam(player)) {
                String text = "!!!BOMB!!!";

                int width = client.getWindow().getGuiScaledWidth();
                int height = client.getWindow().getGuiScaledHeight();

                guiGraphics.drawCenteredString(client.font, text, width / 2, height / 2 - 20, 0xFF0000);
                return;
            }
            CustomData customData = mainHandItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag tag = customData.copyTag();

            if (tag.contains(BombItem.TIMER_KEY)) {
                int timer = tag.getInt(BombItem.TIMER_KEY);
                String text = String.format("%.1fs", timer / 20.0f);

                int width = client.getWindow().getGuiScaledWidth();
                int height = client.getWindow().getGuiScaledHeight();

                guiGraphics.drawCenteredString(client.font, text, width / 2, height / 2 - 20, 0xFF0000);
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderBomberStatus(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;

        // 检查是否是炸弹客
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.BOMBER))
            return;

        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(client.player))
            return;

        // 计算背包中的炸弹数量
        int bombCount = TMMItemUtils.hasItem(client.player, ModItems.BOMB);
        if (client.player.getOffhandItem().is(ModItems.BOMB)) {
            bombCount += client.player.getOffhandItem().getCount();
        }

        // 渲染位置 - 右下角
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 150; // 距离右边缘
        int y = screenHeight - 50; // 距离底部

        Font textRenderer = client.font;

        // 显示炸弹数量
        Component countText = Component.translatable("hud.noellesroles.bomber.count", bombCount);
        context.drawString(textRenderer, countText, x, y, 0xFFFFFF);

        // 显示购买消耗
        Component costText = Component.translatable("hud.noellesroles.bomber.cost");
        context.drawString(textRenderer, costText, x, y + 12, 0xFFFF00);
    }
}