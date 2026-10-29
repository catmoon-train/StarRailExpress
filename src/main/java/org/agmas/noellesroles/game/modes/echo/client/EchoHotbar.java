package org.agmas.noellesroles.game.modes.echo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.content.EchoResourceItem;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoHotbarSlots;

/** 固定三工具与六物资分组显示；资源内容始终读取本人唯一库存快照。 */
public final class EchoHotbar {
    private static final int SLOT = 20, GAP = 2, GROUP_GAP = 8;
    private EchoHotbar() { }
    /** 只有本模式的在局玩家才接管快捷栏。 */
    public static boolean active() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.isSpectator()) return false;
        if (!(io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode)) return false;
        var data = EchoBagClient.data();
        return data != null && data.matchId() != null && data.faction() != null;
    }
    /** 仅占底部28px；其上原版三颗心的位置不绘制分组标签。 */
    public static int reservedHeight() { return 28; }
    /** 返回 true 表示已接管渲染，调用方应取消原版快捷栏。 */
    public static boolean render(GuiGraphics graphics) {
        if (!active()) return false;
        var client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return false;
        var inventory = client.player.getInventory();
        var data = EchoBagClient.data();
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        int toolsWidth = SLOT * 3 + GAP * 2, resourcesWidth = SLOT * 6 + GAP * 5;
        int total = toolsWidth + GROUP_GAP + resourcesWidth;
        int left = (width - total) / 2, top = height - 26;
        int resourcesLeft = left + toolsWidth + GROUP_GAP;
        panel(graphics, left, top, toolsWidth);
        panel(graphics, resourcesLeft, top, resourcesWidth);
        for (int slot = 0; slot < EchoHotbarSlots.TOTAL; slot++) {
            int resource = EchoHotbarSlots.resourceSlot(slot);
            int x = resource < 0 ? left + slot * (SLOT + GAP) : resourcesLeft + resource * (SLOT + GAP);
            boolean chosen = slot == inventory.selected;
            graphics.fillGradient(x, top, x + SLOT, top + SLOT, chosen ? 0xE0342410 : 0xC020160C, chosen ? 0xE01C1207 : 0xB0140D06);
            graphics.renderOutline(x, top, SLOT, SLOT, chosen ? 0xFFFFF4DC : 0xFF6B5232);
            ItemStack stack = inventory.getItem(slot);
            var value = resource >= 0 && data.bag() != null ? data.bag().slots().get(resource) : null;
            if (value != null) stack = EchoResourceItem.project(value);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, top + 2);
                graphics.renderItemDecorations(client.font, stack, x + 2, top + 2,
                        value != null && !value.items().isEmpty() ? Integer.toString(value.items().size()) : null);
            }
            // 绑定提示跟随用户原有数字栏绑定，不额外注册按键。
            small(graphics, client.options.keyHotbarSlots[slot].getTranslatedKeyMessage(), x + 1, top + 1, 0xFFFFF4DC);
            if (value != null && value.reservation() != null) {
                small(graphics, Component.translatable("echo_hotbar.reserved_short"), x + 11, top + 11, 0xFFD4AF37);
            } else if (value != null && value.items().stream().anyMatch(i -> data.bag().locks().containsKey(i.instanceId()))) {
                small(graphics, Component.translatable("echo_hotbar.locked"), x + 1, top + 11, 0xFFE06B65);
            }
        }
        return true;
    }
    private static void panel(GuiGraphics g, int x, int y, int width) {
        g.fillGradient(x - 2, y - 2, x + width + 2, y + SLOT + 2, 0xD81A1008, 0xD820140A);
        g.renderOutline(x - 2, y - 2, width + 4, SLOT + 4, 0xFF8B6914);
        g.fill(x - 1, y - 1, x + width + 1, y, 0x33FFE8C0);
    }
    private static void small(GuiGraphics g, Component text, int x, int y, int color) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(0.65F, 18F / Math.max(1, font.width(text)));
        g.pose().pushPose(); g.pose().translate(x, y, 200); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true); g.pose().popPose();
    }
}
