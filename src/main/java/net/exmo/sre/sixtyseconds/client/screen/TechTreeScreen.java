package net.exmo.sre.sixtyseconds.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsRecipes;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsTechTree;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsTechTree.TechNode;
import net.exmo.sre.sixtyseconds.network.OpenTechTreeS2CPacket;
import net.exmo.sre.sixtyseconds.network.TechUnlockC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * 科技树界面：全屏可拖拽平移 + 滚轮缩放，按层级树状图展示科技节点。
 * 风格参考 {@code docs/ui_style.md}（深色全屏背景 + 金/绿/灰三态卡片 + 科技连线）。
 *
 * <h3>操作</h3>
 * <ul>
 *   <li>左键拖拽背景 → 平移视图</li>
 *   <li>滚轮 → 缩放（以鼠标位置为中心）</li>
 *   <li>点击可解锁节点 → 消耗废料解锁</li>
 *   <li>右键双击 → 重置视图</li>
 * </ul>
 */
public class TechTreeScreen extends Screen {

    // ── 配色 ──────────────────────────────────────────────────────
    private static final int BG_TOP = 0xF018120A;
    private static final int BG_BOTTOM = 0xF0061018;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int HUD_BG = 0xAA1A1008;

    // ── 布局 ──────────────────────────────────────────────────────
    private static final int CARD_W = 130;
    private static final int CARD_H = 62;
    private static final int ROW_GAP = 10;
    private static final int COL_GAP = 210;
    /** 卡片中部解锁物品图标行：最多显示个数 */
    private static final int CARD_MAX_ICONS = 6;

    // ── 缩放限制 ──────────────────────────────────────────────────
    private static final float ZOOM_MIN = 0.35f;
    private static final float ZOOM_MAX = 2.5f;

    // ── 状态 ──────────────────────────────────────────────────────
    private Set<String> unlocked;
    /** 世界坐标空间中的节点矩形：id → {x, y, w, h} */
    private final Map<String, int[]> worldRects = new LinkedHashMap<>();
    /** 整个世界空间布局的边界 */
    private int worldW, worldH;
    /** 当前展示的大类（{@link SixtySecondsTechTree#CATEGORIES}）。 */
    private String activeCategory = SixtySecondsTechTree.CATEGORIES.get(0);
    /** 大类页签屏幕矩形：category → {x, y, w, h}（init 时计算）。 */
    private final Map<String, int[]> tabRects = new LinkedHashMap<>();
    /** 页签栏底边 y（裁剪区顶部）。 */
    private int tabBarBottom = 34;

    // ── 相机 ──────────────────────────────────────────────────────
    private float zoom = 1.0f;
    /** viewX/viewY = 屏幕中心对应的世界坐标 */
    private double viewX, viewY;

    // ── 拖拽 ──────────────────────────────────────────────────────
    private boolean dragging;
    private double dragStartMX, dragStartMY;
    private double dragStartVX, dragStartVY;

    // ── 手势 ──────────────────────────────────────────────────────
    private long lastRightClickTime;
    private static final long DOUBLE_CLICK_MS = 300;

    /** 该科技解锁的一个配方：产物 + 制作它需要的合成台。 */
    private record Unlock(ItemStack stack, SixtySecondsRecipes.Station station) {
    }

    /** 科技 → 该科技解锁的配方（展示用），按配方表顺序。 */
    private final Map<String, List<Unlock>> techUnlocks = new HashMap<>();

    public TechTreeScreen(OpenTechTreeS2CPacket data) {
        super(Component.translatable("message.noellesroles.sixty_seconds.tech_title"));
        this.unlocked = new HashSet<>(Arrays.asList(data.unlockedIds()));
        for (var recipe : SixtySecondsRecipes.all()) {
            techUnlocks.computeIfAbsent(recipe.techId(), k -> new ArrayList<>())
                    .add(new Unlock(SixtySecondsRecipes.outputStack(recipe), recipe.station()));
        }
    }

    public void refresh(OpenTechTreeS2CPacket data) {
        this.unlocked = new HashSet<>(Arrays.asList(data.unlockedIds()));
    }

    // ── 布局计算（世界空间）────────────────────────────────────────

    /** 当前页签下展示的节点（按大类过滤）。 */
    private List<TechNode> visibleNodes() {
        List<TechNode> list = new ArrayList<>();
        for (TechNode n : SixtySecondsTechTree.NODES) {
            if (n.category().equals(activeCategory)) {
                list.add(n);
            }
        }
        return list;
    }

    private void computeWorldLayout() {
        worldRects.clear();
        List<TechNode> nodes = visibleNodes();
        // 按大类内深度分组（跨类门控父节点不计入深度）
        Map<String, Integer> depths = new HashMap<>();
        int maxDepth = 0;
        for (TechNode n : nodes) {
            int d = depth(n);
            depths.put(n.id(), d);
            maxDepth = Math.max(maxDepth, d);
        }
        List<List<TechNode>> tiers = new ArrayList<>();
        for (int t = 0; t <= maxDepth; t++) tiers.add(new ArrayList<>());
        for (TechNode n : nodes) tiers.get(depths.get(n.id())).add(n);

        int cols = tiers.size();
        int maxRows = tiers.stream().mapToInt(List::size).max().orElse(1);

        worldW = cols * COL_GAP + CARD_W + 80;
        worldH = maxRows * (CARD_H + ROW_GAP) + 80;

        for (int t = 0; t < cols; t++) {
            List<TechNode> col = tiers.get(t);
            int n = col.size();
            int totalH = n * CARD_H + (n - 1) * ROW_GAP;
            int startY = 40 + Math.max(0, (worldH - 80 - totalH) / 2);
            int x = 40 + t * COL_GAP;
            for (int i = 0; i < n; i++) {
                int y = startY + i * (CARD_H + ROW_GAP);
                worldRects.put(col.get(i).id(), new int[]{x, y, CARD_W, CARD_H});
            }
        }

        // 打开/切页签时居中
        viewX = worldW / 2.0;
        viewY = worldH / 2.0;
    }

    /** 大类内深度：沿 parent 链上溯，遇到跨大类的门控父节点即停（门控节点在别的页签展示）。 */
    private static int depth(TechNode node) {
        int d = 0;
        String parent = node.parentId();
        while (parent != null) {
            TechNode p = SixtySecondsTechTree.byId(parent);
            if (p == null || !p.category().equals(node.category())) break;
            d++;
            parent = p.parentId();
        }
        return d;
    }

    // ── 坐标变换 ──────────────────────────────────────────────────

    /** 世界坐标 → 屏幕坐标 */
    private int toScreenX(double worldX) {
        return (int) ((worldX - viewX) * zoom + this.width / 2.0);
    }

    private int toScreenY(double worldY) {
        return (int) ((worldY - viewY) * zoom + this.height / 2.0);
    }

    /** 屏幕坐标 → 世界坐标 */
    private double toWorldX(double screenX) {
        return (screenX - this.width / 2.0) / zoom + viewX;
    }

    private double toWorldY(double screenY) {
        return (screenY - this.height / 2.0) / zoom + viewY;
    }

    // ── 初始化 ────────────────────────────────────────────────────

    @Override
    protected void init() {
        computeTabRects();
        computeWorldLayout();
        // 底部信息栏
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width - 60, this.height - 24, 50, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal("⊕"), b -> { zoom = Mth.clamp(zoom * 1.2f, ZOOM_MIN, ZOOM_MAX); })
                .bounds(this.width - 120, this.height - 24, 22, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal("⊖"), b -> { zoom = Mth.clamp(zoom / 1.2f, ZOOM_MIN, ZOOM_MAX); })
                .bounds(this.width - 96, this.height - 24, 22, 18).build());
        addRenderableWidget(Button.builder(
                Component.literal("⌂"), b -> resetView())
                .bounds(this.width - 144, this.height - 24, 22, 18).build());
    }

    private void resetView() {
        zoom = 1.0f;
        viewX = worldW / 2.0;
        viewY = worldH / 2.0;
    }

    // ── 大类页签 ──────────────────────────────────────────────────

    private static final int TAB_H = 14;

    private void computeTabRects() {
        tabRects.clear();
        List<String> cats = SixtySecondsTechTree.CATEGORIES;
        int perRow = (cats.size() + 1) / 2; // 两行
        int y = 34;
        for (int row = 0; row < 2; row++) {
            int from = row * perRow;
            int to = Math.min(cats.size(), from + perRow);
            int count = to - from;
            if (count <= 0) continue;
            int w = this.width / count;
            for (int i = from; i < to; i++) {
                int x = (i - from) * w;
                tabRects.put(cats.get(i), new int[]{x, y + row * (TAB_H + 1), w - 1, TAB_H});
            }
        }
        tabBarBottom = y + 2 * (TAB_H + 1) + 2;
    }

    private void drawTabs(GuiGraphics g) {
        for (Map.Entry<String, int[]> e : tabRects.entrySet()) {
            int[] r = e.getValue();
            boolean active = e.getKey().equals(activeCategory);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], active ? 0xCC4A341C : 0x88221A10);
            if (active) {
                g.renderOutline(r[0], r[1], r[2], r[3], GOLD);
            }
            String label = Component.translatable(
                    "techcat.noellesroles.sixty_seconds." + e.getKey()).getString();
            g.drawCenteredString(this.font, ellipsize(label, r[2] - 4),
                    r[0] + r[2] / 2, r[1] + (r[3] - this.font.lineHeight) / 2 + 1,
                    active ? GOLD : MUTED);
        }
    }

    // ── 背包废料计数 ──────────────────────────────────────────────

    private int countScrap() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack.is(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // ── 节点可用性检查 ────────────────────────────────────────────

    private boolean available(TechNode node, int scrap) {
        if (unlocked.contains(node.id())) return false;
        boolean parentOk = node.parentId() == null || unlocked.contains(node.parentId());
        return parentOk && SixtySecondsTechTree.gateSatisfied(node, unlocked) && scrap >= node.scrapCost();
    }

    // ── 鼠标事件 ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 页签点击
            for (Map.Entry<String, int[]> e : tabRects.entrySet()) {
                int[] r = e.getValue();
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    if (!e.getKey().equals(activeCategory)) {
                        activeCategory = e.getKey();
                        computeWorldLayout();
                        playClick();
                    }
                    return true;
                }
            }
            // 再检查是否点在节点上
            double wx = toWorldX(mouseX);
            double wy = toWorldY(mouseY);
            int scrap = countScrap();
            for (TechNode node : visibleNodes()) {
                int[] r = worldRects.get(node.id());
                if (r != null && wx >= r[0] && wx < r[0] + r[2] && wy >= r[1] && wy < r[1] + r[3]
                        && available(node, scrap)) {
                    ClientPlayNetworking.send(new TechUnlockC2SPacket(node.id()));
                    playClick();
                    return true;
                }
            }
            // 没点到节点 → 开始拖拽
            dragging = true;
            dragStartMX = mouseX;
            dragStartMY = mouseY;
            dragStartVX = viewX;
            dragStartVY = viewY;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            long now = System.currentTimeMillis();
            if (now - lastRightClickTime < DOUBLE_CLICK_MS) {
                resetView();
                lastRightClickTime = 0;
            } else {
                lastRightClickTime = now;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            viewX = dragStartVX - (mouseX - dragStartMX) / zoom;
            viewY = dragStartVY - (mouseY - dragStartMY) / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 以鼠标位置为中心缩放
        double worldBeforeX = toWorldX(mouseX);
        double worldBeforeY = toWorldY(mouseY);

        float oldZoom = zoom;
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.12f : 0.893f), ZOOM_MIN, ZOOM_MAX);

        // 调整 view 使鼠标下的世界坐标不变
        viewX = worldBeforeX - (mouseX - this.width / 2.0) / zoom;
        viewY = worldBeforeY - (mouseY - this.height / 2.0) / zoom;

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            resetView();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoom = Mth.clamp(zoom * 1.15f, ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoom = Mth.clamp(zoom / 1.15f, ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ── 渲染 ──────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // 全屏深色背景
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        double worldMX = toWorldX(mouseX);
        double worldMY = toWorldY(mouseY);

        // scissor: 裁剪到页签栏以下、底部按钮以上
        int clipTop = tabBarBottom;
        int clipBottom = this.height - 30;
        RenderSystem.enableScissor(
                (int) (0 * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) (Minecraft.getInstance().getWindow().getHeight() - clipBottom * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) (this.width * Minecraft.getInstance().getWindow().getGuiScale()),
                (int) ((clipBottom - clipTop) * Minecraft.getInstance().getWindow().getGuiScale()));

        int scrap = countScrap();
        drawConnections(g);
        drawNodes(g, scrap, worldMX, worldMY);
        drawNodeTooltip(g, scrap, worldMX, worldMY);

        RenderSystem.disableScissor();

        drawHUD(g, scrap);
        drawTabs(g);
    }

    // ── 连线 ──────────────────────────────────────────────────────

    private void drawConnections(GuiGraphics g) {
        for (TechNode node : visibleNodes()) {
            if (node.parentId() == null) continue;
            int[] childR = worldRects.get(node.id());
            int[] parentR = worldRects.get(node.parentId());
            if (childR == null || parentR == null) continue;

            boolean lit = unlocked.contains(node.parentId());
            int color = lit ? 0xFF6E5A32 : 0xFF3A2E1C;
            int thickness = Math.max(1, (int) (2 * zoom));

            int x1 = toScreenX(parentR[0] + parentR[2]);
            int y1 = toScreenY(parentR[1] + parentR[3] / 2.0);
            int x2 = toScreenX(childR[0]);
            int y2 = toScreenY(childR[1] + childR[3] / 2.0);

            int midX = (x1 + x2) / 2;
            g.fill(x1, y1, midX + thickness, y1 + thickness, color);
            g.fill(midX, Math.min(y1, y2), midX + thickness, Math.max(y1, y2) + thickness, color);
            g.fill(midX, y2, x2 + thickness, y2 + thickness, color);
        }
    }

    // ── 节点卡片 ──────────────────────────────────────────────────

    private void drawNodes(GuiGraphics g, int scrap, double worldMX, double worldMY) {
        TechNode hovered = null;
        for (TechNode node : visibleNodes()) {
            int[] wr = worldRects.get(node.id());
            if (wr == null) continue;

            boolean hover = worldMX >= wr[0] && worldMX < wr[0] + wr[2]
                    && worldMY >= wr[1] && worldMY < wr[1] + wr[3];
            if (hover) hovered = node;

            drawCard(g, node, wr, scrap, hover);
        }
    }

    private void drawCard(GuiGraphics g, TechNode node, int[] wr, int scrap, boolean hover) {
        int sx = toScreenX(wr[0]);
        int sy = toScreenY(wr[1]);
        int sw = (int) (wr[2] * zoom);
        int sh = (int) (wr[3] * zoom);

        boolean isUnlocked = unlocked.contains(node.id());
        boolean parentOk = (node.parentId() == null || unlocked.contains(node.parentId()))
                && SixtySecondsTechTree.gateSatisfied(node, unlocked);
        boolean canBuy = available(node, scrap);

        // 卡片背景
        int bg;
        int edge;
        if (isUnlocked) {
            bg = hover ? 0x88305A38 : 0x66204A2A;
            edge = GREEN;
        } else if (!parentOk) {
            bg = 0x55140E08;
            edge = 0xFF4A3A26;
        } else if (canBuy) {
            bg = hover ? 0x884A341C : 0x66231A10;
            edge = GOLD;
        } else {
            bg = hover ? 0x8833241A : 0x66231A10;
            edge = 0xFF5A4530;
        }

        g.fill(sx, sy, sx + sw, sy + sh, bg);
        g.renderOutline(sx, sy, sw, sh, edge);

        // 名称
        String name = Component.translatable("tech.noellesroles.sixty_seconds." + node.id()).getString();
        int nameColor = isUnlocked ? GREEN : (parentOk ? TEXT : MUTED);
        if (zoom < 1.5f) {
            // 缩放文字大小：使用 pose stack
            var pose = g.pose();
            pose.pushPose();
            float s = Math.max(0.7f, zoom);
            pose.translate(sx + 4, sy + 4, 0);
            pose.scale(s, s, 1);
            g.drawString(this.font, ellipsize(name, (int) ((wr[2] - 8) / s)), 0, 0, nameColor);
            pose.popPose();
        } else {
            g.drawString(this.font, ellipsize(name, sw - 8), sx + 4, sy + 4, nameColor);
        }

        // 中部：该科技解锁的配方产物图标行（缩得太小就不画，避免糊成一团）
        List<Unlock> outputs = techUnlocks.get(node.id());
        if (outputs != null && zoom > 0.45f) {
            int shown = Math.min(outputs.size(), CARD_MAX_ICONS);
            var pose = g.pose();
            for (int i = 0; i < shown; i++) {
                pose.pushPose();
                pose.translate(toScreenX(wr[0] + 4 + i * 19), toScreenY(wr[1] + 18), 0);
                pose.scale(zoom, zoom, 1);
                g.renderFakeItem(outputs.get(i).stack(), 0, 0);
                pose.popPose();
            }
            if (outputs.size() > shown) {
                pose.pushPose();
                pose.translate(toScreenX(wr[0] + 4 + shown * 19), toScreenY(wr[1] + 22), 0);
                pose.scale(zoom, zoom, 1);
                g.drawString(this.font, "+" + (outputs.size() - shown), 0, 0, MUTED);
                pose.popPose();
            }
        }

        // 底部状态
        Component status;
        int statusColor;
        if (isUnlocked) {
            status = Component.translatable("message.noellesroles.sixty_seconds.tech_unlocked_btn");
            statusColor = GREEN;
        } else if (!parentOk) {
            status = Component.translatable("message.noellesroles.sixty_seconds.tech_locked_short");
            statusColor = RED;
        } else {
            status = Component.translatable("message.noellesroles.sixty_seconds.tech_cost", node.scrapCost());
            statusColor = canBuy ? GOLD : RED;
        }

        if (zoom > 0.6f) {
            g.drawString(this.font, status, sx + 4, sy + sh - this.font.lineHeight - 2, statusColor);
        }
    }

    // ── Tooltip ───────────────────────────────────────────────────

    private void drawNodeTooltip(GuiGraphics g, int scrap, double worldMX, double worldMY) {
        TechNode hovered = null;
        for (TechNode node : visibleNodes()) {
            int[] wr = worldRects.get(node.id());
            if (wr != null && worldMX >= wr[0] && worldMX < wr[0] + wr[2]
                    && worldMY >= wr[1] && worldMY < wr[1] + wr[3]) {
                hovered = node;
                break;
            }
        }

        if (hovered == null) return;

        int sx = toScreenX(worldRects.get(hovered.id())[0]);
        int sy = toScreenY(worldRects.get(hovered.id())[1]);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tech.noellesroles.sixty_seconds." + hovered.id())
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tech.noellesroles.sixty_seconds." + hovered.id() + ".desc")
                .withStyle(ChatFormatting.GRAY));
        if (hovered.parentId() != null && !unlocked.contains(hovered.parentId())) {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.tech_requires",
                    Component.translatable("tech.noellesroles.sixty_seconds." + hovered.parentId()))
                    .withStyle(ChatFormatting.RED));
        }
        // 额外前置（「前置：A 和 B」里的 B，parentId 之外）：未解锁的逐条列出
        for (String extra : SixtySecondsTechTree.EXTRA_REQUIREMENTS
                .getOrDefault(hovered.id(), List.of())) {
            if (!unlocked.contains(extra)) {
                lines.add(Component.translatable("message.noellesroles.sixty_seconds.tech_requires",
                        Component.translatable("tech.noellesroles.sixty_seconds." + extra))
                        .withStyle(ChatFormatting.RED));
            }
        }
        // 特殊门控提示（综合补剂=医疗全解锁 / 神秘技术=全树75%）
        if (!SixtySecondsTechTree.gateSatisfied(hovered, unlocked)
                && ("omni_tonic".equals(hovered.id()) || "sacrifice_1".equals(hovered.id()))) {
            String gateKey = "omni_tonic".equals(hovered.id())
                    ? "message.noellesroles.sixty_seconds.tech_gate_medical"
                    : "message.noellesroles.sixty_seconds.tech_gate_mystic";
            lines.add(Component.translatable(gateKey).withStyle(ChatFormatting.RED));
        }
        if (!unlocked.contains(hovered.id())) {
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.tech_cost", hovered.scrapCost())
                    .withStyle(ChatFormatting.YELLOW));
        }
        // 解锁配方清单（产物名 ×数量 @在哪个合成台做）
        List<Unlock> outputs = techUnlocks.get(hovered.id());
        if (outputs != null && !outputs.isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("message.noellesroles.sixty_seconds.tech_unlocks")
                    .withStyle(ChatFormatting.GOLD));
            int shown = Math.min(outputs.size(), 10);
            for (int i = 0; i < shown; i++) {
                ItemStack stack = outputs.get(i).stack();
                MutableComponent line = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
                if (stack.getCount() > 1) {
                    line.append(Component.literal(" ×" + stack.getCount()).withStyle(ChatFormatting.GRAY));
                }
                line.append(Component.translatable("message.noellesroles.sixty_seconds.tech_unlock_station",
                        Component.translatable(outputs.get(i).station().translationKey()))
                        .withStyle(ChatFormatting.AQUA));
                lines.add(line);
            }
            if (outputs.size() > shown) {
                lines.add(Component.translatable("message.noellesroles.sixty_seconds.tech_unlocks_more",
                        outputs.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        g.renderComponentTooltip(this.font, lines, sx + 10, sy + 10);
    }

    // ── HUD 信息栏 ────────────────────────────────────────────────

    private void drawHUD(GuiGraphics g, int scrap) {
        // 顶部标题栏
        g.fill(0, 0, this.width, 32, HUD_BG);
        g.drawCenteredString(this.font,
                this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, 8, GOLD);

        // 底部状态栏
        g.fill(0, this.height - 30, this.width, this.height, HUD_BG);
        MutableComponent info = Component.empty()
                .append(Component.translatable("message.noellesroles.sixty_seconds.tech_scrap_count", scrap)
                        .withStyle(scrap > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .append(Component.literal("  |  "))
                .append(Component.literal("zoom: " + (int) (zoom * 100) + "%").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  |  "))
                .append(Component.literal("拖拽移动 | 滚轮缩放 | R重置").withStyle(ChatFormatting.DARK_GRAY));
        g.drawCenteredString(this.font, info, this.width / 2, this.height - 20, TEXT);
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    private String ellipsize(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        while (s.length() > 1 && this.font.width(s + "...") > maxW)
            s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
