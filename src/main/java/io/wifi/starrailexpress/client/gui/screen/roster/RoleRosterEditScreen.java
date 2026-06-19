package io.wifi.starrailexpress.client.gui.screen.roster;

import com.google.gson.Gson;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import io.wifi.starrailexpress.network.RoleRosterUpdatePayload;
import io.wifi.starrailexpress.roster.RoleRosterState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * 管理员编辑界面：横向长方形面板，按阵营分组以网格铺开所有可选职业 / 修饰符，
 * 每个条目都可用 [-] [+] 增减名额；底部可输入随机抽选数量、随机抽选、清空、开关名单并保存。
 * <p>
 * 布局参照 {@link RoleRosterViewScreen}，但条目均可编辑且展示全部可选项（数量为 0 表示未在名单内）。
 */
public class RoleRosterEditScreen extends net.minecraft.client.gui.screens.Screen {
    private static final Gson GSON = new Gson();

    // ── 布局常量（与 RoleRosterViewScreen 对齐）────────────────────
    private static final int PAD = 14;
    private static final int SCROLLBAR_W = 4;
    private static final int CARD_H = 22;
    private static final int CARD_GAP = 6;
    private static final int CARD_MIN_W = 158;
    private static final int HEADER_H = 18;
    private static final int SECTION_GAP = 10;

    /** 卡片内 [-] / [+] 步进按钮边长。 */
    private static final int STEP = 11;

    /** 修饰符栏目使用的统一颜色（紫色系）。 */
    private static final int MODIFIER_COLOR = 0xFFB084E0;

    /** 阵营栏目定义：标签、颜色、对应的 roleType 判定。按数组顺序自上而下排列。 */
    private record GroupDef(String labelKey, int color, IntPredicate matches) {
    }

    private static final GroupDef[] GROUPS = {
            new GroupDef("display.type.role.innocent", 0xFF44BB66, t -> t == 0 || t == 1),
            new GroupDef("display.type.role.vigilante", 0xFF22BBCC, t -> t == 5),
            new GroupDef("display.type.role.neutral", 0xFFCCAA22, t -> t == 2),
            new GroupDef("display.type.role.neutral_for_killer", 0xFFAA44CC, t -> t == 3),
            new GroupDef("display.type.role.killer", 0xFFCC2233, t -> t == 4),
            // 兜底：未归类的职业
            new GroupDef("display.type.role", 0xFFC9A84C, t -> true),
    };

    /** 列表条目：职业或修饰符（修饰符 role 为 null）。 */
    private record Item(SRERole role, String id, Component name, int color, boolean modifier) {
    }

    private static final class Group {
        final String labelKey;
        final int color;
        final List<Item> items = new ArrayList<>();

        Group(String labelKey, int color) {
            this.labelKey = labelKey;
            this.color = color;
        }
    }

    /** 渲染 / 点击共用的条目矩形，y 为相对内容顶部的逻辑坐标（未含滚动偏移）。 */
    private static final class CardRect {
        final Item item;
        final int x, y, w, h;

        CardRect(Item item, int x, int y, int w, int h) {
            this.item = item;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private RoleRosterState working;
    private final List<Group> groups = new ArrayList<>();
    private final List<CardRect> cards = new ArrayList<>();
    private final List<int[]> dividers = new ArrayList<>(); // {x0, y, x1}（y 为逻辑坐标）

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentW, listTop, listBottom;
    private int contentHeight;
    private float scroll;

    private EditBox countBox;
    private Button toggleButton;

    public RoleRosterEditScreen() {
        super(Component.translatable("gui.sre.role_roster.edit.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.working = ClientRoleRosterCache.snapshot();

        // 横向长方形面板
        this.panelW = Math.min(this.width - 40, 640);
        this.panelH = Math.min(this.height - 40, 420);
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;
        this.contentX = panelX + PAD;
        this.contentW = panelW - PAD * 2 - SCROLLBAR_W;
        this.listTop = panelY + 42;
        this.listBottom = panelY + panelH - 34;

        rebuildGroups();
        layout();
        buildControls();
    }

    private void buildControls() {
        int by = panelY + panelH - 28;
        int bx = panelX + PAD;

        // 左侧：[随机抽选] [数量输入框] [清空] [开关名单]
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.randomize"), b -> randomizeLocal())
                .bounds(bx, by, 70, 20).build());

        countBox = new EditBox(this.font, bx + 74, by, 36, 20, Component.translatable("gui.sre.role_roster.randomize"));
        countBox.setMaxLength(2);
        countBox.setValue("5");
        countBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
        addRenderableWidget(countBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.enable_all"), b -> enableAll())
                .bounds(bx + 114, by, 56, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.disable_all"), b -> disableAll())
                .bounds(bx + 174, by, 56, 20).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            working.enabled = !working.enabled;
            toggleButton.setMessage(toggleLabel());
        }).bounds(bx + 234, by, 66, 20).build());

        // 右侧：[保存] [关闭]
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.save"), b -> save())
                .bounds(panelX + panelW - PAD - 124, by, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.close"), b -> this.onClose())
                .bounds(panelX + panelW - PAD - 60, by, 60, 20).build());
    }

    private Component toggleLabel() {
        return working.enabled
                ? Component.translatable("gui.sre.role_roster.toggle.on")
                : Component.translatable("gui.sre.role_roster.toggle.off");
    }

    /** 按阵营把所有可选职业分到各栏目，并在末尾追加修饰符栏目（编辑界面展示全部可选项，含数量为 0 的）。 */
    private void rebuildGroups() {
        groups.clear();
        Group[] buckets = new Group[GROUPS.length];
        for (int i = 0; i < GROUPS.length; i++) {
            buckets[i] = new Group(GROUPS[i].labelKey, GROUPS[i].color);
        }
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (!AbstractRoleRosterScreen.isRosterEligible(role)) {
                continue;
            }
            int type = PlayerRoleWeightManager.getRoleType(role);
            for (int i = 0; i < GROUPS.length; i++) {
                if (GROUPS[i].matches.test(type)) {
                    buckets[i].items.add(new Item(role, role.identifier().toString(),
                            RoleUtils.getRoleName(role), role.color(), false));
                    break;
                }
            }
        }
        for (Group g : buckets) {
            if (!g.items.isEmpty()) {
                groups.add(g);
            }
        }

        // 修饰符栏目
        Group modifierGroup = new Group("display.type.modifier", MODIFIER_COLOR);
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (!AbstractRoleRosterScreen.isModifierEligible(modifier)) {
                continue;
            }
            modifierGroup.items.add(new Item(null, modifier.identifier().toString(),
                    RoleUtils.getModifierName(modifier), modifier.color(), true));
        }
        if (!modifierGroup.items.isEmpty()) {
            groups.add(modifierGroup);
        }
    }

    /** 计算每个栏目标题、切割线与职业条目的逻辑坐标。 */
    private void layout() {
        cards.clear();
        dividers.clear();

        int columns = Math.max(1, (contentW + CARD_GAP) / (CARD_MIN_W + CARD_GAP));
        int cardW = (contentW - (columns - 1) * CARD_GAP) / columns;

        int y = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            Group g = groups.get(gi);
            if (gi > 0) {
                y += SECTION_GAP;
            }
            dividers.add(new int[] { contentX, y + HEADER_H - 4, contentX + contentW });
            int gridTop = y + HEADER_H;

            for (int i = 0; i < g.items.size(); i++) {
                int col = i % columns;
                int row = i / columns;
                int cx = contentX + col * (cardW + CARD_GAP);
                int cy = gridTop + row * (CARD_H + CARD_GAP);
                cards.add(new CardRect(g.items.get(i), cx, cy, cardW, CARD_H));
            }
            int rows = (g.items.size() + columns - 1) / columns;
            y = gridTop + rows * (CARD_H + CARD_GAP);
        }
        contentHeight = y;
        clampScroll();
    }

    private void clampScroll() {
        int viewport = listBottom - listTop;
        float max = Math.max(0, contentHeight - viewport);
        scroll = Mth.clamp(scroll, 0, max);
    }

    /** 名单内（数量 > 0）的条目数量，用于顶部提示。 */
    private int enabledCount() {
        int n = 0;
        for (CardRect c : cards) {
            if (countOf(c.item) > 0) {
                n++;
            }
        }
        return n;
    }

    private int countOf(Item item) {
        return item.modifier() ? working.modifierCountFor(item.id()) : working.countFor(item.id());
    }

    /** 把所有当前未启用（数量为 0）的可选条目设为 1，保留已有的更高数量。 */
    private void enableAll() {
        for (CardRect c : cards) {
            if (countOf(c.item) <= 0) {
                var counts = c.item.modifier() ? working.modifierCounts : working.roleCounts;
                counts.put(c.item.id(), 1);
            }
        }
    }

    /** 清空所有职业与修饰符名额。 */
    private void disableAll() {
        working.roleCounts.clear();
        working.modifierCounts.clear();
    }

    private void adjust(Item item, int delta) {
        var counts = item.modifier() ? working.modifierCounts : working.roleCounts;
        int next = Math.max(0, countOf(item) + delta);
        if (next <= 0) {
            counts.remove(item.id());
        } else {
            counts.put(item.id(), next);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) scrollY * 18;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= listTop && mouseY <= listBottom) {
            int off = (int) scroll;
            for (CardRect c : cards) {
                int sy = listTop + c.y - off;
                if (sy + c.h < listTop || sy > listBottom) {
                    continue;
                }
                int stepY = sy + (c.h - STEP) / 2;
                if (mouseY >= stepY && mouseY <= stepY + STEP) {
                    int plusX = c.x + c.w - 5 - STEP;
                    int minusX = plusX - 20 - STEP;
                    if (mouseX >= plusX && mouseX <= plusX + STEP) {
                        adjust(c.item, 1);
                        return true;
                    }
                    if (mouseX >= minusX && mouseX <= minusX + STEP) {
                        adjust(c.item, -1);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.renderBackground(g, mouseX, mouseY, delta);
        RoleRosterStyle.renderBackdrop(g, this.width, this.height);
        RoleRosterStyle.drawPanel(g, panelX, panelY, panelW, panelH, RoleRosterStyle.PANEL_BG, RoleRosterStyle.PANEL_BORDER);

        // 标题 + 状态 + 提示
        g.drawString(this.font, this.title, panelX + 16, panelY + 14, RoleRosterStyle.TITLE, false);
        Component status = working.enabled
                ? Component.translatable("gui.sre.role_roster.status.on")
                : Component.translatable("gui.sre.role_roster.status.off");
        int statusColor = working.enabled ? RoleRosterStyle.ENABLED_GREEN : RoleRosterStyle.DISABLED_RED;
        int statusW = this.font.width(status);
        g.drawString(this.font, status, panelX + panelW - 16 - statusW, panelY + 14, statusColor, false);
        g.drawString(this.font, Component.translatable("gui.sre.role_roster.hint", enabledCount()),
                panelX + 16, panelY + 30, RoleRosterStyle.SUBTITLE, false);

        int off = (int) scroll;
        enableScissor(g);
        try {
            renderGroups(g, mouseX, mouseY, off);
        } finally {
            g.disableScissor();
        }

        renderScrollbar(g);
        super.render(g, mouseX, mouseY, delta);
    }

    private void enableScissor(GuiGraphics g) {
        g.enableScissor(panelX + 1, listTop, panelX + panelW - 1, listBottom);
    }

    private void renderGroups(GuiGraphics g, int mouseX, int mouseY, int off) {
        // 栏目标题 + 切割线
        for (int gi = 0; gi < groups.size(); gi++) {
            Group group = groups.get(gi);
            int[] div = dividers.get(gi);
            int divY = listTop + div[1] - off;
            int headerY = divY - HEADER_H + 4;
            if (headerY + HEADER_H >= listTop && headerY <= listBottom) {
                Component label = Component.translatable(group.labelKey);
                g.drawString(this.font, label, contentX, headerY + 3, group.color, false);
                int labelW = this.font.width(label);
                String count = "× " + group.items.size();
                g.drawString(this.font, count, contentX + labelW + 6, headerY + 3, RoleRosterStyle.MUTED, false);
                int lineStartX = contentX + labelW + 6 + this.font.width(count) + 8;
                if (lineStartX < div[2]) {
                    g.fill(lineStartX, divY, div[2], divY + 1, (group.color & 0x00FFFFFF) | 0x55000000);
                }
            }
        }

        // 条目
        for (CardRect c : cards) {
            int sy = listTop + c.y - off;
            if (sy + c.h < listTop || sy > listBottom) {
                continue;
            }
            renderCard(g, c.item, c.x, sy, c.w, c.h, mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics g, Item item, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
                && mouseY >= listTop && mouseY <= listBottom;
        RoleRosterStyle.drawPanel(g, x, y, w, h,
                hovered ? RoleRosterStyle.ROW_BG_HOVER : RoleRosterStyle.ROW_BG, RoleRosterStyle.ROW_BORDER);

        int count = countOf(item);

        // 左侧颜色条（未启用时变暗）
        int barColor = (item.color() == 0 ? RoleRosterStyle.ACCENT : item.color()) | 0xFF000000;
        g.fill(x + 1, y + 1, x + 4, y + h - 1, count > 0 ? barColor : 0xFF4A4038);

        // 名称（截断以给右侧步进控件留位）
        int textX = x + 9;
        int plusX = x + w - 5 - STEP;
        int minusX = plusX - 20 - STEP;
        int nameColor = count > 0
                ? (hovered ? RoleRosterStyle.TEXT_HOVER : (item.color() == 0 ? RoleRosterStyle.TEXT : item.color()))
                : 0x807060;
        String name = this.font.plainSubstrByWidth(item.name().getString(), minusX - textX - 4);
        g.drawString(this.font, name, textX, y + (h - this.font.lineHeight) / 2, nameColor, false);

        // 右侧 [-] N [+]
        int stepY = y + (h - STEP) / 2;
        drawStepper(g, "-", minusX, stepY, mouseX, mouseY);
        drawStepper(g, "+", plusX, stepY, mouseX, mouseY);
        String countText = String.valueOf(count);
        int cw = this.font.width(countText);
        int countCenter = (minusX + STEP + plusX) / 2;
        g.drawString(this.font, countText, countCenter - cw / 2, y + (h - this.font.lineHeight) / 2,
                count > 0 ? RoleRosterStyle.ACCENT_HOVER : RoleRosterStyle.MUTED, false);
    }

    private void drawStepper(GuiGraphics g, String label, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + STEP && mouseY >= y && mouseY <= y + STEP
                && mouseY >= listTop && mouseY <= listBottom;
        RoleRosterStyle.drawPanel(g, x, y, STEP, STEP,
                hovered ? RoleRosterStyle.ROW_BG_HOVER : RoleRosterStyle.ROW_BG, RoleRosterStyle.PANEL_BORDER);
        int tw = this.font.width(label);
        g.drawString(this.font, label, x + (STEP - tw) / 2 + 1, y + 2,
                hovered ? RoleRosterStyle.TEXT_HOVER : RoleRosterStyle.TEXT, false);
    }

    private void renderScrollbar(GuiGraphics g) {
        int viewport = listBottom - listTop;
        if (contentHeight <= viewport) {
            return;
        }
        int barX = panelX + panelW - 1 - SCROLLBAR_W;
        g.fill(barX, listTop, barX + SCROLLBAR_W - 1, listBottom, 0x40FFE8C0);
        int thumbH = Math.max(20, (int) ((float) viewport / contentHeight * viewport));
        int maxScroll = contentHeight - viewport;
        int thumbY = listTop + (int) ((scroll / maxScroll) * (viewport - thumbH));
        g.fill(barX, thumbY, barX + SCROLLBAR_W - 1, thumbY + thumbH, 0x88C9A84C);
    }

    /** 读取输入框中的随机抽选数量，限制在 1..99。 */
    private int randomCount() {
        int n = 5;
        try {
            String v = countBox.getValue().trim();
            if (!v.isEmpty()) {
                n = Integer.parseInt(v);
            }
        } catch (NumberFormatException ignored) {
            // 使用默认值
        }
        n = Mth.clamp(n, 1, 99);
        countBox.setValue(String.valueOf(n));
        return n;
    }

    /**
     * 随机抽选若干个非平民职业，每个职业分配 1 个名额。
     * 平民始终保留（数量 = max(2, 在线人数)），且确保至少包含一个杀手职业。
     */
    private void randomizeLocal() {
        int randomCount = randomCount();
        Random random = new Random();
        int targetPlayers = onlinePlayerCount();
        working.roleCounts.clear();

        // 收集可选的非平民职业
        List<SRERole> pool = new ArrayList<>();
        boolean hasKillerInPool = false;
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (!AbstractRoleRosterScreen.isRosterEligible(role)) continue;
            if (role == TMMRoles.CIVILIAN) continue;
            pool.add(role);
            if (role.canUseKiller()) hasKillerInPool = true;
        }

        // 随机抽选 randomCount 个
        Collections.shuffle(pool, random);
        int toPick = Math.min(randomCount, pool.size());
        boolean hasKiller = false;
        for (int i = 0; i < toPick; i++) {
            SRERole role = pool.get(i);
            working.roleCounts.put(role.identifier().toString(), 1);
            if (role.canUseKiller()) hasKiller = true;
        }

        // 确保至少有一个杀手职业
        if (!hasKiller && hasKillerInPool) {
            for (SRERole role : pool) {
                if (role.canUseKiller() && !working.roleCounts.containsKey(role.identifier().toString())) {
                    working.roleCounts.put(role.identifier().toString(), 1);
                    break;
                }
            }
            // 如果池子里的杀手已被抽完，就替换一个非杀手职业
            if (!hasKiller) {
                for (SRERole role : pool) {
                    if (role.canUseKiller()) {
                        working.roleCounts.put(role.identifier().toString(), 1);
                        break;
                    }
                }
            }
        }

        // 平民始终包含
        working.roleCounts.put(TMMRoles.CIVILIAN.identifier().toString(), Math.max(2, targetPlayers));
    }

    private int onlinePlayerCount() {
        try {
            if (this.minecraft != null && this.minecraft.getConnection() != null) {
                int size = this.minecraft.getConnection().getListedOnlinePlayers().size();
                if (size > 0) {
                    return size;
                }
            }
        } catch (Throwable ignored) {
            // 使用默认值
        }
        return 8;
    }

    private void save() {
        working.normalized();
        ClientPlayNetworking.send(new RoleRosterUpdatePayload("set", GSON.toJson(working)));
        this.onClose();
    }
}
