// RoleStatsPanel.java (重写后)
package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.ReplayDisplayUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.Noellesroles;

import java.util.*;
import java.util.function.BiConsumer;

public class RoleStatsPanel implements Renderable, GuiEventListener, NarratableEntry {
    private final SREPlayerStatsComponent stats;
    private ScrollableRoleListComponent roleListComponent;
    private RoleDetailsComponent roleDetailsComponent;
    private EditBox searchBox;

    private final int x, y, width, height;
    private boolean visible = true;
    private final List<GuiEventListener> children = new ArrayList<>();
    private final List<Renderable> renderables = new ArrayList<>();

    public RoleStatsPanel(int x, int y, int width, int height, SREPlayerStatsComponent stats) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.stats = stats;
        setupComponents();
    }

    private void setupComponents() {
        int searchX = x + 10;
        int searchY = y + 30;
        int searchW = width - 20;
        searchBox = new EditBox(Minecraft.getInstance().font, searchX, searchY, searchW, 20,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.search_role"));
        searchBox.setHint(Component.translatable("screen." + SRE.MOD_ID + ".player_stats.search_role")
                .withStyle(s -> s.withColor(0xFF888888)));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchTextChanged);
        addRenderableWidget(searchBox);

        int listY = searchY + 25;
        int listH = (height - (listY - y)) / 2 - 5;
        roleListComponent = new ScrollableRoleListComponent(x + 10, listY, width - 20, listH, this::onRoleSelected);
        addRenderableWidget(roleListComponent);

        int detailY = listY + listH + 10;
        int detailH = height - (detailY - y) - 10;
        roleDetailsComponent = new RoleDetailsComponent(x + 10, detailY, width - 20, detailH);
        addRenderableWidget(roleDetailsComponent);

        List<SRERole> roles = new ArrayList<>();
        Map<ResourceLocation, SREPlayerStatsComponent.RoleStats> roleStatsMap = stats.getRoleStats();
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (roleStatsMap.containsKey(role.identifier()))
                roles.add(role);
        }
        roles.sort(Comparator.comparing(r -> r.identifier().getPath()));
        roleListComponent.setRoles(roles, roleStatsMap);
        if (!roles.isEmpty())
            onRoleSelected(roles.get(0), roleStatsMap.get(roles.get(0).identifier()));
    }

    private void onSearchTextChanged(String s) {
        roleListComponent.filterRoles(s);
    }

    private void onRoleSelected(SRERole r, SREPlayerStatsComponent.RoleStats rs) {
        roleDetailsComponent.setRole(r, rs);
    }

    private void addRenderableWidget(Renderable r) {
        renderables.add(r);
        if (r instanceof GuiEventListener)
            children.add((GuiEventListener) r);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        if (!visible)
            return;
        drawPanelBg(g, x, y, width, height);
        g.drawString(Minecraft.getInstance().font,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.role_stats")
                        .withStyle(s -> s.withBold(true)),
                x + 10, y + 10, 0xFFFFFFFF);
        for (Renderable r : renderables)
            r.render(g, mx, my, delta);
    }

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD80C1020, 0xD8101828);
        g.renderOutline(x, y, w, h, 0xFF1E3060);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    public void setVisible(boolean v) {
        this.visible = v;
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (searchBox.isFocused() && searchBox.keyPressed(k, s, m))
            return true;
        for (GuiEventListener c : children)
            if (c.keyPressed(k, s, m))
                return true;
        return false;
    }

    @Override
    public boolean charTyped(char c, int m) {
        return searchBox.isFocused() && searchBox.charTyped(c, m);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (searchBox.mouseClicked(mx, my, b))
            return true;
        for (GuiEventListener c : children)
            if (c.mouseClicked(mx, my, b))
                return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!visible)
            return false;
        for (GuiEventListener c : children)
            if (c.mouseScrolled(mx, my, sx, sy))
                return true;
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        for (GuiEventListener c : children)
            if (c.mouseDragged(mx, my, b, dx, dy))
                return true;
        return false;
    }

    @Override
    public boolean isFocused() {
        return searchBox.isFocused();
    }

    @Override
    public void setFocused(boolean f) {
        searchBox.setFocused(f);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput o) {
    }

    // ========== 内部类：可滚动角色列表 ==========
    private static class ScrollableRoleListComponent extends AbstractWidget {
        private final BiConsumer<SRERole, SREPlayerStatsComponent.RoleStats> onSelect;
        private List<SRERole> allRoles = new ArrayList<>();
        private Map<ResourceLocation, SREPlayerStatsComponent.RoleStats> roleStatsMap = new HashMap<>();
        private List<SRERole> filteredRoles = new ArrayList<>();
        private double scrollAmount = 0.0;
        private static final int SCROLLBAR_W = 6;
        private final int ITEM_H = 40;
        private SRERole selectedRole;
        private boolean draggingScroll = false;
        private double dragStartY = 0;
        private double dragStartScroll = 0;

        public ScrollableRoleListComponent(int x, int y, int w, int h,
                BiConsumer<SRERole, SREPlayerStatsComponent.RoleStats> cb) {
            super(x, y, w, h, Component.empty());
            this.onSelect = cb;
        }

        public void setRoles(List<SRERole> roles, Map<ResourceLocation, SREPlayerStatsComponent.RoleStats> map) {
            allRoles = roles;
            roleStatsMap = map;
            filteredRoles = new ArrayList<>(roles);
            if (!filteredRoles.isEmpty())
                selectedRole = filteredRoles.get(0);
        }

        public void filterRoles(String search) {
            if (search == null || search.isEmpty())
                filteredRoles = new ArrayList<>(allRoles);
            else {
                filteredRoles = allRoles.stream()
                        .filter(r -> ReplayDisplayUtils.getRoleDisplayName(r.identifier().toString()).getString()
                                .toLowerCase().contains(search.toLowerCase()))
                        .toList();
            }
            scrollAmount = 0;
            if (!filteredRoles.isEmpty() && (selectedRole == null || !filteredRoles.contains(selectedRole))) {
                selectedRole = filteredRoles.get(0);
                onSelect.accept(selectedRole, roleStatsMap.get(selectedRole.identifier()));
            } else if (filteredRoles.isEmpty()) {
                selectedRole = null;
                onSelect.accept(null, null);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());

            int startIdx = (int) (scrollAmount / ITEM_H);
            int endIdx = Math.min(startIdx + (getHeight() / ITEM_H) + 1, filteredRoles.size());

            for (int i = startIdx; i < endIdx; i++) {
                SRERole role = filteredRoles.get(i);
                SREPlayerStatsComponent.RoleStats rs = roleStatsMap.get(role.identifier());
                int itemY = getY() + i * ITEM_H - (int) scrollAmount;
                boolean selected = role.equals(selectedRole);
                boolean hovered = !selected && mx >= getX() && mx <= getX() + getWidth() - SCROLLBAR_W - 2 &&
                        my >= itemY && my <= itemY + ITEM_H;

                drawRoleCard(g, role, rs, getX(), itemY, getWidth() - SCROLLBAR_W - 4, ITEM_H, hovered, selected);
            }

            disableScissor();

            // 滚动条
            int totalH = filteredRoles.size() * ITEM_H;
            if (totalH > getHeight()) {
                int scrollbarX = getX() + getWidth() - SCROLLBAR_W;
                int scrollbarY = getY();
                int scrollbarH = getHeight();
                float ratio = (float) scrollbarH / totalH;
                int thumbH = Math.max(20, (int) (scrollbarH * ratio));
                int maxScroll = totalH - scrollbarH;
                int thumbY = scrollbarY + (int) ((scrollAmount / maxScroll) * (scrollbarH - thumbH));
                boolean hl = draggingScroll || (mx >= scrollbarX && mx <= scrollbarX + SCROLLBAR_W && my >= thumbY
                        && my <= thumbY + thumbH);
                g.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_W, scrollbarY + scrollbarH, 0xFF111828);
                g.fill(scrollbarX + 1, scrollbarY + 1, scrollbarX + SCROLLBAR_W - 1, scrollbarY + scrollbarH - 1,
                        0x55334466);
                g.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, hl ? 0xFF8899CC : 0xFF556699);
                g.fill(scrollbarX + 1, thumbY + 1, scrollbarX + SCROLLBAR_W - 1, thumbY + thumbH - 1,
                        hl ? 0xFFAABBEE : 0xFF7788BB);
            }
        }

        private void drawRoleCard(GuiGraphics g, SRERole role, SREPlayerStatsComponent.RoleStats rs, int x, int y,
                int w, int h, boolean hover, boolean selected) {
            int color = role.getColor() | 0xFF000000;
            int bg = selected ? 0xFF2A3A5A : hover ? blendColors(0xFF1A1F2E, color, 0.3f) : 0xFF1A1F2E;
            g.fill(x, y, x + w, y + h, bg);
            g.renderOutline(x, y, w, h, selected ? color : (hover ? blendColors(color, 0xFFFFFFFF, 0.3f) : 0xFF2A2F3F));

            // 图标
            ResourceLocation icon = getTypeIcon(role);
            if (icon != null) {
                RenderSystem.enableBlend();
                g.blit(icon, x + 5, y + 5, 0, 0, 30, 30, 30, 30);
                RenderSystem.disableBlend();
            } else {
                g.fill(x + 5, y + 5, x + 35, y + 35, 0xFF333333);
            }

            // 名称
            Component name = ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString());
            g.drawString(Minecraft.getInstance().font, name, x + 40, y + 6, role.getColor());
            // 类型
            Component type = getRoleTypeDisplay(role);
            g.drawString(Minecraft.getInstance().font, type, x + 40, y + 18, 0xFFAAAAAA);
            // 简略统计
            if (rs != null) {
                String brief = "场次: " + rs.getTimesPlayed() + "  胜场: " + rs.getWinsAsRole();
                g.drawString(Minecraft.getInstance().font, brief, x + 40, y + 28, 0xFFCCCCCC);
            }
        }

        private Component getRoleTypeDisplay(SRERole r) {
            return switch (PlayerRoleWeightManager.getRoleType(r)) {
                case 0, 1 ->
                    Component.translatable("display.type.role.innocent").withStyle(s -> s.withColor(0xFF44BB66));
                case 2 -> Component.translatable("display.type.role.neutral").withStyle(s -> s.withColor(0xFFCCAA22));
                case 3 -> Component.translatable("display.type.role.neutral_for_killer")
                        .withStyle(s -> s.withColor(0xFFAA44CC));
                case 4 -> Component.translatable("display.type.role.killer").withStyle(s -> s.withColor(0xFFCC2233));
                case 5 -> Component.translatable("display.type.role.vigilante").withStyle(s -> s.withColor(0xFF22BBCC));
                default -> Component.literal("Unknown");
            };
        }

        private ResourceLocation getTypeIcon(SRERole role) {
            int type = PlayerRoleWeightManager.getRoleType(role);
            return switch (type) {
                case 0, 1 -> ResourceLocation.tryParse("wathe:textures/gui/sprites/hud/mood_happy.png");
                case 2 -> ResourceLocation.tryParse("noellesroles:textures/gui/sprites/hud/mood_neu.png");
                case 3 -> ResourceLocation.tryParse("noellesroles:textures/gui/sprites/hud/mood_jester.png");
                case 4 -> ResourceLocation.tryParse("wathe:textures/gui/sprites/hud/mood_killer.png");
                case 5 -> ResourceLocation.tryParse("noellesroles:textures/gui/sprites/hud/mood_vig.png");
                default -> null;
            };
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (!isMouseOver(mx, my))
                return false;
            // 滚动条
            int scrollbarX = getX() + getWidth() - SCROLLBAR_W;
            if (mx >= scrollbarX && mx <= scrollbarX + SCROLLBAR_W && my >= getY() && my <= getY() + getHeight()) {
                draggingScroll = true;
                dragStartY = my;
                dragStartScroll = scrollAmount;
                return true;
            }
            // 卡片
            int idx = (int) ((my - getY() + scrollAmount) / ITEM_H);
            if (idx >= 0 && idx < filteredRoles.size()) {
                selectedRole = filteredRoles.get(idx);
                onSelect.accept(selectedRole, roleStatsMap.get(selectedRole.identifier()));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (draggingScroll) {
                int totalH = filteredRoles.size() * ITEM_H;
                int scrollbarH = getHeight();
                int maxScroll = totalH - scrollbarH;
                if (maxScroll > 0) {
                    float ratio = (float) scrollbarH / totalH;
                    int thumbH = Math.max(20, (int) (scrollbarH * ratio));
                    double trackH = scrollbarH - thumbH;
                    if (trackH > 0) {
                        double newScroll = dragStartScroll + (my - dragStartY) / trackH * maxScroll;
                        scrollAmount = Mth.clamp(newScroll, 0, maxScroll);
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            draggingScroll = false;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            if (isMouseOver(mx, my)) {
                int totalH = filteredRoles.size() * ITEM_H;
                int maxScroll = Math.max(0, totalH - getHeight());
                scrollAmount = Mth.clamp(scrollAmount - sy * ITEM_H / 2, 0, maxScroll);
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput o) {
        }
    }

    // ========== 内部类：角色详情 ==========
    private static class RoleDetailsComponent extends AbstractWidget {
        private SRERole role;
        private SREPlayerStatsComponent.RoleStats roleStats;
        private int scrollY = 0, maxScroll = 0;
        private boolean draggingScroll = false;
        private double dragStartY, dragStartScroll;
        private static final int SCROLLBAR_W = 6;

        public RoleDetailsComponent(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        public void setRole(SRERole r, SREPlayerStatsComponent.RoleStats rs) {
            role = r;
            roleStats = rs;
            scrollY = 0;
        }

        private Font font;

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            font = Minecraft.getInstance().font;
            drawPanelBg(g, getX(), getY(), getWidth(), getHeight());
            if (role == null || roleStats == null) {
                g.drawCenteredString(font, Component.translatable("screen.starrailexpress.select_hint"),
                        getX() + getWidth() / 2, getY() + getHeight() / 2, 0x888888);
                return;
            }

            int contentX = getX() + 10;
            int contentY = getY() + 10 - scrollY;
            int contentW = getWidth() - 20 - SCROLLBAR_W - 4;
            int lineH = font.lineHeight + 2;

            enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - SCROLLBAR_W - 2, getY() + getHeight() - 1);

            // 标题
            Component name = ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString()).copy()
                    .withStyle(s -> s.withBold(true));
            g.drawString(font, name, contentX, contentY, role.getColor());
            contentY += lineH + 4;

            // 统计行
            drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID + ".player_stats.times_played",
                    roleStats.getTimesPlayed());
            contentY += lineH;
            drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID + ".player_stats.wins_short",
                    roleStats.getWinsAsRole());
            contentY += lineH;
            drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID + ".player_stats.kills",
                    roleStats.getKillsAsRole());
            contentY += lineH;
            drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID + ".player_stats.team_kills",
                    roleStats.getTeamKillsAsRole());
            contentY += lineH;
            drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID + ".player_stats.deaths",
                    roleStats.getDeathsAsRole());
            contentY += lineH;

            double winRate = roleStats.getTimesPlayed() > 0
                    ? (double) roleStats.getWinsAsRole() / roleStats.getTimesPlayed() * 100
                    : 0;
            double kd = roleStats.getDeathsAsRole() > 0
                    ? (double) roleStats.getKillsAsRole() / roleStats.getDeathsAsRole()
                    : roleStats.getKillsAsRole();
            drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID + ".player_stats.win_rate_short",
                    String.format("%.2f%%", winRate));
            contentY += lineH;
            // drawStatLine(g, contentX, contentY, "screen." + SRE.MOD_ID +
            // ".player_stats.kd_ratio", String.format("%.2f", kd));

            disableScissor();

            // 滚动条
            int contentEndY = contentY + lineH; // 最后一行内容的底部
            int totalH = contentEndY - getY() - 10;
            if (totalH > getHeight()) {
                maxScroll = totalH - getHeight();
                scrollY = Mth.clamp(scrollY, 0, maxScroll);
                int sbX = getX() + getWidth() - SCROLLBAR_W - 4;
                int sbY = getY();
                int sbH = getHeight();
                float ratio = (float) sbH / totalH;
                int thumbH = Math.max(20, (int) (sbH * ratio));
                int thumbY = sbY + (int) ((float) scrollY / maxScroll * (sbH - thumbH));
                boolean hl = draggingScroll
                        || (mx >= sbX && mx <= sbX + SCROLLBAR_W && my >= thumbY && my <= thumbY + thumbH);
                g.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, 0xFF111828);
                g.fill(sbX + 1, sbY + 1, sbX + SCROLLBAR_W - 1, sbY + sbH - 1, 0x55334466);
                g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, hl ? 0xFF8899CC : 0xFF556699);
                g.fill(sbX + 1, thumbY + 1, sbX + SCROLLBAR_W - 1, thumbY + thumbH - 1, hl ? 0xFFAABBEE : 0xFF7788BB);
            } else {
                maxScroll = 0;
            }
        }

        private void drawStatLine(GuiGraphics g, int x, int y, String key, Object val) {
            Component label = Component.translatable(key).withStyle(s -> s.withColor(0xFFAAAAAA));
            Component value = Component.literal(String.valueOf(val)).withStyle(s -> s.withColor(0xFFFFDD88));
            g.drawString(font, label, x, y, 0xFFAAAAAA);
            g.drawString(font, value, x + 120, y, 0xFFFFDD88);
        }

        private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
            g.fillGradient(x, y, x + w, y + h, 0xD80C1020, 0xD8101828);
            g.renderOutline(x, y, w, h, 0xFF1E3060);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn == 0 && isMouseOver(mx, my) && maxScroll > 0) {
                int sbX = getX() + getWidth() - SCROLLBAR_W - 4;
                if (mx >= sbX && mx <= sbX + SCROLLBAR_W) {
                    draggingScroll = true;
                    dragStartY = my;
                    dragStartScroll = scrollY;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (draggingScroll && maxScroll > 0) {
                int sbH = getHeight();
                int totalH = (int) (getHeight() + maxScroll);
                float ratio = (float) sbH / totalH;
                int thumbH = Math.max(20, (int) (sbH * ratio));
                double trackH = sbH - thumbH;
                if (trackH > 0) {
                    double newScroll = dragStartScroll + (my - dragStartY) / trackH * maxScroll;
                    scrollY = Mth.clamp((int) newScroll, 0, maxScroll);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            draggingScroll = false;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            if (isMouseOver(mx, my) && maxScroll > 0) {
                scrollY = Mth.clamp(scrollY - (int) (sy * 20), 0, maxScroll);
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput o) {
        }
    }

    // 工具方法
    private static void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = Minecraft.getInstance().getWindow();
        int scale = (int) w.getGuiScale();
        int sy0 = (int) (w.getScreenHeight() - y1 * scale);
        RenderSystem.enableScissor(x0 * scale, sy0, (x1 - x0) * scale, (y1 - y0) * scale);
    }

    private static void disableScissor() {
        RenderSystem.disableScissor();
    }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0)
            return c1;
        if (t >= 1)
            return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}