package org.agmas.noellesroles.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** 多行文本框，支持回车换行，文字在横线上方，每行限制字宽。 */
public class MultiLineEditBox extends AbstractWidget {
    private final Font font;
    private final int maxCharsPerLine;
    private final int lineCount;
    private int cursorLine;
    private int cursorPos;
    private boolean focused;
    private int tickCount;

    /** 每行文本 */
    private final List<StringBuilder> lines = new ArrayList<>();

    public MultiLineEditBox(Font font, int x, int y, int maxCharsPerLine, int lineCount) {
        super(x, y, 0, lineCount * 14, Component.empty());
        this.font = font;
        this.maxCharsPerLine = maxCharsPerLine;
        this.lineCount = lineCount;
        this.active = true;
        // 至少有一行
        lines.add(new StringBuilder());
        cursorLine = 0;
        cursorPos = 0;
    }

    /** 获取全部文字（多行合并，用换行符分隔） */
    public String getValue() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** 设置文字 */
    public void setValue(String text) {
        lines.clear();
        for (String part : text.split("\n", -1)) {
            lines.add(new StringBuilder(part));
        }
        if (lines.isEmpty()) lines.add(new StringBuilder());
        cursorLine = 0;
        cursorPos = 0;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        super.setFocused(focused);
    }

    @Override
    public boolean isFocused() { return focused; }

    private int currentLineLength() {
        if (cursorLine >= lines.size()) return 0;
        return lines.get(cursorLine).length();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        if (cursorLine >= lines.size()) {
            cursorLine = lines.size() - 1;
            cursorPos = currentLineLength();
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                // 回车：在光标位置断行
                if (lines.size() >= lineCount) return true;
                StringBuilder cur = lines.get(cursorLine);
                String tail = cur.substring(cursorPos);
                cur.delete(cursorPos, cur.length());
                // 插入新行
                cursorLine++;
                lines.add(cursorLine, new StringBuilder(tail));
                cursorPos = 0;
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorPos > 0) {
                    lines.get(cursorLine).deleteCharAt(cursorPos - 1);
                    cursorPos--;
                } else if (cursorLine > 0) {
                    // 合并到上一行
                    String curText = lines.get(cursorLine).toString();
                    lines.remove(cursorLine);
                    cursorLine--;
                    cursorPos = lines.get(cursorLine).length();
                    if (lines.get(cursorLine).length() + curText.length() <= maxCharsPerLine) {
                        lines.get(cursorLine).append(curText);
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursorPos < currentLineLength()) {
                    lines.get(cursorLine).deleteCharAt(cursorPos);
                } else if (cursorLine < lines.size() - 1) {
                    // 合并下一行
                    String next = lines.get(cursorLine + 1).toString();
                    lines.remove(cursorLine + 1);
                    lines.get(cursorLine).append(next);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorPos > 0) {
                    cursorPos--;
                } else if (cursorLine > 0) {
                    cursorLine--;
                    cursorPos = currentLineLength();
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorPos < currentLineLength()) {
                    cursorPos++;
                } else if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorPos = 0;
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorPos = Mth.clamp(cursorPos, 0, currentLineLength());
                }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorPos = Mth.clamp(cursorPos, 0, currentLineLength());
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> { cursorPos = 0; return true; }
            case GLFW.GLFW_KEY_END -> { cursorPos = currentLineLength(); return true; }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;

        if (cursorLine >= lines.size()) {
            cursorLine = lines.size() - 1;
            cursorPos = currentLineLength();
        }

        // 过滤控制字符，允许中文
        if (chr < 32 && chr != '\n') return false;
        if (chr == '\n') return false; // 换行用 Enter 键

        StringBuilder cur = lines.get(cursorLine);
        if (cur.length() >= maxCharsPerLine) {
            // 自动换行
            if (lines.size() >= lineCount) return true;
            String tail = cur.substring(cursorPos);
            cur.delete(cursorPos, cur.length());
            cursorLine++;
            lines.add(cursorLine, new StringBuilder().append(chr).append(tail));
            cursorPos = 1;
            return true;
        }

        cur.insert(cursorPos, chr);
        cursorPos++;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (!this.visible) return false;
        boolean clicked = this.isMouseOver(mx, my);
        if (clicked) {
            this.setFocused(true);
            int relY = (int) (my - this.getY());
            int line = Mth.clamp(relY / 14, 0, lines.size() - 1);
            cursorLine = line;
            cursorPos = currentLineLength();
        } else {
            this.setFocused(false);
        }
        return clicked;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        tickCount++;
        int textColor = 0x3B2312;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i).toString();
            int ly = this.getY() + i * 14;
            g.drawString(font, text, this.getX(), ly + 1, textColor);
        }
        // 光标闪烁
        if (focused && tickCount / 6 % 2 == 0 && cursorLine < lines.size()) {
            StringBuilder cur = lines.get(cursorLine);
            String beforeCursor = cur.substring(0, Math.min(cursorPos, cur.length()));
            int cursorX = this.getX() + font.width(beforeCursor);
            int cursorY = this.getY() + cursorLine * 14;
            g.fill(cursorX, cursorY, cursorX + 1, cursorY + font.lineHeight, 0xFF000000);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
