package org.agmas.noellesroles.mixin.client.roles.magician;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.component.ModComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 魔术师HUD渲染Mixin
 * - 在右下角显示"你正在扮演：xxx"（只给魔术师自己看）
 * - 魔术师使用原版疯狂模式，不需要单独渲染
 */
@Mixin(Gui.class)
public abstract class MagicianDisguiseHudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderMagicianDisguiseHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        if (client.player.isSpectator()) {
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        SRERole magicianRole = TMMRoles.ROLES.get(ModRoles.MAGICIAN_ID);
        if (magicianRole == null) {
            // 魔术师角色未注册
            return;
        }

        if (!gameWorld.isRole(client.player, magicianRole)) {
            // 当前玩家不是魔术师
            return;
        }

        var magicianComponent = ModComponents.MAGICIAN.get(client.player);
        if (magicianComponent == null) {
            // 魔术师组件为空，这不应该发生
            return;
        }

        ResourceLocation disguiseId = magicianComponent.getDisguiseRoleId();
        if (disguiseId == null) {
            // 伪装角色ID为空，游戏可能还未完全初始化
            return;
        }

        // 获取伪装角色的翻译
        Component roleText = Component.translatable("announcement.star.role." + disguiseId.getPath());
        Component fullText = Component.translatable("message.magician.cosplay", roleText)
                .withStyle(ChatFormatting.GOLD);

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int textWidth = getFont().width(fullText);

        // 右下角显示，留出一些边距
        int x = screenWidth - textWidth - 10;
        int y = screenHeight - 35;

        context.drawString(getFont(), fullText, x, y, 0xFFD700);
    }
}
