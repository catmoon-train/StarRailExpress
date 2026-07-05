package io.wifi.starrailexpress.mixin.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public class ItemCooldownOverlayMixin {

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void sre$renderCooldownOnItem(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        // 检查开关：默认关闭，需手动开启
        if (!io.wifi.starrailexpress.SREClientConfig.instance().showItemCooldownOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (stack.isEmpty()) return;

        ItemCooldowns cooldowns = player.getCooldowns();
        Item item = stack.getItem();

        if (!cooldowns.isOnCooldown(item)) return;

        ItemCooldowns.CooldownInstance instance = cooldowns.cooldowns.get(item);
        if (instance == null) return;

        int remainingTicks = instance.endTime - cooldowns.tickCount;
        if (remainingTicks <= 0) return;

        float remainingSeconds = remainingTicks / 20.0f;

        String cooldownText;
        if (remainingSeconds < 10.0f) {
            cooldownText = String.format("%.1f", remainingSeconds);
        } else {
            cooldownText = String.format("%.0f", remainingSeconds);
        }

        GuiGraphics self = (GuiGraphics) (Object) this;

        self.pose().pushPose();
        // 将文字层级提升到物品上方
        self.pose().translate(0, 0, 200);

        // 缩放到适合物品槽位的大小
        float scale = 0.55f;
        self.pose().scale(scale, scale, 1f);

        // 缩放后坐标需要反向补偿，居中于16x16的物品槽
        int centerX = (int) ((x + 8) / scale);
        int centerY = (int) ((y + 5) / scale);

        int textWidth = font.width(cooldownText);

        // 先绘制阴影增强可读性
        self.drawString(font, cooldownText,
                centerX - textWidth / 2 + 1,
                centerY + 1,
                0x80000000,
                false);

        // 绘制白色主文字
        self.drawString(font, cooldownText,
                centerX - textWidth / 2,
                centerY,
                0xFFFFFFFF,
                false);

        self.pose().popPose();
    }
}
