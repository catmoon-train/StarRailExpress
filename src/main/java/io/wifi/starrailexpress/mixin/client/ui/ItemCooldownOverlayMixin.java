/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    /**
     * 自定义列车物品的<b>每物品冷却条</b>：和原版冷却覆盖层同样的画法
     * （从槽位顶部往下的半透明黑条，随时间缩短），但进度取自
     * {@link io.wifi.starrailexpress.cca.CustomItemCooldownComponent}（按物品 id 记），
     * 因为自定义物品共用同一个注册物品、原版 {@code ItemCooldowns} 上什么都没有。
     */
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void sre$renderCustomItemCooldownBar(Font font, ItemStack stack, int x, int y, String text,
            CallbackInfo ci) {
        float progress = sre$customItemCooldownPercent(stack);
        if (progress <= 0.0F) {
            return;
        }
        GuiGraphics self = (GuiGraphics) (Object) this;
        int top = y + net.minecraft.util.Mth.floor(16.0F * (1.0F - progress));
        int bottom = top + net.minecraft.util.Mth.ceil(16.0F * progress);
        self.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), x, top, x + 16, bottom, Integer.MAX_VALUE);
    }

    /** 自定义列车物品的每物品冷却进度（1 = 刚开始，0 = 不在冷却 / 不是自定义物品）。 */
    private static float sre$customItemCooldownPercent(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        io.wifi.starrailexpress.customitem.CustomItemData data = io.wifi.starrailexpress.customitem.CustomItemLoader
                .getData(stack);
        if (data == null || data.id == null || data.id.isEmpty()) {
            return 0.0F;
        }
        return io.wifi.starrailexpress.cca.CustomItemCooldownComponent.KEY.get(player).percent(data.id);
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void sre$renderCooldownOnItem(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        // 检查开关：默认关闭，需手动开启
        if (!io.wifi.starrailexpress.SREClientConfig.instance().showItemCooldownOverlayNum) return;

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
