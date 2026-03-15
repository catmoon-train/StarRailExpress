package net.exmo.sre.loading;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import com.terraformersmc.modmenu.api.ModMenuApi;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
<<<<<<< HEAD
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
=======
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class StarRailExpressTitleScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("narrator.screen.title");

    private static final String QQ_GROUP_URL = "https://qm.qq.com/q/8XXqKRjT7q";
<<<<<<< HEAD
    private static final String DISCORD_URL  = "https://discord.gg/T7R5NkMHt3";
    private static final String FEEDBACK_URL = "https://github.com/catmoon-train/StarRailExpress/issues";
=======
    private static final String DISCORD_URL = "https://discord.gg/T7R5NkMHt3";
    private static final String WIKI_URL = "https://docs.qq.com/smartsheet/DUUNVaFJTdmFTQ0Ry?tab=sc_bw1NDg";

    // 基础尺寸常量（用于缩放）
    private static final int BASE_MENU_START_X = 70;
    private static final int BASE_MENU_START_Y_OFFSET = 110;
    private static final int BASE_MENU_SPACING = 26;
    private static final int BASE_LEFT_PANEL_X = 38;
    private static final int BASE_LEFT_PANEL_Y = 78;
    private static final int BASE_LEFT_PANEL_W = 230;
    private static final int BASE_LEFT_PANEL_H = 270; // 固定部分高度
    private static final int BASE_RIGHT_PANEL_W = 450;
    private static final int BASE_RIGHT_PANEL_HEADER_H = 24;
    private static final int BASE_RIGHT_PANEL_BODY_H = 270;
    private static final int BASE_RIGHT_PANEL_X_OFFSET = 36; // 右侧面板距右边距离
    private static final int BASE_RIGHT_PANEL_Y = 86;
    private static final int BASE_SCROLLBAR_WIDTH = 6;
    private static final int BASE_ANIM_OFFSET = 22; // 菜单动画偏移
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

    /** 菜单项纵向间距 */
    private static final int MENU_SPACING    = 26;
    /** 左面板内：标题+副标题区高度（不可滚动） */
    private static final int MENU_HEADER_H   = 50;
    /** 左面板内：底部内边距 */
    private static final int MENU_BOTTOM_PAD = 10;
    /** 屏幕底部为版本/版权文字保留的空间 */
    private static final int BOTTOM_RESERVE  = 34;
    /** 左侧菜单滚动条宽度 */
    private static final int MENU_SB_W       = 4;

    // ── Splash / Realms ──────────────────────────────────────────────
    @Nullable private SplashRenderer splash;
    @Nullable private RealmsNotificationsScreen realmsNotificationsScreen;
    private final LogoRenderer logoRenderer;

    // ── 动画 & 状态 ──────────────────────────────────────────────────
    private long    screenOpenTime;
    private float   panoramaFade       = 1.0F;
    private boolean waitingForContinue = true;
    private float   continueAlpha      = 0.0F;
    private float   menuAnimProgress   = 0.0F;

    // ── 更新日志面板 ─────────────────────────────────────────────────
    private boolean changelogExpanded     = true;
    private float   changelogExpandAnim   = 1.0F;
    private float   changelogScrollOffset = 0.0F;
    private float   changelogMaxScroll    = 0.0F;
    private boolean changelogDragging     = false;
    private List<ChangelogEntry> parsedChangelogLines;

    // ── 左侧菜单滚动 ─────────────────────────────────────────────────
    private float   menuScrollOffset = 0.0F;
    private float   menuMaxScroll    = 0.0F;
    private boolean menuDragging     = false;

    // ── 响应式布局（每次 init / resize 在 computeLayout() 中重算） ──
    private int  lPanelX;           // 左面板左上角 X
    private int  lPanelY;           // 左面板左上角 Y
    private int  lPanelW;           // 左面板宽度
    private int  lPanelH;           // 左面板高度（动态，永不溢出屏幕）
    private int  menuViewportTop;   // 菜单可见视口上边界（绝对坐标）
    private int  menuViewportBottom;// 菜单可见视口下边界（绝对坐标）
    private int  menuBaseX;         // 菜单项基准 X（不含滚动）
    private int  menuBaseY;         // 菜单项 index=0 时基准 Y（不含滚动）

    private int     cPanelX;        // 日志面板左上角 X
    private int     cPanelW;        // 日志面板宽度
    private int     cTextMaxW;      // 日志文字区最大宽度
    private boolean showChangelog;  // 屏幕够宽才显示

    // ── 菜单项列表 ───────────────────────────────────────────────────
    private final List<MenuEntry> menuEntries = new ArrayList<>();

<<<<<<< HEAD
    // ─────────────────────────────────────────────────────────────────
    //  构造函数
    // ─────────────────────────────────────────────────────────────────
=======
    // 缩放相关
    private float globalScale = 1.0F;
    private int scaledMenuStartX;
    private int scaledMenuStartYOffset;
    private int scaledMenuSpacing;
    private int scaledLeftPanelX;
    private int scaledLeftPanelY;
    private int scaledLeftPanelW;
    private int scaledLeftPanelH;
    private int scaledRightPanelW;
    private int scaledRightPanelHeaderH;
    private int scaledRightPanelBodyH;
    private int scaledRightPanelXOffset;
    private int scaledRightPanelY;
    private int scaledScrollbarWidth;
    private int scaledAnimOffset;

    public StarRailExpressTitleScreen() {
        this(false);
    }
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

    public StarRailExpressTitleScreen() { this(false); }

    public StarRailExpressTitleScreen(boolean fading) { this(fading, null); }

    public StarRailExpressTitleScreen(boolean fading, @Nullable LogoRenderer logoRenderer) {
        super(TITLE);
        this.logoRenderer = Objects.requireNonNullElseGet(logoRenderer, () -> new LogoRenderer(false));
    }

<<<<<<< HEAD
    // ─────────────────────────────────────────────────────────────────
    //  布局计算
    // ─────────────────────────────────────────────────────────────────

    /**
     * 每次 init()（含窗口缩放回调）重新计算布局参数。
     * 两个面板的高度均由 this.height 动态决定，不会溢出屏幕底部。
     */
    private void computeLayout() {
        // 左面板宽：屏幕宽 × 30%，限 [140, 230]
        lPanelW = Mth.clamp((int)(this.width  * 0.30f), 140, 230);
        // 左边距：屏幕宽 × 4%，限 [8, 38]
        lPanelX = Mth.clamp((int)(this.width  * 0.04f),   8,  38);
        // 顶部偏移：屏幕高 × 20%，限 [40, 78]
        lPanelY = Mth.clamp((int)(this.height * 0.20f),  40,  78);
        // 【核心】面板高度 = 屏幕底部 - 顶部偏移 - 底部预留
        lPanelH = this.height - lPanelY - BOTTOM_RESERVE;

        // 菜单视口（面板内，跳过标题区和底部内边距）
        menuViewportTop    = lPanelY + MENU_HEADER_H;
        menuViewportBottom = lPanelY + lPanelH - MENU_BOTTOM_PAD;

        // 菜单项基准坐标（纯位置，渲染时再叠加滚动偏移）
        menuBaseX = lPanelX + 32;
        menuBaseY = menuViewportTop + 4; // 视口顶部留 4px 内边距

        // 日志面板宽：屏幕宽 × 32%，限 [160, 250]
        cPanelW = Mth.clamp((int)(this.width * 0.32f), 160, 250);
        int rightMargin = Mth.clamp((int)(this.width * 0.04f), 8, 36);
        cPanelX = this.width - cPanelW - rightMargin;

        // 两侧面板至少留 16px 间距才显示日志面板
        showChangelog = (cPanelX > lPanelX + lPanelW + 16);

        // 日志文字最大宽度（留出滚动条 + 左右内边距）
        cTextMaxW = cPanelW - 30;
    }

    // ─────────────────────────────────────────────────────────────────
    //  资源加载
    // ─────────────────────────────────────────────────────────────────

    /**
     * 从资源包读取 assets/starrailexpress/texts/update_logs.md，
     * 返回按行分割的字符串列表（保留空行）。
     * 读取失败时返回内置错误提示行。
     */
    private List<String> loadChangelogLines() {
        ResourceLocation loc = SRE.id("texts/update_logs.md");
        try {
            Optional<Resource> res = this.minecraft.getResourceManager().getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.toList());
                }
            }
            LOGGER.warn("[SRE] update_logs.md not found in any resource pack.");
        } catch (Exception e) {
            LOGGER.error("[SRE] Failed to load update_logs.md", e);
        }
        return List.of("# 更新日志加载失败",
                "请确认 assets/starrailexpress/texts/update_logs.md 已存在于资源包中");
    }

    // ─────────────────────────────────────────────────────────────────
    //  Screen 生命周期
    // ─────────────────────────────────────────────────────────────────

=======
    private int scale(int value) {
        return (int) (value * globalScale);
    }

>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
    @Override
    protected void init() {

        // 计算缩放系数（以高度720为基准，只缩小不放大）
        this.globalScale = Math.min(this.height / 720.0f, 1.0f);

        // 计算缩放后的布局参数
        this.scaledMenuStartX = scale(BASE_MENU_START_X);
        this.scaledMenuStartYOffset = scale(BASE_MENU_START_Y_OFFSET);
        // 菜单项间距：至少18px（字体高度14+最小间距4），避免文字重叠
        this.scaledMenuSpacing = Math.max(18, scale(BASE_MENU_SPACING));
        this.scaledLeftPanelX = scale(BASE_LEFT_PANEL_X);
        this.scaledLeftPanelY = scale(BASE_LEFT_PANEL_Y);
        this.scaledLeftPanelW = (BASE_LEFT_PANEL_W);
        this.scaledRightPanelW = scale(BASE_RIGHT_PANEL_W);
        this.scaledRightPanelHeaderH = scale(BASE_RIGHT_PANEL_HEADER_H);
        this.scaledRightPanelBodyH = scale(BASE_RIGHT_PANEL_BODY_H);
        this.scaledRightPanelXOffset = scale(BASE_RIGHT_PANEL_X_OFFSET);
        this.scaledRightPanelY = scale(BASE_RIGHT_PANEL_Y);
        this.scaledScrollbarWidth = scale(BASE_SCROLLBAR_WIDTH);
        this.scaledAnimOffset = scale(BASE_ANIM_OFFSET);

        this.screenOpenTime = Util.getMillis();
        this.menuEntries.clear();
<<<<<<< HEAD
=======

        // 解析更新日志（传入缩放后的面板宽度用于文字换行）
        this.parsedChangelogLines = parseChangelogLines(this.changelogRawLines);
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

        // 1. 先算布局（依赖 this.width / this.height）
        computeLayout();

        // 2. 加载并解析日志（依赖 cTextMaxW 和 this.font）
        this.parsedChangelogLines = parseChangelogLines(loadChangelogLines());

        // 3. Splash / Realms
        if (this.splash == null)
            this.splash = this.minecraft.getSplashManager().getSplash();
        if (this.realmsNotificationsScreen == null)
            this.realmsNotificationsScreen = new RealmsNotificationsScreen();
        if (realmsNotificationsEnabled())
            this.realmsNotificationsScreen.init(this.minecraft, this.width, this.height);
<<<<<<< HEAD
=======
        }

        // 文字菜单
        int baseY = this.height / 2 - scaledMenuStartYOffset / 2;
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

        // 4. 注册菜单项
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.multiplayer"),
                () -> {
                    Screen next = this.minecraft.options.skipMultiplayerWarning
                            ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
                    this.minecraft.setScreen(next);
                }));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.singleplayer"),
                () -> this.minecraft.setScreen(new SelectWorldScreen(this))));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.join_qq"),
                () -> Util.getPlatform().openUri(QQ_GROUP_URL)));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.join_discord"),
<<<<<<< HEAD
                () -> Util.getPlatform().openUri(DISCORD_URL)));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.feedback"),
                () -> Util.getPlatform().openUri(FEEDBACK_URL)));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.options"),
                () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
=======
                () -> Util.getPlatform().openUri(DISCORD_URL)
        ));

>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.mod_config"),
                () -> {
                    if (FabricLoader.getInstance().isModLoaded("modmenu"))
                        this.minecraft.setScreen(ModMenuApi.createModsScreen(this));
<<<<<<< HEAD
                }));
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.role_introduction"),
                () -> this.minecraft.setScreen(new RoleIntroduceScreen(this))));
=======
                    }
                }
        ));

>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
        this.menuEntries.add(new MenuEntry(
                Component.translatable("menu.sre.quit"),
                () -> this.minecraft.stop()));

<<<<<<< HEAD
        // 5. 为每个菜单项设置基准坐标（渲染时叠加滚动偏移）
        for (int i = 0; i < this.menuEntries.size(); i++) {
            MenuEntry e = this.menuEntries.get(i);
            e.x     = menuBaseX;
            e.y     = menuBaseY + i * MENU_SPACING;
            e.index = i;
        }

        // 6. 计算菜单最大滚动量
        int totalMenuH        = this.menuEntries.size() * MENU_SPACING;
        int viewportH         = menuViewportBottom - menuViewportTop;
        this.menuMaxScroll    = Math.max(0, totalMenuH - viewportH);
        this.menuScrollOffset = Mth.clamp(this.menuScrollOffset, 0, this.menuMaxScroll);
=======
        // 设置菜单项位置（使用缩放后的坐标）
        for (int i = 0; i < this.menuEntries.size(); i++) {
            MenuEntry entry = this.menuEntries.get(i);
            entry.x = scaledMenuStartX;
            entry.y = baseY + i * scaledMenuSpacing;
            entry.index = i;
        }

        // 计算左侧面板高度（固定部分缩放 + 菜单项高度 + 底部边距）
        int scaledLeftPanelHBase = scale(BASE_LEFT_PANEL_H);
        int scaledBottomMargin = scale(12);
        // 注意：菜单项行高14不缩放，因为字体大小不变
        this.scaledLeftPanelH = scaledLeftPanelHBase + menuEntries.size() * 14 + scaledBottomMargin;
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
    }

    // ─────────────────────────────────────────────────────────────────
    //  Tick
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (realmsNotificationsEnabled()) this.realmsNotificationsScreen.tick();

        long elapsed    = Util.getMillis() - this.screenOpenTime;
        this.continueAlpha = Math.min(elapsed / 800.0F, 1.0F);

        if (!this.waitingForContinue)
            this.menuAnimProgress = Math.min(this.menuAnimProgress + 0.06F, 1.0F);

        float targetExpand = this.changelogExpanded ? 1.0F : 0.0F;
        this.changelogExpandAnim += (targetExpand - this.changelogExpandAnim) * 0.18F;
    }

    @Override public boolean isPauseScreen()    { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    // ─────────────────────────────────────────────────────────────────
    //  渲染入口
    // ─────────────────────────────────────────────────────────────────

    @Override
<<<<<<< HEAD
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderPanorama(g, delta);
        renderModernOverlay(g);

        // 版本号（左下角）
        String version = "StarRailExpress 3.4.0.1";
        if (Minecraft.checkModStatus().shouldReportAsModified())
=======
    public boolean shouldCloseOnEsc() {
        return false;
    }
    public static CompletableFuture<Void> preloadResources(TextureManager textureManager, Executor executor) {
        return CompletableFuture.allOf(textureManager.preload(LogoRenderer.MINECRAFT_LOGO, executor), textureManager.preload(LogoRenderer.MINECRAFT_EDITION, executor), textureManager.preload(PanoramaRenderer.PANORAMA_OVERLAY, executor), CUBE_MAP.preload(textureManager, executor));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        PANORAMA.render(guiGraphics, this.width, this.height, this.panoramaFade, delta);
//        this.renderPanorama(guiGraphics, delta);
//        this.renderModernOverlay(guiGraphics);

        // 版本信息（不缩放，保持固定像素位置）
        String version = "StarRailExpress " + "3.4.0.1";
        if (Minecraft.checkModStatus().shouldReportAsModified()) {
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
            version += I18n.get("menu.modded");
        g.drawString(this.font, version, 8, this.height - 14, 0xB8C0CC, false);

        if (this.waitingForContinue)
            renderContinuePrompt(g, mouseX, mouseY, delta);
        else
            renderMainMenu(g, mouseX, mouseY, delta);

        if (realmsNotificationsEnabled() && !this.waitingForContinue) {
            RenderSystem.enableDepthTest();
            this.realmsNotificationsScreen.render(g, mouseX, mouseY, delta);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  渲染子方法
    // ─────────────────────────────────────────────────────────────────

    private void renderModernOverlay(GuiGraphics g) {
        g.fillGradient(0, 0, this.width, this.height, 0x66101014, 0xAA06070A);
        g.fillGradient(0, 0, this.width, this.height / 3, 0x2200C2FF, 0x00000000);
    }

    private void renderContinuePrompt(GuiGraphics g, int mouseX, int mouseY, float delta) {
        String title = I18n.get("changelog.continue.title");
<<<<<<< HEAD
        String sub   = I18n.get("changelog.continue.subtitle");
        float pulse  = 0.65F + 0.35F * (float) Math.sin(
                (Util.getMillis() - this.screenOpenTime) / 180.0D);
        int alpha    = (int)(this.continueAlpha * pulse * 255.0F);
        int cx = this.width / 2, cy = this.height / 2 + 40;
        g.drawString(this.font, title, cx - this.font.width(title) / 2, cy,
                (alpha << 24) | 0xF3F6FB, false);
        g.drawString(this.font, sub, cx - this.font.width(sub) / 2, cy + 16,
                ((int)(alpha * 0.6F) << 24) | 0xAAB3C2, false);
    }

    private void renderMainMenu(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // ── 左侧面板（动态高度，不超出屏幕） ─────────────────────────
        drawPanel(g, lPanelX, lPanelY, lPanelW, lPanelH, 0x5A0E1117, 0xAA1A1F2A);

        // 标题 / 副标题（在视口上方，不裁剪）
        g.drawString(this.font, I18n.get("changelog.main_menu.title"),
                lPanelX + 18, lPanelY + 14, 0xDDE6F5, false);
        g.drawString(this.font, I18n.get("changelog.main_menu.subtitle"),
                lPanelX + 18, lPanelY + 28, 0x7F8A9E, false);

        // ── 菜单项（scissor 裁剪到视口，叠加滚动偏移） ────────────────
        enableScissor(lPanelX + 1, menuViewportTop, lPanelX + lPanelW - 1, menuViewportBottom);
        try {
            for (MenuEntry entry : this.menuEntries)
                renderMenuEntry(g, entry, mouseX, mouseY);
        } finally {
            RenderSystem.disableScissor();
        }

        // ── 左侧菜单滚动条 ────────────────────────────────────────────
        if (this.menuMaxScroll > 0) {
            int totalMenuH = this.menuEntries.size() * MENU_SPACING;
            int viewportH  = menuViewportBottom - menuViewportTop;
            int sbX        = lPanelX + lPanelW - MENU_SB_W - 3;
            int sbTop      = menuViewportTop + 4;
            int sbBottom   = menuViewportBottom - 4;
            int sbTrackH   = sbBottom - sbTop;
            int thumbH     = Math.max(16, (int)(sbTrackH * (viewportH / (float) totalMenuH)));
            float prog     = this.menuScrollOffset / this.menuMaxScroll;
            int thumbY     = sbTop + (int)(prog * (sbTrackH - thumbH));

            // 轨道
            g.fill(sbX, sbTop, sbX + MENU_SB_W, sbBottom, 0x30FFFFFF);
            // 滑块
            int alpha = this.menuDragging ? 0xAA : 0x66;
            g.fill(sbX, thumbY, sbX + MENU_SB_W, thumbY + thumbH,
                    (alpha << 24) | 0x88AACC);
        }

        // ── 右侧更新日志面板 ─────────────────────────────────────────
        if (showChangelog) renderChangelog(g, mouseX, mouseY);

        // 版权（左下角，贴近版本号上方）
        g.drawString(this.font, I18n.get("changelog.copyright"),
                8, this.height - 28, 0x7F8A9E, false);
=======
        String sub = I18n.get("changelog.continue.subtitle");

        int titleWidth = this.font.width(title);
        int subWidth = this.font.width(sub);

        float pulse = 0.65F + 0.35F * (float) Math.sin((Util.getMillis() - this.screenOpenTime) / 180.0D);
        int alpha = (int) (this.continueAlpha * pulse * 255.0F);
        int color = (alpha << 24) | 0xF3F6FB;
        int subColor = ((int) (alpha * 0.6F) << 24) | 0xAAB3C2;

        int cx = this.width / 2;
        int cy = this.height / 2 + 40;

        guiGraphics.drawString(this.font, title, cx - titleWidth / 2, cy, color, false);
        guiGraphics.drawString(this.font, sub, cx - subWidth / 2, cy + 16, subColor, false);
    }

    private void renderMainMenu(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        // 左侧面板
        drawPanel(guiGraphics, scaledLeftPanelX, scaledLeftPanelY, scaledLeftPanelW, scaledLeftPanelH, 0x5A0E1117, 0xAA1A1F2A);

        // 标题（内部偏移不缩放，保持相对面板位置）
        guiGraphics.drawString(this.font, I18n.get("changelog.main_menu.title"), scaledLeftPanelX + 18, scaledLeftPanelY + 14, 0xDDE6F5, false);
        guiGraphics.drawString(this.font, I18n.get("changelog.main_menu.subtitle"), scaledLeftPanelX + 18, scaledLeftPanelY + 28, 0x7F8A9E, false);

        // 左侧菜单
        for (MenuEntry entry : this.menuEntries) {
            this.renderMenuEntry(guiGraphics, entry, mouseX, mouseY);
        }

        // 右侧更新日志
        this.renderChangelog(guiGraphics, mouseX, mouseY);

        // 底部版权（不缩放）
        String copyright = I18n.get("changelog.copyright");
        guiGraphics.drawString(this.font, copyright, 8, this.height - 28, 0x7F8A9E, false);
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
    }

    /**
     * 渲染单个菜单项。
     * renderY 会叠加 menuScrollOffset，供后续鼠标命中检测使用。
     */
    private void renderMenuEntry(GuiGraphics g, MenuEntry entry, int mouseX, int mouseY) {
        float appearDelay   = entry.index * 0.08F;
        float localProgress = Mth.clamp((this.menuAnimProgress - appearDelay) / 0.35F, 0, 1);
        float eased         = easeOutCubic(localProgress);

        // 水平滑入
        int drawX = (int)(entry.x - (1.0F - eased) * 22.0F);
        // 纵向：基准 Y - 滚动偏移
        int drawY = entry.y - (int) this.menuScrollOffset;

<<<<<<< HEAD
=======
        int finalX = entry.x;
        int drawX = (int) (finalX - (1.0F - eased) * scaledAnimOffset);
        int drawY = entry.y;
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
        int textWidth = this.font.width(entry.text);

        // Hover：同时要求鼠标在视口内
        boolean hovered = mouseX >= drawX && mouseX <= drawX + textWidth + 12
                && mouseY >= drawY - 2 && mouseY <= drawY + 11
                && mouseY >= menuViewportTop && mouseY <= menuViewportBottom;

        entry.hoverAnim += ((hovered ? 1.0F : 0.0F) - entry.hoverAnim) * 0.22F;

        int baseColor   = lerpColor(entry.hoverAnim, 0xC6CFDB, 0xFFFFFF);
        int accentColor = lerpColor(entry.hoverAnim, 0x3AA6FF, 0x7FDBFF);

<<<<<<< HEAD
        // 左侧装饰竖线
        int lineAlpha = (int)(120 + entry.hoverAnim * 100);
        g.fill(drawX - 14, drawY + 2, drawX - 12, drawY + 12,
=======
        // 左侧装饰线（线宽1px不缩放）
        int lineHeight = 10;
        int lineAlpha = (int) (120 + entry.hoverAnim * 100);
        guiGraphics.fill(drawX - 14, drawY + 2, drawX - 12, drawY + 2 + lineHeight,
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
                (lineAlpha << 24) | (accentColor & 0xFFFFFF));

        // 文字（hover 时右移 6px）
        int textOffset = (int)(entry.hoverAnim * 6.0F);
        g.drawString(this.font, entry.text, drawX + textOffset, drawY, baseColor, false);

        // Hover 下划线
        int underlineW = (int)((textWidth + 4) * entry.hoverAnim);
        if (underlineW > 0)
            g.fill(drawX + textOffset, drawY + 12,
                   drawX + textOffset + underlineW, drawY + 13, 0xCC7FDBFF);

        // 存储渲染坐标（含动画偏移 + 滚动）用于点击检测
        entry.renderX      = drawX;
        entry.renderY      = drawY;
        entry.renderWidth  = textWidth + 16;
        entry.renderHeight = 14;
    }

<<<<<<< HEAD
    /**
     * 渲染右侧更新日志面板。
     * 面板高度与左侧面板对齐（均为 lPanelH），顶部对齐 lPanelY。
     */
    private void renderChangelog(GuiGraphics g, int mouseX, int mouseY) {
        final int HEADER_H = 24;
        final int x = cPanelX;
        final int y = lPanelY; // 与左侧面板顶部对齐

        // 内容区最大高度 = 总面板高 - 标题栏 - 间隙
        int maxBodyH     = lPanelH - HEADER_H - 2;
        int currentBodyH = (int)(maxBodyH * this.changelogExpandAnim);

        // 标题栏
        drawPanel(g, x, y, cPanelW, HEADER_H, 0x7A10141B, 0xCC1B2230);
        String foldLabel = this.changelogExpanded
                ? I18n.get("changelog.title") + "  [-]"
                : I18n.get("changelog.title") + "  [+]";
        g.drawString(this.font, foldLabel, x + 12, y + 8, 0xE8EEF8, false);
=======
    private void renderChangelog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
        int y = scaledRightPanelY;
        int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);
        int textMaxWidth = scaledRightPanelW - scale(30); // 文字区域最大宽度

        // 标题栏
        drawPanel(guiGraphics, x, y, scaledRightPanelW, scaledRightPanelHeaderH, 0x7A10141B, 0xCC1B2230);
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

        if (currentBodyH <= 4) return;

<<<<<<< HEAD
        // 内容区面板
        drawPanel(g, x, y + HEADER_H + 2, cPanelW, currentBodyH, 0x520C1016, 0xA0181E28);

        // 滚动参数
        int totalContentH = this.parsedChangelogLines.stream()
                .mapToInt(e -> e.totalHeight).sum();
        int visibleH = currentBodyH - 16; // 上下各留 8px
        this.changelogMaxScroll    = Math.max(0, totalContentH - visibleH);
        this.changelogScrollOffset = Mth.clamp(this.changelogScrollOffset, 0, this.changelogMaxScroll);

        // 裁剪到内容区
        enableScissor(x + 1, y + HEADER_H + 2, x + cPanelW - 1, y + HEADER_H + 2 + currentBodyH);
        try {
            int baseTextY  = y + HEADER_H + 8;
            int currentY   = baseTextY - (int) this.changelogScrollOffset;
            int clipBottom = y + HEADER_H + 2 + currentBodyH;

            for (ChangelogEntry entry : this.parsedChangelogLines) {
                if (currentY + entry.totalHeight >= baseTextY - 80 && currentY <= clipBottom + 80) {
                    var lines = this.font.split(entry.text, cTextMaxW - entry.x);
                    int offsetY = 0;
                    for (var line : lines) {
                        g.drawString(this.font, line,
                                x + entry.x, currentY + offsetY,
                                entry.color, entry.shadow);
                        offsetY += entry.lineHeight;
=======
        // 可折叠内容
        if (currentBodyH > 4) {
            drawPanel(guiGraphics, x, y + scaledRightPanelHeaderH + 2, scaledRightPanelW, currentBodyH, 0x520C1016, 0xA0181E28);

            // 计算最大滚动距离
            int totalContentHeight = this.parsedChangelogLines.stream()
                    .mapToInt(entry -> entry.totalHeight)
                    .sum();
            int visibleHeight = currentBodyH - 24;
            this.changelogMaxScroll = Math.max(0, totalContentHeight - visibleHeight);
            this.changelogScrollOffset = Mth.clamp(this.changelogScrollOffset, 0.0F, this.changelogMaxScroll);

            // 启用剔除区域
            enableScissor(x, y + scaledRightPanelHeaderH + 2, x + scaledRightPanelW, y + scaledRightPanelHeaderH + 2 + currentBodyH);

            try {
                // 渲染滚动内容
                int textY = y + scaledRightPanelHeaderH + 12;
                int currentY = textY - (int) this.changelogScrollOffset;
                for (ChangelogEntry entry : this.parsedChangelogLines) {
                    if (currentY >= textY - 80 && currentY <= y + (scaledRightPanelHeaderH + currentBodyH) * 3) {
                        var wrappedLines = this.font.split(entry.text, textMaxWidth - entry.x);
                        int lineOffset = 0;
                        for (var line : wrappedLines) {
                            guiGraphics.drawString(this.font, line, x + entry.x, currentY + lineOffset, entry.color, entry.shadow);
                            lineOffset += entry.lineHeight;
                        }
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
                    }
                }
<<<<<<< HEAD
                currentY += entry.totalHeight;
=======
            } finally {
                RenderSystem.disableScissor();
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
            }
        } finally {
            RenderSystem.disableScissor();
        }

<<<<<<< HEAD
        // 滚动条
        if (this.changelogMaxScroll > 0) {
            int sbW      = 6;
            int sbX      = x + cPanelW - sbW - 4;
            int sbTop    = y + HEADER_H + 6;
            int sbBottom = y + HEADER_H + 2 + currentBodyH - 6;
            int sbTrackH = sbBottom - sbTop;
            int thumbH   = Math.max(20, (int)(sbTrackH * (visibleH / (float) totalContentH)));
            float prog   = this.changelogScrollOffset / this.changelogMaxScroll;
            int thumbY   = sbTop + (int)(prog * (sbTrackH - thumbH));

            g.fill(sbX, sbTop, sbX + sbW, sbBottom, 0x40FFFFFF);
            int alpha = this.changelogDragging ? 0xAA : 0x66;
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, (alpha << 24) | 0x88AACC);
=======
            // 渲染滚动条
            if (this.changelogMaxScroll > 0) {
                int scrollbarX = x + scaledRightPanelW - scaledScrollbarWidth - 4; // 4px右边距不缩放
                int scrollbarTop = y + scaledRightPanelHeaderH + 6;
                int scrollbarBottom = y + scaledRightPanelHeaderH + currentBodyH - 6;
                int scrollbarTrackHeight = scrollbarBottom - scrollbarTop;

                float scrollProgress = this.changelogScrollOffset / this.changelogMaxScroll;
                int thumbHeight = Math.max(20, (int) (scrollbarTrackHeight * (visibleHeight / (float) totalContentHeight)));
                int thumbY = scrollbarTop + (int) (scrollProgress * (scrollbarTrackHeight - thumbHeight));

                // 滚动条轨道
                guiGraphics.fill(scrollbarX, scrollbarTop, scrollbarX + scaledScrollbarWidth, scrollbarBottom, 0x40FFFFFF);
                // 滚动条滑块
                int thumbAlpha = this.changelogDragging ? 0xAA : 0x66;
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scaledScrollbarWidth, thumbY + thumbHeight,
                        (thumbAlpha << 24) | 0x88AACC);
            }
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  日志解析（Markdown 简易子集：#/##/### 标题 + 普通段落）
    // ─────────────────────────────────────────────────────────────────

    private List<ChangelogEntry> parseChangelogLines(List<String> rawLines) {
        List<ChangelogEntry> entries = new ArrayList<>();
<<<<<<< HEAD
        int baseX = 12;
        int maxW  = Math.max(80, cTextMaxW - baseX);
=======
        int baseX = 12; // 不缩放，因为相对于面板左边距
        int textMaxWidth = scaledRightPanelW - scale(30); // 使用缩放后的面板宽度
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

        for (String line : rawLines) {
            if (line.isEmpty()) {
                // 空行：仅占 8px
                entries.add(new ChangelogEntry(Component.literal(""), baseX, 8, 8, 0xB8C0CC, false));
                continue;
            }
<<<<<<< HEAD
            if (line.startsWith("###")) {
                Component cmp = Component.literal(line.substring(3).trim())
                        .withStyle(s -> s.withBold(true));
                int rows = this.font.split(cmp, maxW).size();
                entries.add(new ChangelogEntry(cmp, baseX, 14, rows * 14, 0xDDE6F5, false));
            } else if (line.startsWith("##")) {
                Component cmp = Component.literal(line.substring(2).trim())
                        .withStyle(s -> s.withBold(true));
                int rows = this.font.split(cmp, maxW).size();
                entries.add(new ChangelogEntry(cmp, baseX, 13, rows * 13, 0xF0F4FF, false));
            } else if (line.startsWith("#")) {
                Component cmp = Component.literal(line.substring(1).trim())
                        .withStyle(s -> s.withBold(true));
                int rows = this.font.split(cmp, maxW).size();
                entries.add(new ChangelogEntry(cmp, baseX, 16, rows * 16, 0xFFFFFF, false));
            } else {
                Component cmp = Component.literal(line);
                int rows = this.font.split(cmp, maxW).size();
                entries.add(new ChangelogEntry(cmp, baseX, 12, rows * 12, 0xB8C0CC, false));
            }
        }
        return entries;
    }

    // ─────────────────────────────────────────────────────────────────
    //  鼠标 & 键盘事件
    // ─────────────────────────────────────────────────────────────────
=======

            if (line.startsWith("###")) {
                String text = line.substring(3).trim();
                Component styledText = Component.literal(text).withStyle(s -> s.withBold(true));
                var wrapped = this.font.split(styledText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        styledText,
                        baseX,
                        14,
                        wrapped.size() * 14,
                        0xDDE6F5,
                        false
                ));
            } else if (line.startsWith("##")) {
                String text = line.substring(2).trim();
                Component styledText = Component.literal(text).withStyle(s -> s.withBold(true));
                var wrapped = this.font.split(styledText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        styledText,
                        baseX,
                        13,
                        wrapped.size() * 13,
                        0xF0F4FF,
                        false
                ));
            } else if (line.startsWith("#")) {
                String text = line.substring(1).trim();
                Component styledText = Component.literal(text).withStyle(s -> s.withBold(true));
                var wrapped = this.font.split(styledText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        styledText,
                        baseX,
                        16,
                        wrapped.size() * 16,
                        0xFFFFFF,
                        false
                ));
            } else {
                Component normalText = Component.literal(line);
                var wrapped = this.font.split(normalText, textMaxWidth - baseX);
                entries.add(new ChangelogEntry(
                        normalText,
                        baseX,
                        12,
                        wrapped.size() * 12,
                        0xB8C0CC,
                        false
                ));
            }
        }

        return entries;
    }

    private static class ChangelogEntry {
        final Component text;
        final int x;
        final int lineHeight;
        final int totalHeight;
        final int color;
        final boolean shadow;

        ChangelogEntry(Component text, int x, int lineHeight, int totalHeight, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.lineHeight = lineHeight;
            this.totalHeight = totalHeight;
            this.color = color;
            this.shadow = shadow;
        }
    }

    private void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = this.minecraft.getWindow();
        int scale = (int) w.getGuiScale();
        int sy0 = (int) (w.getScreenHeight() - y1 * scale);
        RenderSystem.enableScissor(x0 * scale, sy0, (x1 - x0) * scale, (y1 - y0) * scale);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int w, int h, int bg, int border) {
        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);
    }

//    protected void renderPanorama(GuiGraphics guiGraphics, float delta) {
//        // 假设 PANORAMA 是某个静态全景图渲染器
//        // PANORAMA.render(guiGraphics, this.width, this.height, this.panoramaFade, delta);
//    }
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.waitingForContinue) {
            // 播放点击音效
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.waitingForContinue = false;
            this.menuAnimProgress   = 0.0F;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.waitingForContinue) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.waitingForContinue = false;
            this.menuAnimProgress   = 0.0F;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
<<<<<<< HEAD
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double horizontalAmount, double verticalAmount) {
        if (this.waitingForContinue) return false;

        // ── 左侧菜单滚动 ──────────────────────────────────────────────
        if (this.menuMaxScroll > 0
                && mouseX >= lPanelX && mouseX <= lPanelX + lPanelW
                && mouseY >= menuViewportTop && mouseY <= menuViewportBottom) {
            this.menuScrollOffset = Mth.clamp(
                    (float)(this.menuScrollOffset - verticalAmount * 8.0), 0, this.menuMaxScroll);
            return true;
        }

        // ── 右侧日志滚动 ──────────────────────────────────────────────
        if (showChangelog && this.changelogExpanded && this.changelogMaxScroll > 0) {
            final int HEADER_H = 24;
            int maxBodyH     = lPanelH - HEADER_H - 2;
            int currentBodyH = (int)(maxBodyH * this.changelogExpandAnim);
            if (mouseX >= cPanelX && mouseX <= cPanelX + cPanelW
                    && mouseY >= lPanelY + HEADER_H + 2
                    && mouseY <= lPanelY + HEADER_H + 2 + currentBodyH) {
                this.changelogScrollOffset = Mth.clamp(
                        (float)(this.changelogScrollOffset - verticalAmount * 8.0),
                        0, this.changelogMaxScroll);
                return true;
=======
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.waitingForContinue && this.changelogExpanded) {
            int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
            int y = scaledRightPanelY;
            int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);

            // 检查鼠标是否在更新日志区域内
            if (mouseX >= x && mouseX <= x + scaledRightPanelW
                    && mouseY >= y + scaledRightPanelHeaderH + 2 && mouseY <= y + scaledRightPanelHeaderH + currentBodyH) {
                if (this.changelogMaxScroll > 0) {
                    this.changelogScrollOffset = Mth.clamp(
                            (float) (this.changelogScrollOffset - verticalAmount * 8.0),
                            0.0F,
                            this.changelogMaxScroll
                    );
                    return true;
                }
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
<<<<<<< HEAD
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                 double dragX, double dragY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        // ── 左侧菜单滚动条拖拽 ────────────────────────────────────────
        if (this.menuDragging && this.menuMaxScroll > 0) {
            int totalMenuH = this.menuEntries.size() * MENU_SPACING;
            int viewportH  = menuViewportBottom - menuViewportTop;
            int sbTrackH   = (menuViewportBottom - 4) - (menuViewportTop + 4);
            int thumbH     = Math.max(16, (int)(sbTrackH * (viewportH / (float) totalMenuH)));
            float delta    = (float)(dragY * (this.menuMaxScroll / (float)(sbTrackH - thumbH)));
            this.menuScrollOffset = Mth.clamp(this.menuScrollOffset + delta, 0, this.menuMaxScroll);
            return true;
=======
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.changelogDragging && button == 0) {
            if (this.changelogMaxScroll > 0) {
                int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
                int y = scaledRightPanelY;
                int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);

                int scrollbarTop = y + scaledRightPanelHeaderH + 6;
                int scrollbarBottom = y + scaledRightPanelHeaderH + currentBodyH - 6;
                int scrollbarTrackHeight = scrollbarBottom - scrollbarTop;
                int visibleHeight = currentBodyH - 24;
                int totalContentHeight = this.parsedChangelogLines.stream()
                        .mapToInt(entry -> entry.totalHeight)
                        .sum();
                int thumbHeight = Math.max(20, (int) (scrollbarTrackHeight * (visibleHeight / (float) totalContentHeight)));

                float deltaScroll = (float) (dragY * (this.changelogMaxScroll / (scrollbarTrackHeight - thumbHeight)));
                this.changelogScrollOffset = Mth.clamp(
                        this.changelogScrollOffset + deltaScroll,
                        0.0F,
                        this.changelogMaxScroll
                );
                return true;
            }
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
        }

        // ── 右侧日志滚动条拖拽 ────────────────────────────────────────
        if (this.changelogDragging && this.changelogMaxScroll > 0) {
            final int HEADER_H = 24;
            int maxBodyH     = lPanelH - HEADER_H - 2;
            int currentBodyH = (int)(maxBodyH * this.changelogExpandAnim);
            int visibleH     = currentBodyH - 16;
            int totalContentH = this.parsedChangelogLines.stream()
                    .mapToInt(e -> e.totalHeight).sum();
            int sbTrackH = (lPanelY + HEADER_H + 2 + currentBodyH - 6)
                         - (lPanelY + HEADER_H + 6);
            int thumbH   = Math.max(20, (int)(sbTrackH * (visibleH / (float) totalContentH)));
            float delta  = (float)(dragY * (this.changelogMaxScroll / (float)(sbTrackH - thumbH)));
            this.changelogScrollOffset = Mth.clamp(
                    this.changelogScrollOffset + delta, 0, this.changelogMaxScroll);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.waitingForContinue) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.waitingForContinue = false;
            this.menuAnimProgress   = 0.0F;
            return true;
        }

<<<<<<< HEAD
        // ── 右侧日志面板 ──────────────────────────────────────────────
        if (showChangelog) {
            final int HEADER_H = 24;
            int x = cPanelX, y = lPanelY;

            // 标题栏 → 折叠/展开
            if (mouseX >= x && mouseX <= x + cPanelW
                    && mouseY >= y && mouseY <= y + HEADER_H) {
                this.changelogExpanded = !this.changelogExpanded;
                playClick();
                return true;
            }

            // 日志滚动条点击 / 开始拖拽
            if (this.changelogExpanded && button == 0) {
                int maxBodyH     = lPanelH - HEADER_H - 2;
                int currentBodyH = (int)(maxBodyH * this.changelogExpandAnim);
                int sbW   = 6;
                int sbX   = x + cPanelW - sbW - 4;
                int sbTop = y + HEADER_H + 6;
                int sbBot = y + HEADER_H + 2 + currentBodyH - 6;

                if (mouseX >= sbX && mouseX <= sbX + sbW
                        && mouseY >= sbTop && mouseY <= sbBot) {
                    // 点击轨道空白处时：跳转
                    if (this.changelogMaxScroll > 0) {
                        int visibleH      = currentBodyH - 16;
                        int totalContentH = this.parsedChangelogLines.stream()
                                .mapToInt(e -> e.totalHeight).sum();
                        int sbTrackH = sbBot - sbTop;
                        int thumbH   = Math.max(20,
                                (int)(sbTrackH * (visibleH / (float) totalContentH)));
                        float prog  = this.changelogScrollOffset / this.changelogMaxScroll;
                        int thumbY  = sbTop + (int)(prog * (sbTrackH - thumbH));
                        if (mouseY < thumbY || mouseY > thumbY + thumbH) {
                            float newProg = ((float) mouseY - sbTop - thumbH / 2.0F)
                                    / (float)(sbTrackH - thumbH);
                            this.changelogScrollOffset = Mth.clamp(
                                    newProg * this.changelogMaxScroll, 0, this.changelogMaxScroll);
                        }
                    }
                    this.changelogDragging = true;
                    return true;
                }
            }
        }

        // ── 左侧菜单滚动条点击 / 开始拖拽 ────────────────────────────
        if (button == 0 && this.menuMaxScroll > 0) {
            int totalMenuH = this.menuEntries.size() * MENU_SPACING;
            int viewportH  = menuViewportBottom - menuViewportTop;
            int sbX        = lPanelX + lPanelW - MENU_SB_W - 3;
            int sbTop      = menuViewportTop  + 4;
            int sbBot      = menuViewportBottom - 4;
            int sbTrackH   = sbBot - sbTop;
            int thumbH     = Math.max(16, (int)(sbTrackH * (viewportH / (float) totalMenuH)));
            float prog     = this.menuScrollOffset / this.menuMaxScroll;
            int thumbY     = sbTop + (int)(prog * (sbTrackH - thumbH));

            if (mouseX >= sbX && mouseX <= sbX + MENU_SB_W
                    && mouseY >= sbTop && mouseY <= sbBot) {
                // 点击轨道空白处：跳转
                if (mouseY < thumbY || mouseY > thumbY + thumbH) {
                    float newProg = ((float) mouseY - sbTop - thumbH / 2.0F)
                            / (float)(sbTrackH - thumbH);
                    this.menuScrollOffset = Mth.clamp(
                            newProg * this.menuMaxScroll, 0, this.menuMaxScroll);
                }
                this.menuDragging = true;
=======
        // 点击更新日志标题折叠
        int x = this.width - scaledRightPanelW - scaledRightPanelXOffset;
        int y = scaledRightPanelY;

        if (mouseX >= x && mouseX <= x + scaledRightPanelW && mouseY >= y && mouseY <= y + scaledRightPanelHeaderH) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.changelogExpanded = !this.changelogExpanded;
            return true;
        }

        // 检查是否点击了滚动条
        if (!this.waitingForContinue && this.changelogExpanded && button == 0) {
            int currentBodyH = (int) (scaledRightPanelBodyH * this.changelogExpandAnim);
            int scrollbarX = x + scaledRightPanelW - scaledScrollbarWidth - 4;
            int scrollbarTop = y + scaledRightPanelHeaderH + 6;
            int scrollbarBottom = y + scaledRightPanelHeaderH + currentBodyH - 6;

            if (mouseX >= scrollbarX && mouseX <= scrollbarX + scaledScrollbarWidth
                    && mouseY >= scrollbarTop && mouseY <= scrollbarBottom) {
                this.changelogDragging = true;
                this.lastMouseY = mouseY;
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
                return true;
            }
        }

        // ── 菜单项点击（需在视口范围内） ─────────────────────────────
        for (MenuEntry entry : this.menuEntries) {
            if (mouseX >= entry.renderX && mouseX <= entry.renderX + entry.renderWidth
<<<<<<< HEAD
                    && mouseY >= entry.renderY - 2 && mouseY <= entry.renderY + entry.renderHeight
                    && mouseY >= menuViewportTop && mouseY <= menuViewportBottom) {
                playClick();
=======
                    && mouseY >= entry.renderY - 2 && mouseY <= entry.renderY + entry.renderHeight) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
>>>>>>> 7547bf5dc116852c14d5341d2834a4998ed5b0c9
                entry.onPress.run();
                return true;
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return realmsNotificationsEnabled()
                && this.realmsNotificationsScreen.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.menuDragging      = false;
            this.changelogDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Screen 回调
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void removed() {
        if (this.realmsNotificationsScreen != null) this.realmsNotificationsScreen.removed();
    }

    @Override
    public void added() {
        super.added();
        if (this.realmsNotificationsScreen != null) this.realmsNotificationsScreen.added();
    }

    private boolean realmsNotificationsEnabled() {
        return this.realmsNotificationsScreen != null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  工具方法
    // ─────────────────────────────────────────────────────────────────

    private void playClick() {
        this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /**
     * 启用 OpenGL Scissor 裁剪，自动将 GUI 坐标换算为屏幕像素坐标。
     * 注意：y0/y1 是 GUI 坐标（从上到下），OpenGL Scissor 原点在左下角。
     */
    private void enableScissor(int x0, int y0, int x1, int y1) {
        Window w     = this.minecraft.getWindow();
        double scale = w.getGuiScale();
        RenderSystem.enableScissor(
                (int)(x0 * scale),
                (int)(w.getScreenHeight() - y1 * scale),
                (int)((x1 - x0) * scale),
                (int)((y1 - y0) * scale)
        );
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x,         y,         x + w,     y + h,     bg);
        g.fill(x,         y,         x + w,     y + 1,     border);
        g.fill(x,         y + h - 1, x + w,     y + h,     border);
        g.fill(x,         y,         x + 1,     y + h,     border);
        g.fill(x + w - 1, y,         x + w,     y + h,     border);
    }

    protected void renderPanorama(GuiGraphics g, float delta) {
        PANORAMA.render(g, this.width, this.height, this.panoramaFade, delta);
    }

    private static float easeOutCubic(float t) {
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    private static int lerpColor(float t, int a, int b) {
        t = Mth.clamp(t, 0, 1);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) Mth.lerp(t, ar, br) << 16)
             | ((int) Mth.lerp(t, ag, bg) <<  8)
             |  (int) Mth.lerp(t, ab, bb);
    }

    // ─────────────────────────────────────────────────────────────────
    //  内部数据类
    // ─────────────────────────────────────────────────────────────────

    private static class ChangelogEntry {
        final Component text;
        final int x;
        final int lineHeight;   // 单行高度
        final int totalHeight;  // 换行后总高度
        final int color;
        final boolean shadow;

        ChangelogEntry(Component text, int x, int lineHeight, int totalHeight,
                       int color, boolean shadow) {
            this.text        = text;
            this.x           = x;
            this.lineHeight  = lineHeight;
            this.totalHeight = totalHeight;
            this.color       = color;
            this.shadow      = shadow;
        }
    }

    private static class MenuEntry {
        final Component text;
        final Runnable  onPress;
        int   x, y, index;
        float hoverAnim = 0.0F;
        /** 上一帧实际渲染坐标（含动画 + 滚动），用于鼠标命中检测 */
        int renderX, renderY, renderWidth, renderHeight;

        MenuEntry(Component text, Runnable onPress) {
            this.text    = text;
            this.onPress = onPress;
        }
    }
}