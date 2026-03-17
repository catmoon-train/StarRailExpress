// RoleStatsPanel.java
package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.api.SRERole;
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
        if (r instanceof GuiEventListener gel)
            children.add(gel);
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

    /** Bug fix 1: 去掉对 searchBox 的单独调用，统一通过 children 循环分发，避免事件双发 */
    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (!visible)
            return false;
        for (GuiEventListener c : children)
            if (c.keyPressed(k, s, m))
                return true;
        return false;
    }

    @Override
    public boolean charTyped(char c, int m) {
        if (!visible)
            return false;
        for (GuiEventListener child : children)
            if (child.charTyped(c, m))
                return true;
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (!visible || !isMouseOver(mx, my))
            return false;
        for (GuiEventListener c : children)
            if (c.mouseClicked(mx, my, b))
                return true;
        return false;
    }

    /** Bug fix 1 (续): mouseReleased 同样去掉对 searchBox 的单独调用 */
    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (!visible)
            return false;
        for (GuiEventListener c : children)
            if (c.mouseReleased(mx, my, b))
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
        if (!visible)
            return false;
        for (GuiEventListener c : children)
            if (c.mouseDragged(mx, my, b, dx, dy))
                return true;
        return false;
    }

    /** Bug fix 5: 补充 isMouseOver，防止面板外区域触发事件 */
    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && mx >= x && mx <= x + width && my >= y && my <= y + height;
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
        private static final int ITEM_H = 40;
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
            selectedRole = filteredRoles.isEmpty() ? null : filteredRoles.get(0);
        }

        public void filterRoles(String search) {
            if (search == null || search.isEmpty()) {
                filteredRoles = new ArrayList<>(allRoles);
            } else {
                String lower = search.toLowerCase();
                filteredRoles = allRoles.stream()
                        .filter(r -> ReplayDisplayUtils.getRoleDisplayName(r.identifier().toString())
                                .getString().toLowerCase().contains(lower))
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
            /** Bug fix 4: 使用 GuiGraphics 自带的 enableScissor/disableScissor，无需手动处理 scale */
            g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());

            int startIdx = (int) (scrollAmount / ITEM_H);
            int endIdx = Math.min(startIdx + (getHeight() / ITEM_H) + 2, filteredRoles.size());

            for (int i = startIdx; i < endIdx; i++) {
                SRERole role = filteredRoles.get(i);
                SREPlayerStatsComponent.RoleStats rs = roleStatsMap.get(role.identifier());
                int itemY = getY() + i * ITEM_H - (int) scrollAmount;
                boolean selected = role.equals(selectedRole);
                boolean hovered = !selected
                        && mx >= getX() && mx <= getX() + getWidth() - SCROLLBAR_W - 2
                        && my >= itemY && my <= itemY + ITEM_H;
                drawRoleCard(g, role, rs, getX(), itemY, getWidth() - SCROLLBAR_W - 4, ITEM_H, hovered, selected);
            }

            g.disableScissor();

            // 滚动条
            int totalH = filteredRoles.size() * ITEM_H;
            if (totalH > getHeight()) {
                int maxScroll = totalH - getHeight();
                int scrollbarX = getX() + getWidth() - SCROLLBAR_W;
                int scrollbarY = getY();
                int scrollbarH = getHeight();
                float ratio = (float) scrollbarH / totalH;
                int thumbH = Math.max(20, (int) (scrollbarH * ratio));
                int thumbY = scrollbarY + (int) ((scrollAmount / maxScroll) * (scrollbarH - thumbH));
                boolean hl = draggingScroll
                        || (mx >= scrollbarX && mx <= scrollbarX + SCROLLBAR_W
                                && my >= thumbY && my <= thumbY + thumbH);
                g.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_W, scrollbarY + scrollbarH, 0xFF111828);
                g.fill(scrollbarX + 1, scrollbarY + 1, scrollbarX + SCROLLBAR_W - 1, scrollbarY + scrollbarH - 1, 0x55334466);
                g.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_W, thumbY + thumbH, hl ? 0xFF8899CC : 0xFF556699);
                g.fill(scrollbarX + 1, thumbY + 1, scrollbarX + SCROLLBAR_W - 1, thumbY + thumbH - 1, hl ? 0xFFAABBEE : 0xFF7788BB);
            }
        }

        private void drawRoleCard(GuiGraphics g, SRERole role, SREPlayerStatsComponent.RoleStats rs,
                int x, int y, int w, int h, boolean hover, boolean selected) {
            int color = role.getColor() | 0xFF000000;
            int bg = selected ? 0xFF2A3A5A
                    : hover ? blendColors(0xFF1A1F2E, color, 0.3f) : 0xFF1A1F2E;
            g.fill(x, y, x + w, y + h, bg);
            g.renderOutline(x, y, w, h,
                    selected ? color : (hover ? blendColors(color, 0xFFFFFFFF, 0.3f) : 0xFF2A2F3F));

            ResourceLocation icon = getTypeIcon(role);
            if (icon != null) {
                RenderSystem.enableBlend();
                g.blit(icon, x + 5, y + 5, 0, 0, 30, 30, 30, 30);
                RenderSystem.disableBlend();
            } else {
                g.fill(x + 5, y + 5, x + 35, y + 35, 0xFF333333);
            }

            Component name = ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString());
            g.drawString(Minecraft.getInstance().font, name, x + 40, y + 6, role.getColor());
            Component type = getRoleTypeDisplay(role);
            g.drawString(Minecraft.getInstance().font, type, x + 40, y + 18, 0xFFAAAAAA);
            if (rs != null) {
                String brief = "场次: " + rs.getTimesPlayed() + "  胜场: " + rs.getWinsAsRole();
                g.drawString(Minecraft.getInstance().font, brief, x + 40, y + 28, 0xFFCCCCCC);
            }
        }

        private Component getRoleTypeDisplay(SRERole r) {
            return switch (PlayerRoleWeightManager.getRoleType(r)) {
                case 0, 1 -> Component.translatable("display.type.role.innocent").withStyle(s -> s.withColor(0xFF44BB66));
                case 2    -> Component.translatable("display.type.role.neutral").withStyle(s -> s.withColor(0xFFCCAA22));
                case 3    -> Component.translatable("display.type.role.neutral_for_killer").withStyle(s -> s.withColor(0xFFAA44CC));
                case 4    -> Component.translatable("display.type.role.killer").withStyle(s -> s.withColor(0xFFCC2233));
                case 5    -> Component.translatable("display.type.role.vigilante").withStyle(s -> s.withColor(0xFF22BBCC));
                default   -> Component.literal("Unknown");
            };
        }

        private ResourceLocation getTypeIcon(SRERole role) {
            return switch (PlayerRoleWeightManager.getRoleType(role)) {
                case 0, 1 -> ResourceLocation.tryParse("wathe:textures/gui/sprites/hud/mood_happy.png");
                case 2    -> ResourceLocation.tryParse("noellesroles:textures/gui/sprites/hud/mood_neu.png");
                case 3    -> ResourceLocation.tryParse("noellesroles:textures/gui/sprites/hud/mood_jester.png");
                case 4    -> ResourceLocation.tryParse("wathe:textures/gui/sprites/hud/mood_killer.png");
                case 5    -> ResourceLocation.tryParse("noellesroles:textures/gui/sprites/hud/mood_vig.png");
                default   -> null;
            };
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (!isMouseOver(mx, my))
                return false;
            int scrollbarX = getX() + getWidth() - SCROLLBAR_W;
            if (mx >= scrollbarX && mx <= scrollbarX + SCROLLBAR_W
                    && my >= getY() && my <= getY() + getHeight()) {
                draggingScroll = true;
                dragStartY = my;
                dragStartScroll = scrollAmount;
                return true;
            }
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
            if (!draggingScroll)
                return false;
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

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            draggingScroll = false;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            if (!isMouseOver(mx, my))
                return false;
            int totalH = filteredRoles.size() * ITEM_H;
            int maxScroll = Math.max(0, totalH - getHeight());
            scrollAmount = Mth.clamp(scrollAmount - sy * ITEM_H / 2.0, 0, maxScroll);
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput o) {
        }
    }

    // ========== 内部类：角色详情 ==========
    private static class RoleDetailsComponent extends AbstractWidget {
        private SRERole role;
        private SREPlayerStatsComponent.RoleStats roleStats;
        private int scrollY = 0;
        private int maxScroll = 0;
        private boolean draggingScroll = false;
        private double dragStartY;
        private double dragStartScroll;
        private static final int SCROLLBAR_W = 6;

        public RoleDetailsComponent(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        public void setRole(SRERole r, SREPlayerStatsComponent.RoleStats rs) {
            role = r;
            roleStats = rs;
            scrollY = 0;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            /** Bug fix 3: font 改为局部变量，不再污染实例状态 */
            Font font = Minecraft.getInstance().font;
            int lineH = font.lineHeight + 2;

            drawPanelBg(g, getX(), getY(), getWidth(), getHeight());

            if (role == null || roleStats == null) {
                g.drawCenteredString(font,
                        Component.translatable("screen.starrailexpress.select_hint"),
                        getX() + getWidth() / 2, getY() + getHeight() / 2, 0x888888);
                return;
            }

            /** Bug fix 4: 使用 GuiGraphics 自带 scissor */
            g.enableScissor(getX() + 1, getY() + 1,
                    getX() + getWidth() - SCROLLBAR_W - 2, getY() + getHeight() - 1);

            int contentX = getX() + 10;
            /**
             * Bug fix 2: contentY 中不再减去 scrollY。
             * 把 scrollY 的作用单独体现在每一行绘制时，
             * 这样 contentY 的最终值才能用于准确计算 totalH。
             */
            int contentY = getY() + 10;

            Component name = ReplayDisplayUtils.getRoleDisplayName(role.identifier().toString()).copy()
                    .withStyle(s -> s.withBold(true));
            g.drawString(font, name, contentX, contentY - scrollY, role.getColor());
            contentY += lineH + 4;

            drawStatLine(g, font, contentX, contentY - scrollY,
                    "screen." + SRE.MOD_ID + ".player_stats.times_played", roleStats.getTimesPlayed());
            contentY += lineH;
            drawStatLine(g, font, contentX, contentY - scrollY,
                    "screen." + SRE.MOD_ID + ".player_stats.wins_short", roleStats.getWinsAsRole());
            contentY += lineH;
            drawStatLine(g, font, contentX, contentY - scrollY,
                    "screen." + SRE.MOD_ID + ".player_stats.kills", roleStats.getKillsAsRole());
            contentY += lineH;
            drawStatLine(g, font, contentX, contentY - scrollY,
                    "screen." + SRE.MOD_ID + ".player_stats.team_kills", roleStats.getTeamKillsAsRole());
            contentY += lineH;
            drawStatLine(g, font, contentX, contentY - scrollY,
                    "screen." + SRE.MOD_ID + ".player_stats.deaths", roleStats.getDeathsAsRole());
            contentY += lineH;

            double winRate = roleStats.getTimesPlayed() > 0
                    ? (double) roleStats.getWinsAsRole() / roleStats.getTimesPlayed() * 100 : 0;
            drawStatLine(g, font, contentX, contentY - scrollY,
                    "screen." + SRE.MOD_ID + ".player_stats.win_rate_short",
                    String.format("%.2f%%", winRate));
            contentY += lineH;

            g.disableScissor();

            /**
             * Bug fix 2 (续): totalH 现在基于未偏移的 contentY，计算结果正确。
             * Bug fix 6: 去掉原来多余的 + lineH（contentY 已指向下一行起始位置，本身就是内容底端）
             */
            int totalH = contentY - getY() - 10;
            if (totalH > getHeight()) {
                maxScroll = totalH - getHeight();
                scrollY = Mth.clamp(scrollY, 0, maxScroll);
                drawScrollbar(g, mx, my);
            } else {
                maxScroll = 0;
                scrollY = 0;
            }
        }

        private void drawScrollbar(GuiGraphics g, int mx, int my) {
            int sbX = getX() + getWidth() - SCROLLBAR_W - 4;
            int sbY = getY();
            int sbH = getHeight();
            int totalH = getHeight() + maxScroll;
            float ratio = (float) sbH / totalH;
            int thumbH = Math.max(20, (int) (sbH * ratio));
            int thumbY = sbY + (int) ((float) scrollY / maxScroll * (sbH - thumbH));
            boolean hl = draggingScroll
                    || (mx >= sbX && mx <= sbX + SCROLLBAR_W && my >= thumbY && my <= thumbY + thumbH);
            g.fill(sbX, sbY, sbX + SCROLLBAR_W, sbY + sbH, 0xFF111828);
            g.fill(sbX + 1, sbY + 1, sbX + SCROLLBAR_W - 1, sbY + sbH - 1, 0x55334466);
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, hl ? 0xFF8899CC : 0xFF556699);
            g.fill(sbX + 1, thumbY + 1, sbX + SCROLLBAR_W - 1, thumbY + thumbH - 1, hl ? 0xFFAABBEE : 0xFF7788BB);
        }

        private void drawStatLine(GuiGraphics g, Font font, int x, int y, String key, Object val) {
            g.drawString(font, Component.translatable(key), x, y, 0xFFAAAAAA);
            g.drawString(font, Component.literal(String.valueOf(val)), x + 120, y, 0xFFFFDD88);
        }

        private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
            g.fillGradient(x, y, x + w, y + h, 0xD80C1020, 0xD8101828);
            g.renderOutline(x, y, w, h, 0xFF1E3060);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn != 0 || !isMouseOver(mx, my) || maxScroll <= 0)
                return false;
            int sbX = getX() + getWidth() - SCROLLBAR_W - 4;
            if (mx >= sbX && mx <= sbX + SCROLLBAR_W) {
                draggingScroll = true;
                dragStartY = my;
                dragStartScroll = scrollY;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (!draggingScroll || maxScroll <= 0)
                return false;
            int sbH = getHeight();
            int totalH = getHeight() + maxScroll;
            float ratio = (float) sbH / totalH;
            int thumbH = Math.max(20, (int) (sbH * ratio));
            double trackH = sbH - thumbH;
            if (trackH > 0) {
                double newScroll = dragStartScroll + (my - dragStartY) / trackH * maxScroll;
                scrollY = Mth.clamp((int) newScroll, 0, maxScroll);
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            draggingScroll = false;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            if (!isMouseOver(mx, my) || maxScroll <= 0)
                return false;
            scrollY = Mth.clamp(scrollY - (int) (sy * 20), 0, maxScroll);
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput o) {
        }
    }

    // ========== 工具方法 ==========
    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0) return c1;
        if (t >= 1) return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int gr = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}