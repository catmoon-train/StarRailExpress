package io.wifi.starrailexpress.client.gui.screen.roster;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.roster.RoleRosterState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 职业轮换名单的“随机抽选”弹窗：让管理员一次随机启用<strong>多个</strong>职业与修饰符。
 * <p>
 * 作为 {@link RoleRosterEditScreen} 的子界面存在，直接改动父界面传入的同一份 {@code working}
 * 状态；点击“抽选并启用”后返回父界面即可看到结果，父界面点“保存”才真正下发到服务端。
 * <p>
 * 抽选池与父界面完全一致（沿用 {@link AbstractRoleRosterScreen#isRosterEligible} /
 * {@link AbstractRoleRosterScreen#isModifierEligible} 的过滤口径），避免抽到其它模式的职业。
 */
final class RoleRosterRandomScreen extends Screen {
    private static final int STEP = 12;

    private final Screen parent;
    private final RoleRosterState working;
    private final List<String> rolePool = new ArrayList<>();
    private final List<String> modPool = new ArrayList<>();

    private int roleN;
    private int modN;
    /** true = 清空现有名单后抽选；false = 在现有基础上追加抽选。 */
    private boolean replace = true;

    private int panelX, panelY, panelW, panelH;
    private int roleRowY, modRowY;
    private Button replaceButton;

    RoleRosterRandomScreen(Screen parent, RoleRosterState working) {
        super(Component.translatable("gui.sre.role_roster.random.title"));
        this.parent = parent;
        this.working = working;
        buildPools();
        this.roleN = Math.min(5, rolePool.size());
        this.modN = 0;
    }

    private void buildPools() {
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (AbstractRoleRosterScreen.isRosterEligible(role)) {
                rolePool.add(role.identifier().toString());
            }
        }
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (AbstractRoleRosterScreen.isModifierEligible(modifier)) {
                modPool.add(modifier.identifier().toString());
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.panelW = 300;
        this.panelH = 178;
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;
        this.roleRowY = panelY + 56;
        this.modRowY = panelY + 82;

        this.roleN = Mth.clamp(roleN, 0, rolePool.size());
        this.modN = Mth.clamp(modN, 0, modPool.size());

        int mw = panelW - 40;
        replaceButton = addRenderableWidget(Button.builder(replaceLabel(), b -> {
            replace = !replace;
            replaceButton.setMessage(replaceLabel());
        }).bounds(panelX + 20, panelY + 112, mw, 18).build());

        int half = (panelW - 48) / 2;
        int by = panelY + panelH - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.random.confirm"), b -> apply())
                .bounds(panelX + 20, by, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.random.cancel"), b -> onClose())
                .bounds(panelX + 28 + half, by, half, 20).build());
    }

    private Component replaceLabel() {
        return Component.translatable(replace
                ? "gui.sre.role_roster.random.mode.replace"
                : "gui.sre.role_roster.random.mode.append");
    }

    // ── 抽选逻辑 ──

    private void apply() {
        Random rng = new Random();
        if (replace) {
            working.roleCounts.clear();
            working.modifierCounts.clear();
        }
        drawInto(working.roleCounts, rolePool, roleN, rng);
        drawInto(working.modifierCounts, modPool, modN, rng);
        this.minecraft.setScreen(parent);
    }

    /**
     * 从 {@code pool} 里随机挑选 {@code n} 个<strong>当前未启用</strong>的条目并置为启用（值 1）。
     * 只从未启用条目里抽，保证追加模式下真的多启用 n 个，而不会把名额浪费在已启用项上。
     */
    private static void drawInto(Map<String, Integer> counts, List<String> pool, int n, Random rng) {
        if (n <= 0) {
            return;
        }
        List<String> candidates = new ArrayList<>();
        for (String id : pool) {
            Integer v = counts.get(id);
            if (v == null || v <= 0) {
                candidates.add(id);
            }
        }
        Collections.shuffle(candidates, rng);
        for (int i = 0; i < n && i < candidates.size(); i++) {
            counts.put(candidates.get(i), 1);
        }
    }

    // ── 交互 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hit(mouseX, mouseY, minusX(), roleRowY)) { adjustRole(-1); return true; }
            if (hit(mouseX, mouseY, plusX(), roleRowY)) { adjustRole(1); return true; }
            if (hit(mouseX, mouseY, minusX(), modRowY)) { adjustMod(-1); return true; }
            if (hit(mouseX, mouseY, plusX(), modRowY)) { adjustMod(1); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void adjustRole(int delta) {
        roleN = Mth.clamp(roleN + delta, 0, rolePool.size());
        click();
    }

    private void adjustMod(int delta) {
        modN = Mth.clamp(modN + delta, 0, modPool.size());
        click();
    }

    private void click() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private int minusX() { return panelX + panelW - 92; }
    private int plusX() { return panelX + panelW - 32; }

    private boolean hit(double mx, double my, int x, int rowY) {
        int y = rowY - 1;
        return mx >= x && mx <= x + STEP && my >= y && my <= y + STEP;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ── 渲染 ──

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        RoleRosterStyle.renderBackdrop(g, this.width, this.height);
        RoleRosterStyle.drawPanel(g, panelX, panelY, panelW, panelH,
                RoleRosterStyle.PANEL_BG, RoleRosterStyle.PANEL_BORDER);

        g.drawString(this.font, this.title, panelX + 16, panelY + 14, RoleRosterStyle.TITLE, false);
        g.drawString(this.font, Component.translatable("gui.sre.role_roster.random.hint"),
                panelX + 16, panelY + 30, RoleRosterStyle.SUBTITLE, false);

        renderRow(g, roleRowY, Component.translatable("gui.sre.role_roster.random.roles"), roleN, rolePool.size(), mouseX, mouseY);
        renderRow(g, modRowY, Component.translatable("gui.sre.role_roster.random.modifiers"), modN, modPool.size(), mouseX, mouseY);
    }

    private void renderRow(GuiGraphics g, int rowY, Component label, int value, int max, int mouseX, int mouseY) {
        g.drawString(this.font, label, panelX + 20, rowY + 2, RoleRosterStyle.TEXT, false);

        drawStepper(g, "-", minusX(), rowY - 1, mouseX, mouseY);
        drawStepper(g, "+", plusX(), rowY - 1, mouseX, mouseY);

        String text = value + " / " + max;
        int tw = this.font.width(text);
        int mid = (minusX() + STEP + plusX()) / 2;
        g.drawString(this.font, text, mid - tw / 2, rowY + 2,
                value > 0 ? RoleRosterStyle.ACCENT_HOVER : RoleRosterStyle.MUTED, false);
    }

    private void drawStepper(GuiGraphics g, String label, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + STEP && mouseY >= y && mouseY <= y + STEP;
        RoleRosterStyle.drawPanel(g, x, y, STEP, STEP,
                hovered ? RoleRosterStyle.ROW_BG_HOVER : RoleRosterStyle.ROW_BG, RoleRosterStyle.PANEL_BORDER);
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (STEP - tw) / 2 + 1, y + 2,
                hovered ? RoleRosterStyle.TEXT_HOVER : RoleRosterStyle.TEXT, false);
    }
}
