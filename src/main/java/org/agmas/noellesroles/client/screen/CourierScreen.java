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

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** 信使送信 GUI — 信纸风格，参考便签实现多行编辑 */
public class CourierScreen extends Screen {
    private static final int GUI_W = 256, GUI_H = 210;
    private static final int LINE_TOP = 42;
    private static final int LINE_COUNT = 7;
    private static final int MAX_CHARS = 36;

    private final InteractionHand hand;
    private final String[] lines = new String[]{"", "", "", "", "", "", ""};
    private int currentRow;
    private @Nullable TextFieldHelper textField;
    private int selectedEffect;
    private int selectedItemSlot = -1;
    private Button effectBtn, itemBtn, confirmBtn;
    private boolean showingItems;

    public CourierScreen(InteractionHand hand) {
        super(Component.translatable("screen.noellesroles.courier.title"));
        this.hand = hand;
    }

    private String getFullMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LINE_COUNT; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString().trim();
    }

    @Override
    protected void init() {
        int cx = (width - GUI_W) / 2;
        int cy = (height - GUI_H) / 2;

        if (minecraft != null) {
            textField = new TextFieldHelper(
                    () -> lines[currentRow],
                    s -> lines[currentRow] = s,
                    TextFieldHelper.createClipboardGetter(minecraft),
                    TextFieldHelper.createClipboardSetter(minecraft),
                    s -> minecraft.font.width(s) <= MAX_CHARS * 6
            );
        }

        effectBtn = new BlackTextBtn(cx + 22, cy + GUI_H - 68, 100, 20, getEffectLabel(), b -> cycleEffect());
        addRenderableWidget(effectBtn);

        itemBtn = new BlackTextBtn(cx + GUI_W - 122, cy + GUI_H - 68, 100, 20,
                Component.translatable("screen.noellesroles.courier.item_btn"), b -> showingItems = !showingItems);
        addRenderableWidget(itemBtn);

        confirmBtn = new BlackTextBtn(cx + GUI_W / 2 - 40, cy + GUI_H - 30, 80, 20,
                Component.translatable("screen.noellesroles.courier.confirm"), b -> confirmSend());
        addRenderableWidget(confirmBtn);
    }

    private static class BlackTextBtn extends Button {
        BlackTextBtn(int x, int y, int w, int h, Component msg, OnPress onPress) { super(x, y, w, h, msg, onPress, DEFAULT_NARRATION); }
        @Override public void renderString(GuiGraphics g, net.minecraft.client.gui.Font font, int color) {
            g.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0x000000);
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
        String msg = getFullMessage();
        if (msg.isEmpty() && selectedEffect == 0 && selectedItemSlot < 0) return;
        Minecraft.getInstance().setScreen(new CourierPlayerSelectScreen(hand, msg, selectedEffect, selectedItemSlot));
    }

    // ── 键盘输入（与便签一致）──
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (textField == null) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_UP && currentRow > 0) {
            currentRow--;
            textField.setCursorToEnd();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN && currentRow < LINE_COUNT - 1) {
            currentRow++;
            textField.setCursorToEnd();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (currentRow < LINE_COUNT - 1) {
                currentRow++;
                textField.setCursorToEnd();
            }
            return true;
        }
        if (textField.keyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (textField != null) {
            textField.charTyped(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 点击行选择
        int cx = (width - GUI_W) / 2;
        int cy = (height - GUI_H) / 2;
        int relY = (int) (my - cy - LINE_TOP);
        int clickedRow = relY / 14;
        if (clickedRow >= 0 && clickedRow < LINE_COUNT && textField != null) {
            currentRow = clickedRow;
            textField.setCursorToEnd();
        }

        if (showingItems && minecraft != null && minecraft.player != null) {
            int sx = cx + 25, sy = cy + GUI_H - 120;
            for (int i = 0; i < 9; i++) {
                int ix = sx + i * 21;
                if (mx >= ix && mx <= ix + 18 && my >= sy && my <= sy + 18) {
                    ItemStack s = minecraft.player.getInventory().getItem(i);
                    if (!s.isEmpty() && !isFilteredItem(s)) {
                        selectedItemSlot = i;
                        showingItems = false;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
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

        g.drawCenteredString(font, Component.literal("\u2709  ").append(title), cx + GUI_W / 2, cy + 12, 0xFF5B3A1E);
        g.fill(cx + 30, cy + 30, cx + 31, cy + GUI_H - 65, 0xFFCC8888);

        // 横格线 + 文字（基线对齐横线，不超出内框）
        for (int i = 0; i < LINE_COUNT; i++) {
            int lineY = cy + LINE_TOP + i * 14;
            g.fill(cx + 38, lineY, cx + GUI_W - 38, lineY + 1, 0xFFD4C4A8);
            int textY = lineY - font.lineHeight + 1;
            g.drawString(font, lines[i], cx + 38, textY, currentRow == i ? 0xFF3B2312 : 0xFF5B3A1E);
        }

        // 光标
        if (textField != null && currentRow < LINE_COUNT) {
            int cursorX = cx + 38 + font.width(lines[currentRow]);
            int cursorLineY = cy + LINE_TOP + currentRow * 14;
            g.fill(cursorX, cursorLineY - font.lineHeight + 1, cursorX + 1, cursorLineY, 0xFF000000);
        }

        g.fill(cx + 20, cy + GUI_H - 70, cx + GUI_W - 20, cy + GUI_H - 69, 0xFFC4A882);

        // 物品选择
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
        if (stack.getItem() instanceof org.agmas.noellesroles.content.item.CourierMailItem) return true;
        for (var predicate : LimitedHandledScreen.NotAllowItemTakePredicates) {
            if (predicate.test(stack)) return true;
        }
        return false;
    }
}
