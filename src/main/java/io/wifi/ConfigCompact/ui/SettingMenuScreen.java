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
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

import java.util.function.Supplier;

public class SettingMenuScreen extends Screen {
    Screen parent;
    boolean isFromPausingScreen = false;

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

    static int WIDE_BUTTON_WIDTH = 204;
    static int SMALL_BUTTON_WIDTH = 204;
    static final int BUTTON_HEIGHT = 20;
    static final int MARGIN = 4;
    static int COLUMN_COUNT = 1;
    public boolean showSettings = true;

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
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
        // 客户端设置
        // rowHelper.addChild()

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
        // gridLayout.newCellSettings().paddingTop(50)
        gridLayout.arrangeElements();
        FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5F, 0.25F);
        gridLayout.visitWidgets(this::addRenderableWidget);
    }

}
