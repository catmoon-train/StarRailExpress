package org.agmas.noellesroles.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.packet.TerminalCommandC2SPacket;
import org.agmas.noellesroles.role.bouns.roles.ProgrammerRole;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 终端界面：程序员右键终端后打开（由服务端经 OpenScreenManager 下发）。
 *
 * <p>
 * 黑底绿字的命令行风格：指令**必须自己手输**（没有任何可点击的命令列表），
 * 输入 {@code /help} 会在日志里列出所有可用指令，回车执行。
 * 除了 {@code /help}（本地展开），其余输入一律发给服务端判定 —— 输错的话服务端会把终端直接消耗掉。
 *
 * <p>
 * 日志区可以**滚轮翻页**（内容超出显示区域时右侧会出现滚动条），提交新指令后自动回到最新一行。
 */
public class TerminalScreen extends Screen {

    /** 终端绿 */
    private static final int TERMINAL_GREEN = 0xFF3AF07A;
    private static final int PANEL_BG = 0xF00A0F0A;
    private static final int PANEL_BORDER = 0xFF1F7A46;
    private static final int LINE_HEIGHT = 10;
    /** 日志最多保留多少行（防止无限增长） */
    private static final int MAX_LOG_LINES = 400;
    /** 滚轮一格翻几行 */
    private static final int SCROLL_STEP = 3;

    /** 界面日志（字段而不是局部变量，resize 重建界面后内容还在） */
    private final List<Component> log = new ArrayList<>();
    /** 日志滚动偏移：0 = 停在最新一行，越大越往上看 */
    private int scrollOffset = 0;

    private EditBox input;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public TerminalScreen() {
        super(Component.translatable("screen.noellesroles.terminal.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.panelWidth = Math.min(440, this.width - 40);
        this.panelHeight = Math.min(240, this.height - 40);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.input = new EditBox(this.font, this.panelX + 12, this.panelY + this.panelHeight - 26,
                this.panelWidth - 24, 18, Component.translatable("screen.noellesroles.terminal.input_hint"));
        this.input.setMaxLength(120);
        this.input.setHint(Component.translatable("screen.noellesroles.terminal.input_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.input);
        this.setInitialFocus(this.input);

        // 首次打开时的开场日志
        if (this.log.isEmpty()) {
            this.log.add(Component.translatable("screen.noellesroles.terminal.welcome").withStyle(ChatFormatting.GRAY));
            this.log.add(Component.translatable("screen.noellesroles.terminal.help_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            this.log.add(Component.translatable("screen.noellesroles.terminal.warning")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    // ==================== 布局计算 ====================

    /** 日志区顶部 y */
    private int logTop() {
        return this.panelY + 54;
    }

    /** 日志区底部 y（输入框上方） */
    private int logBottom() {
        return this.panelY + this.panelHeight - 34;
    }

    /** 日志区能显示多少行 */
    private int visibleLines() {
        return Math.max(1, (logBottom() - logTop()) / LINE_HEIGHT + 1);
    }

    /** 滚动上限（0 表示内容没有超出） */
    private int maxScrollOffset() {
        return Math.max(0, this.log.size() - visibleLines());
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 终端面板（先画底板，再让 super.render 画输入框，最后画文字）
        guiGraphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight,
                PANEL_BG);
        guiGraphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 1, PANEL_BORDER);
        guiGraphics.fill(this.panelX, this.panelY + this.panelHeight - 1, this.panelX + this.panelWidth,
                this.panelY + this.panelHeight, PANEL_BORDER);
        guiGraphics.fill(this.panelX, this.panelY, this.panelX + 1, this.panelY + this.panelHeight, PANEL_BORDER);
        guiGraphics.fill(this.panelX + this.panelWidth - 1, this.panelY, this.panelX + this.panelWidth,
                this.panelY + this.panelHeight, PANEL_BORDER);
        guiGraphics.fill(this.panelX + 8, this.panelY + 34, this.panelX + this.panelWidth - 8, this.panelY + 35,
                PANEL_BORDER);
        guiGraphics.fill(this.panelX + 8, this.panelY + 48, this.panelX + this.panelWidth - 8, this.panelY + 49,
                PANEL_BORDER);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 标题栏
        guiGraphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2,
                this.panelY + 10, TERMINAL_GREEN);
        Component closeHint = Component.translatable("screen.noellesroles.terminal.close_hint")
                .withStyle(ChatFormatting.DARK_GRAY);
        guiGraphics.drawString(this.font, closeHint,
                this.panelX + this.panelWidth - 10 - this.font.width(closeHint), this.panelY + 10, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.noellesroles.terminal.welcome").withStyle(ChatFormatting.GRAY),
                this.panelX + 12, this.panelY + 22, 0xFFFFFF, false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.noellesroles.terminal.help_hint").withStyle(ChatFormatting.DARK_GRAY),
                this.panelX + 12, this.panelY + 38, 0xFFFFFF, false);

        // 日志：从底部往上排，滚轮可以往前翻
        int maxOffset = maxScrollOffset();
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);
        int visible = visibleLines();
        int end = this.log.size() - this.scrollOffset;
        int start = Math.max(0, end - visible);
        int top = logTop();
        for (int i = start; i < end; i++) {
            guiGraphics.drawString(this.font, this.log.get(i), this.panelX + 12, top + (i - start) * LINE_HEIGHT,
                    TERMINAL_GREEN, false);
        }

        if (maxOffset > 0) {
            // 有内容没显示出来：右侧画滚动条 + 提示
            int trackTop = top - 2;
            int trackBottom = top + (visible - 1) * LINE_HEIGHT + 2;
            int barX = this.panelX + this.panelWidth - 8;
            guiGraphics.fill(barX, trackTop, barX + 2, trackBottom, 0x40FFFFFF);
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(8, trackHeight * visible / Math.max(1, this.log.size()));
            int thumbY = trackTop + (trackHeight - thumbHeight) * (maxOffset - this.scrollOffset) / maxOffset;
            guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, TERMINAL_GREEN);

            // 显示到第几行 / 共几行
            Component scrollHint = Component.translatable("screen.noellesroles.terminal.scroll_hint", end,
                    this.log.size()).withStyle(ChatFormatting.DARK_GRAY);
            guiGraphics.drawString(this.font, scrollHint,
                    this.panelX + this.panelWidth - 14 - this.font.width(scrollHint), this.panelY + 38, 0xFFFFFF,
                    false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxOffset = maxScrollOffset();
        if (maxOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int delta = (int) Math.signum(verticalAmount) * SCROLL_STEP;
        this.scrollOffset = Math.max(0, Math.min(maxOffset, this.scrollOffset + delta));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车执行（先于输入框处理，保证按下回车就是「执行」）
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.submitCommand();
            return true;
        }
        // PageUp/PageDown 快速翻页
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int maxOffset = maxScrollOffset();
            if (maxOffset > 0) {
                int step = visibleLines() * (keyCode == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1);
                this.scrollOffset = Math.max(0, Math.min(maxOffset, this.scrollOffset + step));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 往日志里追加内容（自动裁剪过老的记录，并回到最新一行） */
    private void appendLog(List<Component> lines) {
        this.log.addAll(lines);
        int overflow = this.log.size() - MAX_LOG_LINES;
        if (overflow > 0) {
            this.log.subList(0, overflow).clear();
        }
        this.scrollOffset = 0;
    }

    /** 提交输入框里的指令 */
    private void submitCommand() {
        if (this.input == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        String raw = this.input.getValue().trim();
        if (raw.isEmpty()) {
            return;
        }
        appendLog(List.of(Component.literal("> " + raw).withStyle(ChatFormatting.WHITE)));

        ProgrammerRole.TerminalCommand command = ProgrammerRole.parseTerminalCommand(raw);
        if (command.type() == ProgrammerRole.TerminalCommandType.HELP) {
            // 本地展开指令清单：不发包、不消耗终端。
            // /help 只对程序员开放，其他人只能自己背指令。
            if (ProgrammerRole.isProgrammer(this.minecraft.player)) {
                appendLog(ProgrammerRole.getTerminalHelpLines());
            } else {
                appendLog(List.of(Component.translatable("screen.noellesroles.terminal.help_denied")
                        .withStyle(ChatFormatting.RED)));
            }
            this.input.setValue("");
            return;
        }
        // 其余一律发给服务端判定：输错的话服务端会销毁终端并在聊天栏说明原因
        ClientPlayNetworking.send(new TerminalCommandC2SPacket(raw));
        this.onClose();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String text = this.input == null ? "" : this.input.getValue();
        super.resize(minecraft, width, height);
        if (this.input != null) {
            this.input.setValue(text);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}
