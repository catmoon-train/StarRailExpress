package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.MultiLineEditBox;
import org.jetbrains.annotations.NotNull;

/** 信使送信 GUI — 信纸风格 */
public class CourierScreen extends Screen {
    private static final int GUI_W = 256, GUI_H = 210;
    private static final int LINE_TOP = 55;
    private static final int LINE_COUNT = 7;

    private final InteractionHand hand;
    private MultiLineEditBox messageBox;
    private int selectedEffect = 0;
    private int selectedItemSlot = -1;
    private Button effectBtn, itemBtn, confirmBtn;
    private boolean showingItems;

    public CourierScreen(InteractionHand hand) {
        super(Component.translatable("screen.noellesroles.courier.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        int cx = (width - GUI_W) / 2;
        int cy = (height - GUI_H) / 2;

        // 写信区——多行文本框，36字/行，7行
        messageBox = new MultiLineEditBox(font, cx + 38, cy + LINE_TOP, 36, LINE_COUNT);
        addRenderableWidget(messageBox);

        effectBtn = buildBlackTextBtn(cx + 22, cy + GUI_H - 68, 100, 20, getEffectLabel(), btn -> cycleEffect());
        addRenderableWidget(effectBtn);

        itemBtn = buildBlackTextBtn(cx + GUI_W - 122, cy + GUI_H - 68, 100, 20,
                Component.translatable("screen.noellesroles.courier.item_btn"), btn -> showingItems = !showingItems);
        addRenderableWidget(itemBtn);

        confirmBtn = buildBlackTextBtn(cx + GUI_W / 2 - 40, cy + GUI_H - 30, 80, 20,
                Component.translatable("screen.noellesroles.courier.confirm"), btn -> confirmSend());
        addRenderableWidget(confirmBtn);
    }

    /** 创建黑色文字的按钮 */
    private static Button buildBlackTextBtn(int x, int y, int w, int h, Component msg, Button.OnPress onPress) {
        return new BlackTextButton(x, y, w, h, msg, onPress);
    }

    private static class BlackTextButton extends Button {
        BlackTextButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        }
        @Override
        public void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int color) {
            g.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2, 0x000000);
        }
    }

    private Component getEffectLabel() {
        return switch (selectedEffect) {
            case 1 -> Component.translatable("screen.noellesroles.courier.effect.san");
            case 2 -> Component.translatable("screen.noellesroles.courier.effect.speed");
            case 3 -> Component.translatable("screen.noellesroles.courier.effect.disguise");
            default -> Component.translatable("screen.noellesroles.courier.effect.none");
        };
    }

    private void cycleEffect() {
        selectedEffect = (selectedEffect + 1) % 4;
        effectBtn.setMessage(getEffectLabel());
    }

    private void confirmSend() {
        String msg = messageBox.getValue().trim();
        // 空信件禁止发送
        if (msg.isEmpty() && selectedEffect == 0 && selectedItemSlot < 0) {
            return;
        }
        Minecraft.getInstance().setScreen(new CourierPlayerSelectScreen(hand, msg, selectedEffect, selectedItemSlot));
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        int cx = (width - GUI_W) / 2;
        int cy = (height - GUI_H) / 2;

        // 信纸背景
        g.fill(cx, cy, cx + GUI_W, cy + GUI_H, 0xFFF5E6C8);
        g.renderOutline(cx, cy, GUI_W, GUI_H, 0xFFA08060);
        g.renderOutline(cx + 6, cy + 6, GUI_W - 12, GUI_H - 12, 0xFFC4A882);

        // 标题
        g.drawCenteredString(font, Component.literal("\u2709  ").append(title), cx + GUI_W / 2, cy + 12, 0xFF5B3A1E);

        // 红色竖线
        g.fill(cx + 30, cy + 38, cx + 31, cy + GUI_H - 70, 0xFFCC8888);

        // 横格线
        for (int i = 0; i < LINE_COUNT; i++) {
            int lineY = cy + LINE_TOP + i * 14;
            g.fill(cx + 35, lineY, cx + GUI_W - 25, lineY + 1, 0xFFD4C4A8);
        }

        // 底部分隔线
        g.fill(cx + 20, cy + GUI_H - 75, cx + GUI_W - 20, cy + GUI_H - 74, 0xFFC4A882);

        // 物品选择弹窗
        if (showingItems && minecraft != null && minecraft.player != null) {
            int sx = cx + 25, sy = cy + GUI_H - 120;
            g.fill(sx - 4, sy - 4, sx + 200, sy + 60, 0xFFF0E6D0);
            g.renderOutline(sx - 4, sy - 4, 200, 60, 0xFFA08060);
            g.drawString(font, Component.translatable("screen.noellesroles.courier.pick_item"), sx, sy - 14, 0xFF3B2312);
            for (int i = 0; i < 9; i++) {
                ItemStack s = minecraft.player.getInventory().getItem(i);
                if (isFilteredItem(s)) continue;
                int ix = sx + i * 21;
                g.fill(ix, sy, ix + 18, sy + 18, selectedItemSlot == i ? 0xFFAA8866 : 0xFF887766);
                if (!s.isEmpty()) {
                    g.renderItem(s, ix + 1, sy + 1);
                    g.renderItemDecorations(font, s, ix + 1, sy + 1);
                }
            }
        }
    }

    private boolean isFilteredItem(ItemStack stack) {
        if (stack.isEmpty()) return true;
        // 不能选信封自身
        if (stack.getItem() instanceof org.agmas.noellesroles.content.item.CourierMailItem) return true;
        // 不能选传递盒(DELIVERY_BOX)排除的物品
        for (var predicate : LimitedHandledScreen.NotAllowItemTakePredicates) {
            if (predicate.test(stack)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showingItems && minecraft != null && minecraft.player != null) {
            int cx = (width - GUI_W) / 2, cy = (height - GUI_H) / 2;
            int sx = cx + 25, sy = cy + GUI_H - 120;
            for (int i = 0; i < 9; i++) {
                int ix = sx + i * 21;
                if (mouseX >= ix && mouseX <= ix + 18 && mouseY >= sy && mouseY <= sy + 18) {
                    ItemStack s = minecraft.player.getInventory().getItem(i);
                    if (!s.isEmpty() && !isFilteredItem(s)) {
                        selectedItemSlot = i;
                        showingItems = false;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
