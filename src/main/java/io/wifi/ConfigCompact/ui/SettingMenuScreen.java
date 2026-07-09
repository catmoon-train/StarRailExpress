package io.wifi.ConfigCompact.ui;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.client.util.SREClientUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

import java.util.function.Supplier;

/**
 * 设置菜单 —— 复古列车车票风格。
 * <p>
 * 遵循 {@code docs/ui_style.md}：深棕渐变背景 + 棕褐描边 + 浅米色装饰线，
 * 金色标题，居中按钮网格。
 */
public class SettingMenuScreen extends Screen {
    Screen parent;
    boolean isFromPausingScreen = false;

    // ── 颜色（复古列车车票风格，参见 docs/ui_style.md §2）───────────────────────
    private static final int BG_TOP     = 0xD81A1008;
    private static final int BG_BOTTOM  = 0xD820140A;
    private static final int BORDER     = 0xFF8B6914;
    private static final int DECOR_LINE = 0x33FFE8C0;
    private static final int GOLD       = 0xFFD4AF37;
    private static final int TEXT       = 0xFFFFF4DC;
    private static final int MUTED      = 0xFF9E8B6E;

    // ── 布局 ──────────────────────────────────────────────────────────────────
    private static final int MAX_PANEL_W = 480;
    private static final int MAX_PANEL_H = 420;
    private static final int MIN_PANEL_H = 280;
    private static final int TITLE_H     = 24;
    private static final int TOP_PAD     = 16;

    static int WIDE_BUTTON_WIDTH = 204;
    static int SMALL_BUTTON_WIDTH = 204;
    static final int BUTTON_HEIGHT = 20;
    static final int MARGIN = 4;
    static int COLUMN_COUNT = 1;
    public boolean showSettings = true;

    public SettingMenuScreen(Screen parent, boolean isFromPausingScreen) {
        this(parent);
        this.isFromPausingScreen = isFromPausingScreen;
    }

    public SettingMenuScreen(Screen parent) {
        super(Component.translatable("screen.starrailexpress.settings"));
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
        }
        if (this.minecraft.level != null) {
            if (!this.minecraft.isSingleplayer()) {
                if (!this.minecraft.isLocalServer()) {
                    if (this.minecraft.getCurrentServer() != null) {
                        if (this.minecraft.player != null) {
                            if (!this.minecraft.player.hasPermissions(2)) {
                                showSettings = false;
                            }
                        }
                    }
                }
            }
        }
        this.parent = parent;
    }

    // =========================================================================
    // renderBackground — 复古列车车票面板
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        drawPanel(g);
    }

    private void drawPanel(GuiGraphics g) {
        int panelW = Math.min(MAX_PANEL_W, (int)(this.width  * 0.55F));
        int panelH = Mth.clamp((int)(this.height * 0.72F), MIN_PANEL_H, MAX_PANEL_H);
        int panelX = (this.width  - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        // 全局半透明遮罩
        g.fill(0, 0, this.width, this.height, 0x88000000);

        // 1. 上下渐变背景
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);

        // 2. 棕褐色描边
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);

        // 3. 上边缘装饰线
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECOR_LINE);
    }

    // =========================================================================
    // render — 叠加金色粗体标题
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        // 金色粗体标题，置于面板顶部
        Component titleComp = this.title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleW = font.width(titleComp);
        int titleX = (this.width - titleW) / 2;

        int panelY = (this.height - Mth.clamp((int)(this.height * 0.72F), MIN_PANEL_H, MAX_PANEL_H)) / 2;
        int titleY = panelY + TOP_PAD;

        g.drawString(font, titleComp, titleX, titleY, TEXT);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    public final Button openScreenButton(Component component, Supplier<Screen> supplier) {
        return Button.builder(component, (button) -> {
            Screen scr = supplier.get();
            if (scr != null || scr != this) {
                this.minecraft.setScreen(scr);
            }
        }).width(SMALL_BUTTON_WIDTH)
                .build();
    }

    private boolean isSmallUI() {
        return height <= 300;
    }

    @Override
    protected void init() {
        super.init();

        int top = 20;
        int maxWidth = this.width;
        this.addRenderableWidget(new StringWidget(0, top, maxWidth, 9, this.title, this.font));
        COLUMN_COUNT = isSmallUI() ? 2 : 1;
        SMALL_BUTTON_WIDTH = isSmallUI() ? 98 : 204;
        GridLayout gridLayout = new GridLayout();
        gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
        GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(COLUMN_COUNT);

        // 角色介绍
        rowHelper.addChild(
                Button.builder(Component.translatable("screen.starrailexpress.settings.introduction"), (button) -> {
                    this.minecraft.setScreen(new RoleIntroduceScreen(this));
                }).width(WIDE_BUTTON_WIDTH).build(), COLUMN_COUNT, gridLayout.newCellSettings().paddingTop(50));
        {
            var bbtn = this.openScreenButton(Component.translatable("screen.starrailexpress.settings.client"),
                    () -> (SREClientConfig.HANDLER.generateGui().generateScreen(this)));
            bbtn.setWidth(WIDE_BUTTON_WIDTH);
            rowHelper.addChild(
                    bbtn, COLUMN_COUNT);
        }

        rowHelper.addChild(
                this.openScreenButton(Component.translatable("screen.starrailexpress.client_utils"),
                        () -> (new ClientUtilScreen(this))));
        // 列车设置
        {
            Button btn = this.openScreenButton(Component.translatable("screen.starrailexpress.settings.tmm"),
                    () -> {
                        if (showSettings)
                            return (SREConfig.HANDLER.generateGui().generateScreen(this));
                        return null;
                    });
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            rowHelper.addChild(btn);
        }

        // Noelle's Roles
        {
            Button btn = this.openScreenButton(Component.translatable("screen.starrailexpress.settings.noellesroles"),
                    () -> {
                        if (showSettings)
                            return (NoellesRolesConfig.HANDLER.generateGui().generateScreen(this));
                        return null;
                    });
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            rowHelper.addChild(btn);
        }
        // HarpyModLoader
        {
            Button btn = this.openScreenButton(Component.translatable("screen.starrailexpress.settings.harpymodloader"),
                    () -> {
                        if (showSettings)
                            return (HarpyModLoaderConfig.HANDLER.generateGui().generateScreen(this));
                        return null;
                    });
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            rowHelper.addChild(btn);
        }

        // StupidExpress
        {
            Button btn = this.openScreenButton(Component.translatable("screen.starrailexpress.settings.stupid_express"),
                    () -> {
                        if (showSettings)
                            return (StupidExpressConfig.HANDLER.generateGui().generateScreen(this));
                        return null;
                    });
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            rowHelper.addChild(btn);
        }
        // 角色设置
        {
            Button btn = this.openScreenButton(Component.translatable("screen.starrailexpress.settings.role_modifier"),
                    () -> {
                        if (showSettings)
                            return (RoleManageConfigUI.getScreen(this));
                        return null;
                    });
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            rowHelper.addChild(btn);
        }
        if (isFromPausingScreen) {
            // 返回原版菜单
            rowHelper.addChild(
                    Button.builder(Component.translatable("screen.starrailexpress.settings.backpausing"), (button) -> {
                        SREClientUtils.setScreenIgnoreMixins(this.minecraft, new PauseScreen(true));
                    }).width(WIDE_BUTTON_WIDTH).build(), COLUMN_COUNT);
        }
        // 返回
        rowHelper.addChild(Button.builder(Component.translatable("gui.back"), (button) -> {
            this.minecraft.setScreen((Screen) parent);
        }).width(WIDE_BUTTON_WIDTH).build(), COLUMN_COUNT);

        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5F, 0.25F);
        gridLayout.visitWidgets(this::addRenderableWidget);
    }

}
