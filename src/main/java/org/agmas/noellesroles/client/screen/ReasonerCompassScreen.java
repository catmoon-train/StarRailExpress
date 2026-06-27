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
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 216;
    private static final int ROW_HEIGHT = 30;
    private static final int OPTION_PAGE_SIZE = 6;
    private static final int ROLE_COLS = 4;
    private static final int ROLE_ROWS = 3;
    private static final int ROLES_PER_PAGE = ROLE_COLS * ROLE_ROWS;
    private static final int ROLE_WIDGET_W = 86;
    private static final int ROLE_WIDGET_H = 24;
    private static final int ROLE_SPACING_X = 4;
    private static final int ROLE_SPACING_Y = 2;

    /** RAED_BOOK 是服务端枚举的 typo，对应翻译键 task.read_book。 */
    private static final Map<String, String> TASK_NAME_FIX = Map.of("RAED_BOOK", "READ_BOOK");

    private final ReasonerOpenScreenS2CPacket payload;
    private EditBox aliveCountInput;
    private EditBox killerCountInput;
    private String selectedRole = "";
    private String selectedDeathReason = "";
    private String selectedTask = "";
    private int selectionQuestion = 0; // 0=主界面, 2=角色, 3=死因, 4=任务
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
                if (role != null) {
                    result.add(role);
                }
            }
        }
        return result;
    }

    @Override
    protected void init() {
        rebuildMain();
    }

    /** 计算当前未解决的问题数量，用于动态缩减面板高度。 */
    private int unsolvedCount() {
        int count = 0;
        if (!payload.solvedAliveCount()) count++;
        if (!payload.solvedRole()) count++;
        if (payload.deathReasonQuestionAvailable() && !payload.solvedDeathReason()) count++;
        if (!payload.solvedTask()) count++;
        if (payload.killerQuestionAvailable() && !payload.solvedKillerCount()) count++;
        return count;
    }

    private int effectivePanelHeight() {
        return Math.max(PANEL_HEIGHT, 80 + unsolvedCount() * ROW_HEIGHT);
    }

    private void rebuildMain() {
        clearWidgets();
        selectionQuestion = 0;
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - effectivePanelHeight()) / 2;
        int inputX = left + PANEL_WIDTH - 118;
        int buttonX = left + PANEL_WIDTH - 82;
        int y = top + 46;

        // Q1: 存活人数（未解时显示）
        if (!payload.solvedAliveCount()) {
            aliveCountInput = numberBox(inputX, y + 5, 34, "1");
            addRenderableWidget(aliveCountInput);
            addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.reasoner.submit"),
                    button -> submitNumber(1, aliveCountInput.getValue()))
                    .bounds(buttonX, y + 3, 62, 20).build());
            y += ROW_HEIGHT;
        }

        // Q2: 角色身份（未解时显示）
        if (!payload.solvedRole()) {
            addRenderableWidget(Button.builder(selectionLabel(selectedRole, "screen.noellesroles.reasoner.choose_role"),
                    button -> openSelection(2)).bounds(buttonX - 86, y + 3, 148, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.reasoner.submit"),
                    button -> submitChoice(2, selectedRole))
                    .bounds(buttonX + 68, y + 3, 62, 20).build());
            y += ROW_HEIGHT;
        }

        // Q3: 死因（可用且未解时显示）
        if (payload.deathReasonQuestionAvailable() && !payload.solvedDeathReason()) {
            addRenderableWidget(Button.builder(selectionLabel(selectedDeathReason, "screen.noellesroles.reasoner.choose_reason"),
                    button -> openSelection(3)).bounds(buttonX - 86, y + 3, 148, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.reasoner.submit"),
                    button -> submitChoice(3, selectedDeathReason))
                    .bounds(buttonX + 68, y + 3, 62, 20).build());
            y += ROW_HEIGHT;
        }

        // Q4: 任务（未解时显示）
        if (!payload.solvedTask()) {
            addRenderableWidget(Button.builder(selectionLabel(selectedTask, "screen.noellesroles.reasoner.choose_task"),
                    button -> openSelection(4)).bounds(buttonX - 86, y + 3, 148, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.reasoner.submit"),
                    button -> submitChoice(4, selectedTask))
                    .bounds(buttonX + 68, y + 3, 62, 20).build());
            y += ROW_HEIGHT;
        }

        // Q5: 杀手数量（可用且未解时显示）
        if (payload.killerQuestionAvailable() && !payload.solvedKillerCount()) {
            killerCountInput = numberBox(inputX, y + 5, 34, "5");
            addRenderableWidget(killerCountInput);
            addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.reasoner.submit"),
                    button -> submitNumber(5, killerCountInput.getValue()))
                    .bounds(buttonX, y + 3, 62, 20).build());
            y += ROW_HEIGHT;
        }

        // 如果全部解决，显示完成提示，否则始终有至少一个问题
        if (unsolvedCount() == 0) {
            // 全部解决 — 不需要额外操作，游戏会自动胜利
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 84, top + effectivePanelHeight() - 28, 62, 20).build());
    }

    private EditBox numberBox(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, x, y, width, 18, Component.literal(hint));
        box.setFilter(value -> value.matches("\\d*"));
        box.setMaxLength(3);
        return box;
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
            rebuildRoleSelection();
        } else {
            rebuildSelection();
        }
    }

    // ───── 角色选择（复用 ConspiratorRoleWidget + GuessRoleScreen 网格） ─────

    private void rebuildRoleSelection() {
        clearWidgets();
        selectionPage = 0;
        drawRolePage();
    }

    private void drawRolePage() {
        clearWidgets();
        List<SRERole> roles = allRoles;
        if (roles.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> rebuildMain())
                    .bounds((width - PANEL_WIDTH) / 2 + PANEL_WIDTH - 108,
                            (height - effectivePanelHeight()) / 2 + effectivePanelHeight() - 30, 54, 20).build());
            return;
        }

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - effectivePanelHeight()) / 2;
        int totalPages = (roles.size() + ROLES_PER_PAGE - 1) / ROLES_PER_PAGE;

        int totalGridW = ROLE_COLS * (ROLE_WIDGET_W + ROLE_SPACING_X) - ROLE_SPACING_X;
        int startX = left + (PANEL_WIDTH - totalGridW) / 2;
        int startY = top + 40;

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
                    if (role != null) {
                        selectedRole = role.identifier().toString();
                    }
                    rebuildMain();
                }
            });
        }

        // 分页按钮
        int pageY = top + effectivePanelHeight() - 30;
        if (totalPages > 1) {
            if (selectionPage > 0) {
                addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                    selectionPage--;
                    drawRolePage();
                }).bounds(left + 14, pageY, 32, 20).build());
            }
            if (selectionPage < totalPages - 1) {
                addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                    selectionPage++;
                    drawRolePage();
                }).bounds(left + 52, pageY, 32, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> rebuildMain())
                .bounds(left + PANEL_WIDTH - 108, pageY, 54, 20).build());
    }

    // ───── 死因 / 任务选择 ─────

    private void rebuildSelection() {
        clearWidgets();
        List<String> options = optionsFor(selectionQuestion);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - effectivePanelHeight()) / 2;
        int start = selectionPage * OPTION_PAGE_SIZE;
        int end = Math.min(start + OPTION_PAGE_SIZE, options.size());

        for (int i = start; i < end; i++) {
            String option = options.get(i);
            int y = top + 48 + (i - start) * 24;
            addRenderableWidget(Button.builder(displayFor(selectionQuestion, option), button -> {
                setSelected(selectionQuestion, option);
                rebuildMain();
            }).bounds(left + 50, y, PANEL_WIDTH - 100, 20).build());
        }

        int pageY = top + effectivePanelHeight() - 30;
        int totalPages = Math.max(1, (options.size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE);
        if (selectionPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                selectionPage--;
                rebuildSelection();
            }).bounds(left + 14, pageY, 32, 20).build());
        }
        if (selectionPage < totalPages - 1) {
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                selectionPage++;
                rebuildSelection();
            }).bounds(left + 52, pageY, 32, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> rebuildMain())
                .bounds(left + PANEL_WIDTH - 108, pageY, 54, 20).build());
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
            default -> {}
        }
    }

    private Component displayFor(int question, String value) {
        if (question == 2) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            return id == null ? Component.literal(value) : Component.translatable("announcement.star.role." + id.getPath());
        }
        if (question == 3) {
            return Component.translatable("death_reason." + value.replace(':', '.'));
        }
        if (question == 4) {
            // 修正 typo: RAED_BOOK → READ_BOOK
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectionQuestion != 0) {
            if (selectionQuestion == 2) {
                List<SRERole> roles = allRoles;
                int totalPages = (roles.size() + ROLES_PER_PAGE - 1) / ROLES_PER_PAGE;
                if (verticalAmount < 0 && selectionPage < totalPages - 1) {
                    selectionPage++;
                    drawRolePage();
                    return true;
                }
                if (verticalAmount > 0 && selectionPage > 0) {
                    selectionPage--;
                    drawRolePage();
                    return true;
                }
            } else {
                List<String> options = optionsFor(selectionQuestion);
                int totalPages = Math.max(1, (options.size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE);
                if (verticalAmount < 0 && selectionPage < totalPages - 1) {
                    selectionPage++;
                    rebuildSelection();
                    return true;
                }
                if (verticalAmount > 0 && selectionPage > 0) {
                    selectionPage--;
                    rebuildSelection();
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - effectivePanelHeight()) / 2;
        drawPanel(guiGraphics, left, top, effectivePanelHeight());
        if (selectionQuestion == 0) {
            drawQuestions(guiGraphics, left, top);
        } else if (selectionQuestion == 2) {
            drawRoleSelectionTitle(guiGraphics, left, top);
        } else {
            drawSelectionTitle(guiGraphics, left, top);
        }
    }

    private void drawPanel(GuiGraphics guiGraphics, int left, int top, int panelHeight) {
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight, 0xEE140F1D);
        guiGraphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + panelHeight - 4, 0xAA211626);
        int cx = left + PANEL_WIDTH / 2;
        int cy = top + panelHeight / 2;
        guiGraphics.hLine(cx - 92, cx + 92, cy, 0x77E8C872);
        guiGraphics.vLine(cx, cy - 92, cy + 92, 0x77E8C872);
        for (int r = 70; r <= 88; r += 6) {
            guiGraphics.hLine(cx - r, cx + r, cy - r / 2, 0x44E8C872);
            guiGraphics.hLine(cx - r, cx + r, cy + r / 2, 0x44E8C872);
            guiGraphics.vLine(cx - r, cy - r / 2, cy + r / 2, 0x44E8C872);
            guiGraphics.vLine(cx + r, cy - r / 2, cy + r / 2, 0x44E8C872);
        }
        guiGraphics.drawCenteredString(font, title, cx, top + 16, 0xFFECCB79);
        guiGraphics.drawCenteredString(font, Component.translatable(
                "screen.noellesroles.reasoner.subtitle"), cx, top + 29, 0xFFBBA86D);
    }

    private void drawQuestions(GuiGraphics guiGraphics, int left, int top) {
        int y = top + 52;
        // 仅绘制未解的问题
        if (!payload.solvedAliveCount()) {
            drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q1"));
            y += ROW_HEIGHT;
        }
        if (!payload.solvedRole()) {
            drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q2", payload.roleTargetName()));
            y += ROW_HEIGHT;
        }
        if (payload.deathReasonQuestionAvailable() && !payload.solvedDeathReason()) {
            drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q3", payload.bodyTargetName()));
            y += ROW_HEIGHT;
        }
        if (!payload.solvedTask()) {
            drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q4", payload.taskTargetName()));
            y += ROW_HEIGHT;
        }
        if (payload.killerQuestionAvailable() && !payload.solvedKillerCount()) {
            drawQuestion(guiGraphics, y, Component.translatable("screen.noellesroles.reasoner.q5"));
        }
    }

    private void drawQuestion(GuiGraphics guiGraphics, int y, Component text) {
        guiGraphics.drawString(font, text, (width - PANEL_WIDTH) / 2 + 28, y, 0xFFEBDFAE, false);
    }

    private void drawSelectionTitle(GuiGraphics guiGraphics, int left, int top) {
        Component label = switch (selectionQuestion) {
            case 3 -> Component.translatable("screen.noellesroles.reasoner.select_reason");
            case 4 -> Component.translatable("screen.noellesroles.reasoner.select_task");
            default -> Component.translatable("screen.noellesroles.reasoner.select_role");
        };
        guiGraphics.drawCenteredString(font, label, left + PANEL_WIDTH / 2, top + 36, 0xFFEBDFAE);
        int totalPages = Math.max(1, (optionsFor(selectionQuestion).size() + OPTION_PAGE_SIZE - 1) / OPTION_PAGE_SIZE);
        guiGraphics.drawString(font, Component.literal((selectionPage + 1) + "/" + totalPages),
                left + 132, top + effectivePanelHeight() - 24, 0xFFBBA86D, false);
    }

    private void drawRoleSelectionTitle(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.reasoner.select_role"),
                left + PANEL_WIDTH / 2, top + 36, 0xFFEBDFAE);
        List<SRERole> roles = allRoles;
        int totalPages = Math.max(1, (roles.size() + ROLES_PER_PAGE - 1) / ROLES_PER_PAGE);
        guiGraphics.drawString(font, Component.literal((selectionPage + 1) + "/" + totalPages),
                left + 132, top + effectivePanelHeight() - 24, 0xFFBBA86D, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
