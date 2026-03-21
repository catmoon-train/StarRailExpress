package io.wifi.ConfigCompact.ui;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import io.wifi.starrailexpress.SREConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
        // if (this.parent != null) {
        // this.parent.render(context, mouseX, mouseY, delta);
        // }
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
        int buttonCount = 7;

        int buttonY = maxHeight / 2 - buttonCount * (BUTTON_HEIGHT + MARGIN) / 2;

        {
            var btn1 = ModernButton
                    .builder(Component.translatable("screen.starrailexpress.settings.tmm"), (bbtn) -> {
                        if (!showSettings) {
                            return;
                        }
                        var screen = SREConfig.HANDLER.generateGui().generateScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn1.active = false;
                btn1.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = ModernButton
                    .builder(Component.translatable("screen.starrailexpress.settings.noellesroles"), (bbtn) -> {
                        if (!showSettings) {
                            return;
                        }
                        var screen = NoellesRolesConfig.HANDLER.generateGui().generateScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn1.active = false;
                btn1.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = ModernButton
                    .builder(Component.translatable("screen.starrailexpress.settings.harpymodloader"), (bbtn) -> {
                        if (!showSettings) {
                            return;
                        }
                        var screen = HarpyModLoaderConfig.HANDLER.generateGui().generateScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn1.active = false;
                btn1.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = ModernButton
                    .builder(Component.translatable("screen.starrailexpress.settings.stupid_express"), (bbtn) -> {
                        if (!showSettings) {
                            return;
                        }
                        var screen = StupidExpressConfig.HANDLER.generateGui().generateScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn1.active = false;
                btn1.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = ModernButton
                    .builder(Component.translatable("screen.starrailexpress.settings.role_modifier"), (bbtn) -> {
                        if (!showSettings) {
                            return;
                        }
                        var screen = RoleManageConfigUI.getScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            if (!showSettings) {
                btn1.active = false;
                btn1.setTooltip(Tooltip.create(Component.translatable("screen.starrailexpress.settings.unable")
                        .withStyle(ChatFormatting.RED)));
            }
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = ModernButton
                    .builder(Component.translatable("screen.starrailexpress.settings.introduction"), (bbtn) -> {
                        var screen = new RoleIntroduceScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = ModernButton
                    .builder(Component.translatable("gui.back"), (bbtn) -> {
                        this.minecraft.setScreen(parent);
                    }).accentColor(new java.awt.Color(34, 177, 76).getRGB())
                    .bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
    }
}