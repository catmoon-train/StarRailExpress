package io.wifi.starrailexpress.client.gui.screen.roster;

import com.google.gson.Gson;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import io.wifi.starrailexpress.network.RoleRosterUpdatePayload;
import io.wifi.starrailexpress.roster.RoleRosterState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public class RoleRosterEditScreen extends net.minecraft.client.gui.screens.Screen {
    private static final Gson GSON = new Gson();

    // ── 布局常量 ────────────────────
    private static final int PAD = 14;
    private static final int SCROLLBAR_W = 4;
    private static final int CARD_H = 22;
    private static final int CARD_GAP = 6;
    private static final int CARD_MIN_W = 158;
    private static final int HEADER_H = 18;
    private static final int SECTION_GAP = 10;
    private static final int PILL_W = 34;
    private static final int PILL_H = 12;
    private static final int MODIFIER_COLOR = 0xFFB084E0;

    private record GroupDef(String labelKey, int color, IntPredicate matches) {}
    private static final GroupDef[] GROUPS = {
            new GroupDef("display.type.role.innocent", 0xFF44BB66, t -> t == 0 || t == 1),
            new GroupDef("display.type.role.vigilante", 0xFF22BBCC, t -> t == 5),
            new GroupDef("display.type.role.neutral", 0xFFCCAA22, t -> t == 2),
            new GroupDef("display.type.role.neutral_for_killer", 0xFFAA44CC, t -> t == 3),
            new GroupDef("display.type.role.killer", 0xFFCC2233, t -> t == 4),
            new GroupDef("display.type.role", 0xFFC9A84C, t -> true),
    };

    private record Item(SRERole role, String id, Component name, int color, boolean modifier) {}

    private static final class Group {
        final String labelKey;
        final int color;
        final List<Item> items = new ArrayList<>();
        Group(String labelKey, int color) { this.labelKey = labelKey; this.color = color; }
    }

    private static final class CardRect {
        final Item item;
        final int x, y, w, h;
        CardRect(Item item, int x, int y, int w, int h) {
            this.item = item; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    private RoleRosterState working;
    private final List<Group> groups = new ArrayList<>();
    private final List<CardRect> cards = new ArrayList<>();
    private final List<int[]> dividers = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentW, listTop, listBottom;
    private int contentHeight;
    private float scroll;

    private Button toggleButton;

    // ── 滚动条拖拽支持 ──
    private boolean draggingScrollbar;
    private double scrollbarDragOffset;

    public RoleRosterEditScreen() {
        super(Component.translatable("gui.sre.role_roster.edit.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.working = ClientRoleRosterCache.snapshot();

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

        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.enable_all"), b -> enableAll())
                .bounds(bx, by, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.disable_all"), b -> disableAll())
                .bounds(bx + 74, by, 70, 20).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            working.enabled = !working.enabled;
            toggleButton.setMessage(toggleLabel());
        }).bounds(bx + 148, by, 80, 20).build());

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

    private void rebuildGroups() {
        groups.clear();
        Group[] buckets = new Group[GROUPS.length];
        for (int i = 0; i < GROUPS.length; i++) {
            buckets[i] = new Group(GROUPS[i].labelKey, GROUPS[i].color);
        }
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (!AbstractRoleRosterScreen.isRosterEligible(role)) continue;
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
            if (!g.items.isEmpty()) groups.add(g);
        }

        Group modifierGroup = new Group("display.type.modifier", MODIFIER_COLOR);
        for (SREModifier modifier : HMLModifiers.MODIFIERS) {
            if (!AbstractRoleRosterScreen.isModifierEligible(modifier)) continue;
            modifierGroup.items.add(new Item(null, modifier.identifier().toString(),
                    RoleUtils.getModifierName(modifier), modifier.color(), true));
        }
        if (!modifierGroup.items.isEmpty()) groups.add(modifierGroup);
    }

    private void layout() {
        cards.clear();
        dividers.clear();

        int columns = Math.max(1, (contentW + CARD_GAP) / (CARD_MIN_W + CARD_GAP));
        int cardW = (contentW - (columns - 1) * CARD_GAP) / columns;

        int y = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            Group g = groups.get(gi);
            if (gi > 0) y += SECTION_GAP;
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

    private int enabledCount() {
        int n = 0;
        for (CardRect c : cards) {
            if (isEnabled(c.item)) n++;
        }
        return n;
    }

    private boolean isEnabled(Item item) {
        return item.modifier() ? working.modifierCountFor(item.id()) > 0 : working.countFor(item.id()) > 0;
    }

    private void enableAll() {
        for (CardRect c : cards) counts(c.item).put(c.item.id(), 1);
    }

    private void disableAll() {
        working.roleCounts.clear();
        working.modifierCounts.clear();
    }

    private void toggle(Item item) {
        var counts = counts(item);
        if (isEnabled(item)) counts.remove(item.id());
        else counts.put(item.id(), 1);
    }

    private java.util.Map<String, Integer> counts(Item item) {
        return item.modifier() ? working.modifierCounts : working.roleCounts;
    }

    // ── 滚动与拖拽 ──

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) scrollY * 18;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 优先处理滚动条拖拽
        int viewport = listBottom - listTop;
        if (button == 0 && contentHeight > viewport) {
            int barX = panelX + panelW - 1 - SCROLLBAR_W;
            int thumbH = Math.max(20, (int) ((float) viewport / contentHeight * viewport));
            int maxScroll = contentHeight - viewport;
            int thumbY = listTop + (int) ((scroll / maxScroll) * (viewport - thumbH));
            if (mouseX >= barX && mouseX <= barX + SCROLLBAR_W - 1
                    && mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                draggingScrollbar = true;
                scrollbarDragOffset = mouseY - thumbY;
                return true;
            }
        }

        // 原有卡片点击切换
        if (button == 0 && mouseY >= listTop && mouseY <= listBottom) {
            int off = (int) scroll;
            for (CardRect c : cards) {
                int sy = listTop + c.y - off;
                if (sy + c.h < listTop || sy > listBottom) continue;
                if (mouseX >= c.x && mouseX <= c.x + c.w && mouseY >= sy && mouseY <= sy + c.h) {
                    toggle(c.item);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            int viewport = listBottom - listTop;
            int thumbH = Math.max(20, (int) ((float) viewport / contentHeight * viewport));
            int maxScroll = contentHeight - viewport;
            double newThumbY = mouseY - scrollbarDragOffset - listTop;
            scroll = (float) (newThumbY / (viewport - thumbH) * maxScroll);
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ── 渲染 ──

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        RoleRosterStyle.renderBackdrop(g, this.width, this.height);
        RoleRosterStyle.drawPanel(g, panelX, panelY, panelW, panelH, RoleRosterStyle.PANEL_BG,
                RoleRosterStyle.PANEL_BORDER);

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
    }

    private void enableScissor(GuiGraphics g) {
        g.enableScissor(panelX + 1, listTop, panelX + panelW - 1, listBottom);
    }

    private void renderGroups(GuiGraphics g, int mouseX, int mouseY, int off) {
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

        for (CardRect c : cards) {
            int sy = listTop + c.y - off;
            if (sy + c.h < listTop || sy > listBottom) continue;
            renderCard(g, c.item, c.x, sy, c.w, c.h, mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics g, Item item, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
                && mouseY >= listTop && mouseY <= listBottom;
        RoleRosterStyle.drawPanel(g, x, y, w, h,
                hovered ? RoleRosterStyle.ROW_BG_HOVER : RoleRosterStyle.ROW_BG, RoleRosterStyle.ROW_BORDER);

        boolean on = isEnabled(item);
        int barColor = (item.color() == 0 ? RoleRosterStyle.ACCENT : item.color()) | 0xFF000000;
        g.fill(x + 1, y + 1, x + 4, y + h - 1, on ? barColor : 0xFF4A4038);

        int textX = x + 9;
        int pillX = x + w - 5 - PILL_W;
        int nameColor = on
                ? (hovered ? RoleRosterStyle.TEXT_HOVER : (item.color() == 0 ? RoleRosterStyle.TEXT : item.color()))
                : 0x807060;
        String name = this.font.plainSubstrByWidth(item.name().getString(), pillX - textX - 4);
        g.drawString(this.font, name, textX, y + (h - this.font.lineHeight) / 2, nameColor, false);

        int pillY = y + (h - PILL_H) / 2;
        int pillBg = on ? 0x3344BB66 : 0x33CC2233;
        int pillBorder = on ? 0xFF44BB66 : 0xFF8A5050;
        RoleRosterStyle.drawPanel(g, pillX, pillY, PILL_W, PILL_H, pillBg, pillBorder);
        Component pillText = on
                ? Component.translatable("gui.sre.role_roster.card.on")
                : Component.translatable("gui.sre.role_roster.card.off");
        int ptw = this.font.width(pillText);
        g.drawString(this.font, pillText, pillX + (PILL_W - ptw) / 2, pillY + 2,
                on ? RoleRosterStyle.ENABLED_GREEN : RoleRosterStyle.DISABLED_RED, false);
    }

    private void renderScrollbar(GuiGraphics g) {
        int viewport = listBottom - listTop;
        if (contentHeight <= viewport) return;
        int barX = panelX + panelW - 1 - SCROLLBAR_W;
        g.fill(barX, listTop, barX + SCROLLBAR_W - 1, listBottom, 0x40FFE8C0);
        int thumbH = Math.max(20, (int) ((float) viewport / contentHeight * viewport));
        int maxScroll = contentHeight - viewport;
        int thumbY = listTop + (int) ((scroll / maxScroll) * (viewport - thumbH));
        g.fill(barX, thumbY, barX + SCROLLBAR_W - 1, thumbY + thumbH, 0x88C9A84C);
    }

    private void save() {
        working.normalized();
        ClientPlayNetworking.send(new RoleRosterUpdatePayload("set", GSON.toJson(working)));
        this.onClose();
    }
}