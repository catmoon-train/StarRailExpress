package io.wifi.ConfigCompact.ui;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

public class SettingMenuScreen extends Screen {
    Screen parent;

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

    final int BUTTON_WIDTH = 200;
    final int BUTTON_HEIGHT = 20;
    final int MARGIN = 4;
    public boolean showSettings = true;

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        int maxWidth = this.width;
        int maxHeight = this.height;
        int buttonX = maxWidth / 2 - BUTTON_WIDTH / 2;
        int buttonCount = 8;

        int buttonY = maxHeight / 2 - buttonCount * (BUTTON_HEIGHT + MARGIN) / 2;

        // 客户端设置
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.starrailexpress.settings.client"),
                (btn) -> this.minecraft.setScreen(SREClientConfig.HANDLER.generateGui().generateScreen(this))
        ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        buttonY += (BUTTON_HEIGHT + MARGIN);

        // 列车设置
        {
            Button btn = Button.builder(
                    Component.translatable("screen.starrailexpress.settings.tmm"),
                    (b) -> {
                        if (showSettings)
                            this.minecraft.setScreen(SREConfig.HANDLER.generateGui().generateScreen(this));
                    }
            ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }

        // Noelle's Roles
        {
            Button btn = Button.builder(
                    Component.translatable("screen.starrailexpress.settings.noellesroles"),
                    (b) -> {
                        if (showSettings)
                            this.minecraft.setScreen(NoellesRolesConfig.HANDLER.generateGui().generateScreen(this));
                    }
            ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }

        // HarpyModLoader
        {
            Button btn = Button.builder(
                    Component.translatable("screen.starrailexpress.settings.harpymodloader"),
                    (b) -> {
                        if (showSettings)
                            this.minecraft.setScreen(HarpyModLoaderConfig.HANDLER.generateGui().generateScreen(this));
                    }
            ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }

        // StupidExpress
        {
            Button btn = Button.builder(
                    Component.translatable("screen.starrailexpress.settings.stupid_express"),
                    (b) -> {
                        if (showSettings)
                            this.minecraft.setScreen(StupidExpressConfig.HANDLER.generateGui().generateScreen(this));
                    }
            ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }

        // 角色设置
        {
            Button btn = Button.builder(
                    Component.translatable("screen.starrailexpress.settings.role_modifier"),
                    (b) -> {
                        if (showSettings)
                            this.minecraft.setScreen(RoleManageConfigUI.getScreen(this));
                    }
            ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn.active = false;
                btn.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }

        // 角色介绍
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.starrailexpress.settings.introduction"),
                (btn) -> this.minecraft.setScreen(new RoleIntroduceScreen(this))
        ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        buttonY += (BUTTON_HEIGHT + MARGIN);

        // 返回
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                (btn) -> this.minecraft.setScreen(parent)
        ).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }
}
