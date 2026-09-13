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
 * 黑底绿字的终端风格：上半部分是可点击的指令提示，下半部分是日志，底部是输入框，
 * 回车执行。指令是否合法先用 {@link ProgrammerRole#parseTerminalCommand} 在本地判一遍：
 * 不合法就只在日志里报错（不发包，终端也不会被消耗），合法才发给服务端。
 */
public class TerminalScreen extends Screen {

    /** 终端绿 */
    private static final int TERMINAL_GREEN = 0xFF3AF07A;
    private static final int PANEL_BG = 0xF00A0F0A;
    private static final int PANEL_BORDER = 0xFF1F7A46;
    private static final int LINE_HEIGHT = 10;

    /** 界面日志（字段而不是局部变量，resize 重建界面后内容还在） */
    private final List<Component> log = new ArrayList<>();
    /** 可点击的指令提示行 */
    private final List<HelpRow> helpRows = new ArrayList<>();

    private EditBox input;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    /** 一行可点击的指令提示：点击后把命令填进输入框 */
    private record HelpRow(String command, int x, int y, int width) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + LINE_HEIGHT;
        }
    }

    public TerminalScreen() {
        super(Component.translatable("screen.noellesroles.terminal.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.panelWidth = Math.min(420, this.width - 40);
        this.panelHeight = Math.min(232, this.height - 40);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        // 可点击的指令提示
        this.helpRows.clear();
        int rowX = this.panelX + 12;
        int rowY = this.panelY + 60;
        for (String itemId : ProgrammerRole.getTerminalItemIds()) {
            addHelpRow("/give @s " + itemId, rowX, rowY);
            rowY += LINE_HEIGHT + 2;
        }
        addHelpRow("/tp @s room", rowX, rowY);

        // 输入框
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
            this.log.add(Component.translatable("screen.noellesroles.terminal.help_give"));
            this.log.add(Component.translatable("screen.noellesroles.terminal.help_tp"));
            this.log.add(Component.translatable("screen.noellesroles.terminal.warning")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private void addHelpRow(String command, int x, int y) {
        this.helpRows.add(new HelpRow(command, x, y, this.font.width(command)));
    }

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
        guiGraphics.fill(this.panelX + 8, this.panelY + 92, this.panelX + this.panelWidth - 8, this.panelY + 93,
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

        // 指令提示（可点击填入）
        guiGraphics.drawString(this.font,
                Component.translatable("screen.noellesroles.terminal.help_title").withStyle(ChatFormatting.DARK_GRAY),
                this.panelX + 12, this.panelY + 46, 0xFFFFFF, false);
        for (HelpRow row : this.helpRows) {
            boolean hovered = row.contains(mouseX, mouseY);
            guiGraphics.drawString(this.font, Component.literal(row.command()), row.x(), row.y(),
                    hovered ? 0xFFFFFFFF : TERMINAL_GREEN, false);
        }
        guiGraphics.drawString(this.font,
                Component.translatable("screen.noellesroles.terminal.warning").withStyle(ChatFormatting.YELLOW),
                this.panelX + 12, this.panelY + 104, 0xFFFFFF, false);

        // 日志：紧贴输入框上方，从下往上排
        int logBottom = this.panelY + this.panelHeight - 34;
        int maxLines = Math.max(1, (logBottom - (this.panelY + 118)) / LINE_HEIGHT + 1);
        int start = Math.max(0, this.log.size() - maxLines);
        for (int i = start; i < this.log.size(); i++) {
            guiGraphics.drawString(this.font, this.log.get(i), this.panelX + 12,
                    this.panelY + 118 + (i - start) * LINE_HEIGHT, TERMINAL_GREEN, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (HelpRow row : this.helpRows) {
                if (row.contains((int) mouseX, (int) mouseY)) {
                    if (this.input != null) {
                        this.input.setValue(row.command());
                        this.setFocused(this.input);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 回车执行（先于输入框处理，保证按下回车就是「执行」）
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.submitCommand();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        this.log.add(Component.literal("> " + raw).withStyle(ChatFormatting.WHITE));

        ProgrammerRole.TerminalCommand command = ProgrammerRole.parseTerminalCommand(raw);
        if (!command.isValid()) {
            // 本地就能看出不合法：只报错，不发包，终端不会被消耗
            this.log.add(Component.translatable(command.errorKey()).withStyle(ChatFormatting.RED));
            this.input.setValue("");
            return;
        }
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
