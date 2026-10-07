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

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.customitem.CustomItemCooldownKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自定义列车物品的冷却显示适配。
 *
 * <p>
 * 原版冷却覆盖层就画在 {@code GuiGraphics#renderItemDecorations} 里，用的是
 * {@code getCooldownPercent(stack.getItem(), …)}。自定义物品的冷却条目同样在原版
 * {@link ItemCooldowns} 里，但键是「按自定义物品 id 分配的专属键」，所以这里把查表改成
 * 先解析出这把键 —— <b>画出来的仍然是原版那一层覆盖层</b>，表现与原版物品完全一致，
 * 也没有第二份冷却状态。
 */
@Mixin(GuiGraphics.class)
public class ItemCooldownOverlayMixin {

    /** 原版正在给哪个物品栈画装饰（含冷却覆盖层）。 */
    @Unique
    private ItemStack sre$decoratingStack;

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"))
    private void sre$captureDecoratingStack(Font font, ItemStack stack, int x, int y, String text,
            CallbackInfo ci) {
        this.sre$decoratingStack = stack;
    }

    @Redirect(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/Item;F)F"))
    private float sre$customItemCooldownPercent(ItemCooldowns cooldowns, Item item, float partialTick) {
        Item key = CustomItemCooldownKeys.keyFor(this.sre$decoratingStack);
        return cooldowns.getCooldownPercent(key != null ? key : item, partialTick);
    }

    /** 物品格上的冷却剩余秒数（默认关闭，需在客户端配置里开启）。 */
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void sre$renderCooldownOnItem(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        // 检查开关：默认关闭，需手动开启
        if (!SREClientConfig.instance().showItemCooldownOverlayNum) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (stack.isEmpty()) return;

        ItemCooldowns cooldowns = player.getCooldowns();
        // 自定义列车物品用自己专属的冷却键，其它物品用原版键
        Item customKey = CustomItemCooldownKeys.keyFor(stack);
        Item item = customKey != null ? customKey : stack.getItem();

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