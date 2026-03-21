package net.exmo.sre.client.chat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 现代化聊天对话界面 —— 星穹铁道风格。
 * <p>
 * 特性：
 * <ul>
 *   <li>底部居中的对话面板，带半透明毛玻璃背景</li>
 *   <li>说话者名称 + 逐字打字机动画</li>
 *   <li>面板滑入/滑出动画</li>
 *   <li>点击或回车推进对话，对话结束自动关闭</li>
 *   <li>Camera 平滑聚焦到指定实体</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ChatDialogueScreen extends Screen {

    // ── 常量 ──────────────────────────────────────────────────────────
    /** 面板高度（屏幕底部） */
    private static final int PANEL_H = 100;
    /** 每个选项增加的高度 */
    private static final int CHOICE_AREA_H = 22;
    /** 面板与屏幕两侧的边距百分比 */
    private static final float PANEL_MARGIN_RATIO = 0.08F;
    /** 面板圆角边框粗细 */
    private static final int BORDER = 1;
    /** 面板内文字左内边距 */
    private static final int TEXT_PAD_X = 20;
    /** 说话者名称行距 */
    private static final int SPEAKER_H = 16;
    /** 对话文字行距 */
    private static final int LINE_H = 14;
    /** 面板底部内边距 */
    private static final int TEXT_PAD_BOTTOM = 14;
    /** 打字速度：每个字符间隔毫秒 */
    private static final long CHAR_INTERVAL_MS = 32;
    /** 面板滑入动画持续毫秒 */
    private static final long SLIDE_DURATION_MS = 350;
    /** 面板滑出动画持续毫秒 */
    private static final long SLIDE_OUT_DURATION_MS = 280;

    // ── 数据 ──────────────────────────────────────────────────────────
    private final ChatDialogueData dialogue;
    private final int focusEntityId;
    private final List<ChatDialogueData.DialogueLine> lines;

    // ── 状态 ──────────────────────────────────────────────────────────
    private int currentLine = 0;
    private long lineStartTime;
    private long screenOpenTime;
    private boolean closingAnim = false;
    private long closeStartTime;
    private int selectedChoiceIndex = 0;
    private final List<ChoiceBounds> renderedChoiceBounds = new ArrayList<>();

    // ── Camera 聚焦 ──────────────────────────────────────────────────
    private float origYaw, origPitch;
    private boolean cameraRestored = false;
    /** Camera 渐进插值进度 0→1 */
    private float cameraProgress = 0.0F;

    // ── "继续" 提示闪烁 ─────────────────────────────────────────────
    private static final String CONTINUE_HINT = "▶  点击或按 Enter 继续";
    private static final String END_HINT = "▶  对话结束";
    private static final String CHOICE_HINT = "↑ ↓ 选择  Enter 确认";

    private record ChoiceBounds(int index, int x1, int y1, int x2, int y2) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    public ChatDialogueScreen(ChatDialogueData dialogue, int focusEntityId) {
        super(Component.literal(dialogue.title));
        this.dialogue = dialogue;
        this.focusEntityId = focusEntityId;
        this.lines = dialogue.lines;
    }

    // ─────────────────────────────────────────────────────────────────
    // 生命周期
    // ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        this.screenOpenTime = Util.getMillis();
        this.lineStartTime = this.screenOpenTime + SLIDE_DURATION_MS; // 等滑入完毕再开始打字
        if (this.minecraft != null && this.minecraft.player != null) {
            this.origYaw = this.minecraft.player.getYRot();
            this.origPitch = this.minecraft.player.getXRot();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ─────────────────────────────────────────────────────────────────
    // Tick — Camera 聚焦
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (this.minecraft == null || this.minecraft.player == null) return;

        // 关闭动画期间：还原 Camera
        if (closingAnim) {
            cameraProgress = Math.max(cameraProgress - 0.06F, 0.0F);
            applyCameraFocus();
            if (Util.getMillis() - closeStartTime > SLIDE_OUT_DURATION_MS) {
                restoreCamera();
                this.minecraft.setScreen(null);
            }
            return;
        }

        // 聚焦 Camera 到目标实体
        if (focusEntityId >= 0) {
            cameraProgress = Math.min(cameraProgress + 0.04F, 1.0F);
            applyCameraFocus();
        }
    }

    private void applyCameraFocus() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (focusEntityId < 0) return;

        Entity target = this.minecraft.level != null
                ? this.minecraft.level.getEntity(focusEntityId)
                : null;
        if (target == null) return;

        double dx = target.getX() - this.minecraft.player.getX();
        double dy = (target.getEyeY()) - this.minecraft.player.getEyeY();
        double dz = target.getZ() - this.minecraft.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float targetPitch = (float) -(Mth.atan2(dy, dist) * (180.0 / Math.PI));

        float eased = easeInOutCubic(cameraProgress);
        float yaw = lerpAngle(eased, origYaw, targetYaw);
        float pitch = Mth.lerp(eased, origPitch, targetPitch);

        this.minecraft.player.setYRot(yaw);
        this.minecraft.player.setXRot(pitch);
        this.minecraft.player.yRotO = yaw;
        this.minecraft.player.xRotO = pitch;
    }

    private void restoreCamera() {
        if (cameraRestored || this.minecraft == null || this.minecraft.player == null) return;
        cameraRestored = true;
    }

    // ─────────────────────────────────────────────────────────────────
    // 渲染
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 不绘制默认背景
        long now = Util.getMillis();
        renderedChoiceBounds.clear();
        ChatDialogueData.DialogueLine line = getCurrentLineData();

        // ── 面板滑入/滑出动画 ─────────────────────────────────────────
        float slideProgress;
        if (closingAnim) {
            float t = Math.min((now - closeStartTime) / (float) SLIDE_OUT_DURATION_MS, 1.0F);
            slideProgress = 1.0F - easeInCubic(t); // 从1到0
        } else {
            float t = Math.min((now - screenOpenTime) / (float) SLIDE_DURATION_MS, 1.0F);
            slideProgress = easeOutCubic(t); // 从0到1
        }

        // 面板尺寸
        int margin = (int) (this.width * PANEL_MARGIN_RATIO);
        int panelW = this.width - margin * 2;
        int panelX = margin;
        int textMaxW = panelW - TEXT_PAD_X * 2;
        String displayText = "";
        List<FormattedCharSequence> wrapped = List.of();
        boolean finished = false;
        int choiceCount = 0;

        if (line != null) {
            displayText = getDisplayText(line, now);
            wrapped = this.font.split(Component.literal(displayText), textMaxW);
            finished = isLineFinished(line, now);
            if (finished && line.hasChoices()) {
                choiceCount = line.choices.size();
            }
        }

        int panelH = PANEL_H + choiceCount * CHOICE_AREA_H + (choiceCount > 0 ? 8 : 0);
        // 从底部滑入：slideProgress=0 时面板在屏幕外
        int panelY = this.height - (int) (panelH * slideProgress) - 10;

        // ── 全屏上方遮罩（电影感） ───────────────────────────────────
        int overlayAlpha = (int) (50 * slideProgress);
        g.fill(0, 0, this.width, this.height, (overlayAlpha << 24));

        // 在面板可见区域绘制一条横向分隔线（面板顶部装饰）
        int accentAlpha = (int) (180 * slideProgress);
        g.fill(panelX + 12, panelY - 2, panelX + panelW - 12, panelY - 1,
                (accentAlpha << 24) | 0x3AA6FF);

        // ── 面板背景 ─────────────────────────────────────────────────
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 半透明深色背景
        int bgAlpha = (int) (210 * slideProgress);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH,
                (bgAlpha << 24) | 0x0C1018);
        // 边框
        int borderAlpha = (int) (140 * slideProgress);
        int borderColor = (borderAlpha << 24) | 0x1E2A3A;
        g.fill(panelX, panelY, panelX + panelW, panelY + BORDER, borderColor);
        g.fill(panelX, panelY + panelH - BORDER, panelX + panelW, panelY + panelH, borderColor);
        g.fill(panelX, panelY, panelX + BORDER, panelY + panelH, borderColor);
        g.fill(panelX + panelW - BORDER, panelY, panelX + panelW, panelY + panelH, borderColor);

        RenderSystem.disableBlend();

        if (slideProgress < 0.1F) return; // 太小的时候不绘制文字

        // ── 对话标题（面板左上角，小号、低饱和度） ────────────────────
        int titleAlpha = (int) (120 * slideProgress);
        g.drawString(this.font, dialogue.title,
                panelX + TEXT_PAD_X, panelY + 6,
                (titleAlpha << 24) | 0x6688AA, false);

        // ── 当前对话行 ───────────────────────────────────────────────
        if (line != null) {

            // 说话者名称：带左侧装饰竖线
            int namePosY = panelY + 22;
            int nameColor = line.parseColor();
            int nameAlpha = (int) (220 * slideProgress);

            // 装饰竖线
            g.fill(panelX + TEXT_PAD_X - 6, namePosY, panelX + TEXT_PAD_X - 4, namePosY + 12,
                    (nameAlpha << 24) | nameColor);

            g.drawString(this.font, line.speaker,
                    panelX + TEXT_PAD_X, namePosY,
                    (nameAlpha << 24) | nameColor, false);

            // 绘制文字（支持换行）
            int textPosY = namePosY + SPEAKER_H + 2;
            int textAlpha = (int) (240 * slideProgress);
            int textColor = (textAlpha << 24) | 0xE0E6EE;
            int lineY = textPosY;
            for (var wline : wrapped) {
                g.drawString(this.font, wline, panelX + TEXT_PAD_X, lineY, textColor, false);
                lineY += LINE_H;
            }

            // 打字光标闪烁（未完成时）
            int charCount = getCurrentCharCount(line, now);
            if (!finished && charCount > 0 && !wrapped.isEmpty()) {
                boolean cursorVisible = (now / 400) % 2 == 0;
                if (cursorVisible) {
                    int lastLineW = this.font.width(wrapped.get(wrapped.size() - 1));
                    int cursorX = panelX + TEXT_PAD_X + lastLineW + 2;
                    int cursorY = textPosY + (wrapped.size() - 1) * LINE_H;
                    g.fill(cursorX, cursorY, cursorX + 2, cursorY + 10,
                            (textAlpha << 24) | 0x7FDBFF);
                }
            }

            if (finished && line.hasChoices()) {
                int choiceBaseY = lineY + 4;
                int hintAlpha = (int) (140 * slideProgress);
                int hintW = this.font.width(CHOICE_HINT);
                g.drawString(this.font, CHOICE_HINT,
                        panelX + panelW - TEXT_PAD_X - hintW,
                        panelY + panelH - TEXT_PAD_BOTTOM,
                        (hintAlpha << 24) | 0x88AACC, false);

                for (int i = 0; i < line.choices.size(); i++) {
                    ChatDialogueData.DialogueChoice choice = line.choices.get(i);
                    int optionY = choiceBaseY + i * CHOICE_AREA_H;
                    ChoiceBounds bounds = new ChoiceBounds(
                            i,
                            panelX + TEXT_PAD_X - 6,
                            optionY - 2,
                            panelX + panelW - TEXT_PAD_X + 6,
                            optionY + 14);
                    renderedChoiceBounds.add(bounds);

                    boolean hovered = bounds.contains(mouseX, mouseY);
                    boolean selected = i == selectedChoiceIndex;
                    int bgColor = selected
                            ? ((int) (125 * slideProgress) << 24) | 0x193552
                            : hovered
                            ? ((int) (85 * slideProgress) << 24) | 0x14263B
                            : ((int) (45 * slideProgress) << 24) | 0x11161F;
                    int choiceBorderColor = selected
                            ? ((int) (180 * slideProgress) << 24) | 0x5AB7FF
                            : ((int) (90 * slideProgress) << 24) | 0x30465C;
                    int choiceColor = ((int) (230 * slideProgress) << 24) | (selected ? 0xF5FAFF : 0xC8D4E3);

                    g.fill(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(), bgColor);
                    g.fill(bounds.x1(), bounds.y1(), bounds.x2(), bounds.y1() + 1, choiceBorderColor);
                    g.fill(bounds.x1(), bounds.y2() - 1, bounds.x2(), bounds.y2(), choiceBorderColor);
                    g.fill(bounds.x1(), bounds.y1(), bounds.x1() + 1, bounds.y2(), choiceBorderColor);
                    g.fill(bounds.x2() - 1, bounds.y1(), bounds.x2(), bounds.y2(), choiceBorderColor);
                    g.drawString(this.font, (i + 1) + ". " + choice.text,
                            panelX + TEXT_PAD_X, optionY,
                            choiceColor, false);
                }
            }

            // 已完成时显示"继续"提示
            if (finished && !closingAnim && !line.hasChoices()) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0);
                int hintAlpha = (int) (pulse * 160 * slideProgress);
                boolean isLast = currentLine >= lines.size() - 1;
                String hint = isLast ? END_HINT : CONTINUE_HINT;
                int hintW = this.font.width(hint);
                g.drawString(this.font, hint,
                        panelX + panelW - TEXT_PAD_X - hintW,
                        panelY + panelH - TEXT_PAD_BOTTOM,
                        (hintAlpha << 24) | 0x88AACC, false);
            }

            // 对话进度指示器（右上角）
            String progress = (currentLine + 1) + " / " + lines.size();
            int progW = this.font.width(progress);
            int progAlpha = (int) (100 * slideProgress);
            g.drawString(this.font, progress,
                    panelX + panelW - TEXT_PAD_X - progW, panelY + 6,
                    (progAlpha << 24) | 0x556677, false);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 输入事件
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closingAnim) return true;

        if (isSelectingChoice()) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
                moveChoiceSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
                moveChoiceSelection(1);
                return true;
            }
            int directChoice = mapNumberKeyToChoice(keyCode);
            if (directChoice >= 0) {
                selectedChoiceIndex = directChoice;
                advanceDialogue();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            advanceDialogue();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            startClosing();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closingAnim) return true;
        if (button == 0) {
            if (isSelectingChoice()) {
                for (ChoiceBounds bounds : renderedChoiceBounds) {
                    if (bounds.contains(mouseX, mouseY)) {
                        selectedChoiceIndex = bounds.index();
                        advanceDialogue();
                        return true;
                    }
                }
            }
            advanceDialogue();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void advanceDialogue() {
        long now = Util.getMillis();
        ChatDialogueData.DialogueLine line = getCurrentLineData();
        if (line == null) {
            startClosing();
            return;
        }

        // 如果当前行打字还未完成 → 直接展示完整文字
        int charCount = getCurrentCharCount(line, now);
        if (charCount < line.text.length()) {
            lineStartTime = now - (long) line.text.length() * CHAR_INTERVAL_MS - 1;
            return;
        }

        if (line.hasChoices()) {
            confirmChoiceSelection(now, line);
            return;
        }

        if (!line.runsOnServer()) {
            runClientCommand(line.command);
        }

        // 通知服务端当前行推进（用于执行绑定的 command）
        ClientPlayNetworking.send(ChatDialogueAdvancePayload.advance(dialogue.id, currentLine));

        // 推进到下一行
        currentLine++;
        if (currentLine >= lines.size()) {
            startClosing();
        } else {
            lineStartTime = now;
            selectedChoiceIndex = 0;
        }
    }

    private void confirmChoiceSelection(long now, ChatDialogueData.DialogueLine line) {
        if (selectedChoiceIndex < 0 || selectedChoiceIndex >= line.choices.size()) {
            selectedChoiceIndex = 0;
        }

        ChatDialogueData.DialogueChoice choice = line.choices.get(selectedChoiceIndex);
        if (!choice.runsOnServer()) {
            runClientCommand(choice.command);
        }
        ClientPlayNetworking.send(ChatDialogueAdvancePayload.select(
                dialogue.id, currentLine, selectedChoiceIndex, focusEntityId));

        renderedChoiceBounds.clear();
        selectedChoiceIndex = 0;

        if (choice.opensDialogue()) {
            startClosing();
            return;
        }

        if (choice.nextLine >= 0 && choice.nextLine < lines.size()) {
            currentLine = choice.nextLine;
            lineStartTime = now;
            return;
        }

        startClosing();
    }

    private void startClosing() {
        if (!closingAnim) {
            closingAnim = true;
            closeStartTime = Util.getMillis();
        }
    }

    private void runClientCommand(String command) {
        if (command == null || command.isBlank() || this.minecraft == null || this.minecraft.player == null
                || this.minecraft.player.connection == null) {
            return;
        }

        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return;
        }

        this.minecraft.player.connection.sendCommand(normalized);
    }

    // ─────────────────────────────────────────────────────────────────
    // 动画工具方法
    // ─────────────────────────────────────────────────────────────────

    private static float easeOutCubic(float t) {
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0F;
    }

    private static float lerpAngle(float t, float a, float b) {
        float diff = Mth.wrapDegrees(b - a);
        return a + diff * t;
    }

    private ChatDialogueData.DialogueLine getCurrentLineData() {
        if (currentLine < 0 || currentLine >= lines.size()) return null;
        return lines.get(currentLine);
    }

    private int getCurrentCharCount(ChatDialogueData.DialogueLine line, long now) {
        long elapsed = now - lineStartTime;
        if (elapsed < 0) return 0;
        return (int) (elapsed / CHAR_INTERVAL_MS);
    }

    private boolean isLineFinished(ChatDialogueData.DialogueLine line, long now) {
        return getCurrentCharCount(line, now) >= line.text.length();
    }

    private String getDisplayText(ChatDialogueData.DialogueLine line, long now) {
        int charCount = getCurrentCharCount(line, now);
        if (charCount >= line.text.length()) return line.text;
        return line.text.substring(0, Math.min(charCount, line.text.length()));
    }

    private boolean isSelectingChoice() {
        ChatDialogueData.DialogueLine line = getCurrentLineData();
        return line != null && line.hasChoices() && isLineFinished(line, Util.getMillis());
    }

    private void moveChoiceSelection(int delta) {
        ChatDialogueData.DialogueLine line = getCurrentLineData();
        if (line == null || !line.hasChoices()) return;
        int size = line.choices.size();
        if (size == 0) return;
        selectedChoiceIndex = (selectedChoiceIndex + delta + size) % size;
    }

    private int mapNumberKeyToChoice(int keyCode) {
        ChatDialogueData.DialogueLine line = getCurrentLineData();
        if (line == null || !line.hasChoices()) return -1;

        int index = switch (keyCode) {
            case GLFW.GLFW_KEY_1 -> 0;
            case GLFW.GLFW_KEY_2 -> 1;
            case GLFW.GLFW_KEY_3 -> 2;
            case GLFW.GLFW_KEY_4 -> 3;
            case GLFW.GLFW_KEY_5 -> 4;
            case GLFW.GLFW_KEY_6 -> 5;
            case GLFW.GLFW_KEY_7 -> 6;
            case GLFW.GLFW_KEY_8 -> 7;
            case GLFW.GLFW_KEY_9 -> 8;
            default -> -1;
        };
        return index >= 0 && index < line.choices.size() ? index : -1;
    }
}
