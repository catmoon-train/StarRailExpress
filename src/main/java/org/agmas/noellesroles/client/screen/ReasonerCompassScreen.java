package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.widget.ConspiratorRoleWidget;
import org.agmas.noellesroles.packet.ReasonerOpenScreenS2CPacket;
import org.agmas.noellesroles.packet.ReasonerSubmitC2SPacket;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

public class ReasonerCompassScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 284;
    private static final int ROW_HEIGHT = 34;
    private static final int OPTION_PAGE_SIZE = 6;
    private static final int ROLE_COLS = 4;
    private static final int ROLE_ROWS = 3;
    private static final int ROLES_PER_PAGE = ROLE_COLS * ROLE_ROWS;
    private static final int ROLE_WIDGET_W = 88;
    private static final int ROLE_WIDGET_H = 24;
    private static final int ROLE_SPACING_X = 6;
    private static final int ROLE_SPACING_Y = 4;

    /** RAED_BOOK 是服务端枚举的 typo，对应翻译键 task.read_book。 */
    private static final Map<String, String> TASK_NAME_FIX = Map.of("RAED_BOOK", "READ_BOOK");

    private final ReasonerOpenScreenS2CPacket payload;
    private EditBox aliveCountInput;
    private EditBox killerCountInput;
    private String selectedRole = "";
    private String selectedDeathReason = "";
    private String selectedTask = "";
    private int selectionQuestion = 0; // 0=主页, 2=角色, 3=死因, 4=任务
    private int selectionPage = 0;
    private List<SRERole> allRoles = List.of();

    public ReasonerCompassScreen(ReasonerOpenScreenS2CPacket payload) {
        super(Component.translatable("screen.noellesroles.reasoner.title"));
        this.payload = payload;
        this.allRoles = resolveRoles(payload.roleIds());
    }

    private static List<SRERole> resolveRoles(List<String> roleIds) {
        List<SRERole> result = new ArrayList<>();
        for (String id : roleIds) {
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc != null) {
                SRERole role = RoleUtils.getRole(loc);
                if (role != null) result.add(role);
            }
        }
        return result;
    }

    @Override
    protected void init() {
        rebuildMain();
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    // ───── 主页 ─────

    private void rebuildMain() {
        clearWidgets();
        selectionQuestion = 0;
        int left = panelX();
        int top = panelY();
        int inputX = left + PANEL_WIDTH - 118;
        int btnWideX = inputX - 90;     // 选择按钮左侧
        int submitX = inputX + 40;       // 提交按钮
        int y = top + 50;

        // Q1: 存活人数
        if (!payload.solvedAliveCount()) {
            aliveCountInput = numberBox(inputX, y + 4, 34);
            addRenderableWidget(aliveCountInput);
            addRenderableWidget(submitBtn(submitX, y + 2, () -> submitNumber(1, aliveCountInput.getValue())));
            y += ROW_HEIGHT;
        }

        // Q2: 角色身份
        if (!payload.solvedRole()) {
            addRenderableWidget(Button.builder(
                    selectionLabel(selectedRole, "screen.noellesroles.reasoner.choose_role"),
                    button -> openSelection(2)).bounds(btnWideX, y + 2, 148, 20).build());
            addRenderableWidget(submitBtn(submitX, y + 2, () -> submitChoice(2, selectedRole)));
            y += ROW_HEIGHT;
        }

        // Q3: 死因
        if (!payload.solvedDeathReason()) {
            if (payload.deathReasonQuestionAvailable()) {
                addRenderableWidget(Button.builder(
                        selectionLabel(selectedDeathReason, "screen.noellesroles.reasoner.choose_reason"),
                        button -> openSelection(3)).bounds(btnWideX, y + 2, 148, 20).build());
                addRenderableWidget(submitBtn(submitX, y + 2, () -> submitChoice(3, selectedDeathReason)));
            }
            y += ROW_HEIGHT;
        }

        // Q4: 任务
        if (!payload.solvedTask()) {
            addRenderableWidget(Button.builder(
                    selectionLabel(selectedTask, "screen.noellesroles.reasoner.choose_task"),
                    button -> openSelection(4)).bounds(btnWideX, y + 2, 148, 20).build());
            addRenderableWidget(submitBtn(submitX, y + 2, () -> submitChoice(4, selectedTask)));
            y += ROW_HEIGHT;
        }

        // Q5: 杀手数量
        if (!payload.solvedKillerCount()) {
            if (payload.killerQuestionAvailable()) {
                killerCountInput = numberBox(inputX, y + 4, 34);
                addRenderableWidget(killerCountInput);
                addRenderableWidget(submitBtn(submitX, y + 2, () -> submitNumber(5, killerCountInput.getValue())));
            }
            y += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.reasoner.exit"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 80, top + PANEL_HEIGHT - 28, 62, 20).build());
    }

    private EditBox numberBox(int x, int y, int width) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal("0"));
        box.setFilter(value -> value.matches("\\d*"));
        box.setMaxLength(3);
        return box;
    }

    private Button submitBtn(int x, int y, Runnable action) {
        return Button.builder(Component.translatable("screen.noellesroles.reasoner.submit"),
                button -> action.run()).bounds(x, y, 62, 20).build();
    }

    private Component selectionLabel(String selected, String fallbackKey) {
        if (selected == null || selected.isBlank()) {
            return Component.translatable(fallbackKey);
        }
        return displayFor(valueQuestion(selected), selected);
    }

    private int valueQuestion(String value) {
        if (payload.deathReasonIds().contains(value)) return 3;
        if (payload.taskIds().contains(value)) return 4;
        return 2;
    }

    private void openSelection(int question) {
        selectionQuestion = question;
        selectionPage = 0;
        if (question == 2) {
            drawRolePage();
        } else {
            rebuildSelection();
        }
    }

    // ───── 角色选择（ConspiratorRoleWidget 网格，与 GuessRoleScreen 一致） ─────

    private void drawRolePage() {
        clearWidgets();
        int left = panelX();
        int top = panelY();
        List<SRERole> roles = allRoles;
        if (roles.isEmpty()) {
            addRenderableWidget(backBtn(left + PANEL_WIDTH - 108, top + PANEL_HEIGHT - 30));
            return;
        }

        int totalPages = (roles.size() + ROLES_PER_PAGE - 1) / ROLES_PER_PAGE;
        int totalGridW = ROLE_COLS * (ROLE_WIDGET_W + ROLE_SPACING_X) - ROLE_SPACING_X;
        int startX = left + (PANEL_WIDTH - totalGridW) / 2;
        int startY = top + 44;

        int startIdx = selectionPage * ROLES_PER_PAGE;
        for (int i = 0; i < ROLES_PER_PAGE; i++) {
            int idx = startIdx + i;
            if (idx >= roles.size()) break;
            SRERole role = roles.get(idx);
            int col = i % ROLE_COLS;
            int row = i / ROLE_COLS;
            int wx = startX + col * (ROLE_WIDGET_W + ROLE_SPACING_X);
            int wy = startY + row * (ROLE_WIDGET_H + ROLE_SPACING_Y);

            addRenderableWidget(new ConspiratorRoleWidget(null, wx, wy, ROLE_WIDGET_W, ROLE_WIDGET_H, role, idx) {
                @Override
                public void onPress() {
                    if (role != null) selectedRole = role.identifier().toString();
                    rebuildMain();
                }
            });
        }

        int pageY = top + PANEL_HEIGHT - 30;
        drawPageNav(left, pageY, totalPages, this::drawRolePage);
        addRenderableWidget(backBtn(left + PANEL_WIDTH - 108, pageY));
    }

    // ───── 死因 / 任务选择 ─────

    private void rebuildSelection() {
        clearWidgets();
        List<String> options = optionsFor(selectionQuestion);
        int left = panelX();
        int top = panelY();
        int start = selectionPage * OPTION_PAGE_SIZE;
        int end = Math.min(start + OPTION_PAGE_SIZE, options.size());

        for (int i = start; i < end; i++) {
            String option = options.get(i);
            int wy = top + 48 + (i - start) * 24;
            addRenderableWidget(Button.builder(displayFor(selectionQuestion, option), button -> {
                setSelected(selectionQuestion, option);
                rebuildMain();
            }).bounds(left + 36, wy, PANEL_WIDTH - 72, 20).build());
        }

        int pageY = top + PANEL_HEIGHT - 30;
        int totalPages = Math.max(1, (options.size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE);
        drawPageNav(left, pageY, totalPages, this::rebuildSelection);
        addRenderableWidget(backBtn(left + PANEL_WIDTH - 108, pageY));
    }

    private void drawPageNav(int left, int py, int totalPages, Runnable refresh) {
        if (selectionPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                selectionPage--;
                refresh.run();
            }).bounds(left + 14, py, 32, 20).build());
        }
        if (selectionPage < totalPages - 1) {
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                selectionPage++;
                refresh.run();
            }).bounds(left + 52, py, 32, 20).build());
        }
    }

    private Button backBtn(int x, int y) {
        return Button.builder(Component.translatable("gui.back"), button -> rebuildMain())
                .bounds(x, y, 54, 20).build();
    }

    private List<String> optionsFor(int question) {
        return switch (question) {
            case 3 -> payload.deathReasonIds();
            case 4 -> payload.taskIds();
            default -> payload.roleIds();
        };
    }

    private void setSelected(int question, String value) {
        switch (question) {
            case 2 -> selectedRole = value;
            case 3 -> selectedDeathReason = value;
            case 4 -> selectedTask = value;
        }
    }

    private Component displayFor(int question, String value) {
        if (question == 2) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            return id == null ? Component.literal(value)
                    : Component.translatable("announcement.star.role." + id.getPath());
        }
        if (question == 3) {
            return Component.translatable("death_reason." + value.replace(':', '.'));
        }
        if (question == 4) {
            String fixed = TASK_NAME_FIX.getOrDefault(value, value);
            return Component.translatable("task." + fixed.toLowerCase(Locale.ROOT));
        }
        return Component.literal(value);
    }

    private void submitNumber(int question, String answer) {
        if (!answer.isBlank()) {
            ClientPlayNetworking.send(new ReasonerSubmitC2SPacket(question, answer));
            onClose();
        }
    }

    private void submitChoice(int question, String answer) {
        if (answer != null && !answer.isBlank()) {
            ClientPlayNetworking.send(new ReasonerSubmitC2SPacket(question, answer));
            onClose();
        }
    }

    // ───── 渲染 ─────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double verticalAmount) {
        if (selectionQuestion != 0) {
            if (selectionQuestion == 2) {
                int totalPages = (allRoles.size() + ROLES_PER_PAGE - 1) / ROLES_PER_PAGE;
                if (verticalAmount < 0 && selectionPage < totalPages - 1) { selectionPage++; drawRolePage(); return true; }
                if (verticalAmount > 0 && selectionPage > 0) { selectionPage--; drawRolePage(); return true; }
            } else {
                int totalPages = Math.max(1, (optionsFor(selectionQuestion).size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE);
                if (verticalAmount < 0 && selectionPage < totalPages - 1) { selectionPage++; rebuildSelection(); return true; }
                if (verticalAmount > 0 && selectionPage > 0) { selectionPage--; rebuildSelection(); return true; }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, h, verticalAmount);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 底层：罗盘面板图案
        drawPanel(g);

        // 文字标签
        if (selectionQuestion == 0) {
            drawQuestionLabels(g);
        } else if (selectionQuestion == 2) {
            drawRoleSelectionTitle(g);
        } else {
            drawSelectionTitle(g);
        }

        // 最上层：按钮和输入框
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ───── 面板绘制 ─────

    private void drawPanel(GuiGraphics g) {
        int left = panelX();
        int top = panelY();
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        int cx = left + PANEL_WIDTH / 2;
        int cy = top + PANEL_HEIGHT / 2;

        // 底色
        g.fill(left, top, right, bottom, 0xEE140F1D);
        g.fill(left + 4, top + 4, right - 4, bottom - 4, 0xAA211626);

        // 十字准星
        g.hLine(cx - 92, cx + 92, cy, 0x77E8C872);
        g.vLine(cx, cy - 92, cy + 92, 0x77E8C872);
        for (int r = 70; r <= 88; r += 6) {
            g.hLine(cx - r, cx + r, cy - r / 2, 0x44E8C872);
            g.hLine(cx - r, cx + r, cy + r / 2, 0x44E8C872);
            g.vLine(cx - r, cy - r / 2, cy + r / 2, 0x44E8C872);
            g.vLine(cx + r, cy - r / 2, cy + r / 2, 0x44E8C872);
        }

        // 标题
        g.drawCenteredString(font, title, cx, top + 16, 0xFFECCB79);
        g.drawCenteredString(font, Component.translatable("screen.noellesroles.reasoner.subtitle"),
                cx, top + 31, 0xFFBBA86D);
    }

    // ───── 问题标签 ─────

    private void drawQuestionLabels(GuiGraphics g) {
        int left = panelX();
        int top = panelY();
        int textX = left + 30;
        int y = top + 54;

        // Q1
        if (!payload.solvedAliveCount()) {
            g.drawString(font, Component.translatable("screen.noellesroles.reasoner.q1"), textX, y, 0xFFEBDFAE, false);
            y += ROW_HEIGHT;
        }

        // Q2
        if (!payload.solvedRole()) {
            g.drawString(font, Component.translatable("screen.noellesroles.reasoner.q2", payload.roleTargetName()),
                    textX, y, 0xFFEBDFAE, false);
            y += ROW_HEIGHT;
        }

        // Q3
        if (!payload.solvedDeathReason()) {
            Component q3Text = payload.deathReasonQuestionAvailable()
                    ? Component.translatable("screen.noellesroles.reasoner.q3", payload.bodyTargetName())
                    : Component.translatable("screen.noellesroles.reasoner.hidden").copy()
                            .withStyle(net.minecraft.ChatFormatting.GRAY);
            g.drawString(font, q3Text, textX, y, 0xFFEBDFAE, false);
            y += ROW_HEIGHT;
        }

        // Q4
        if (!payload.solvedTask()) {
            g.drawString(font, Component.translatable("screen.noellesroles.reasoner.q4", payload.taskTargetName()),
                    textX, y, 0xFFEBDFAE, false);
            y += ROW_HEIGHT;
        }

        // Q5
        if (!payload.solvedKillerCount()) {
            Component q5Text = payload.killerQuestionAvailable()
                    ? Component.translatable("screen.noellesroles.reasoner.q5")
                    : Component.translatable("screen.noellesroles.reasoner.hidden").copy()
                            .withStyle(net.minecraft.ChatFormatting.GRAY);
            g.drawString(font, q5Text, textX, y, 0xFFEBDFAE, false);
        }
    }

    private void drawSelectionTitle(GuiGraphics g) {
        int left = panelX();
        int top = panelY();
        Component label = switch (selectionQuestion) {
            case 3 -> Component.translatable("screen.noellesroles.reasoner.select_reason");
            case 4 -> Component.translatable("screen.noellesroles.reasoner.select_task");
            default -> Component.translatable("screen.noellesroles.reasoner.select_role");
        };
        g.drawCenteredString(font, label, left + PANEL_WIDTH / 2, top + 34, 0xFFEBDFAE);
        int totalPages = Math.max(1, (optionsFor(selectionQuestion).size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE);
        g.drawString(font, Component.literal((selectionPage + 1) + "/" + totalPages),
                left + 140, top + PANEL_HEIGHT - 24, 0xFFBBA86D, false);
    }

    private void drawRoleSelectionTitle(GuiGraphics g) {
        int left = panelX();
        int top = panelY();
        g.drawCenteredString(font, Component.translatable("screen.noellesroles.reasoner.select_role"),
                left + PANEL_WIDTH / 2, top + 34, 0xFFEBDFAE);
        int totalPages = Math.max(1, (allRoles.size() + ROLES_PER_PAGE - 1) / ROLES_PER_PAGE);
        g.drawString(font, Component.literal((selectionPage + 1) + "/" + totalPages),
                left + 140, top + PANEL_HEIGHT - 24, 0xFFBBA86D, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制全屏背景，交由 drawPanel 处理
    }
}
