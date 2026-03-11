package org.agmas.noellesroles.mixin.client.roles.nianshou;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;

import org.agmas.noellesroles.component.NianShouPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 年兽HUD，显示红包数量
 */
@Mixin(Gui.class)
public abstract class NianShouHudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    private void renderNianShouHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // 检查是否是年兽
        if (net.minecraft.client.Minecraft.getInstance().player == null)
            return;

        var player = net.minecraft.client.Minecraft.getInstance().player;
        var gameWorld = GameWorldComponent.KEY.get(player.level());

        if (gameWorld == null || !gameWorld.isRole(player, ModRoles.NIAN_SHOU))
            return;

        // 获取红包组件
        var nianShouComponent = NianShouPlayerComponent.KEY.get(player);

        if (nianShouComponent == null)
            return;

        // 渲染红包数量
        int redPacketCount = nianShouComponent.getRedPacketCount();

        var font = getFont();
        int x = guiGraphics.guiWidth() - 10;
        int y = guiGraphics.guiHeight() - 30;

        MutableComponent text = net.minecraft.network.chat.Component
                .translatable("hud.noellesroles.nianshou.red_packets", redPacketCount);

        guiGraphics.drawString(font, text, x - font.width(text), y, 0xFFD700, true);
    }
}
