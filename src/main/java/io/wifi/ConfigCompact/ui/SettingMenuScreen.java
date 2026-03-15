package io.wifi.ConfigCompact.ui;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.client.screen.RoleIntroduceScreen;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingMenuScreen extends Screen {
    Screen parent;

    public SettingMenuScreen(Screen parent) {
        super(Component.translatable("screen.starrailexpress.settings"));
        this.parent = parent;
    }

    final int BUTTON_WIDTH = 200;
    final int BUTTON_HEIGHT = 20;
    final int MARGIN = 4;
    final int buttonCount = 6;

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
        int buttonY = maxHeight / 2 - buttonCount * (BUTTON_HEIGHT + MARGIN) / 2;
        {
            var btn1 = Button
                    .builder(Component.translatable("screen.starrailexpress.settings.noellesroles"), (bbtn) -> {
                        var screen = NoellesRolesConfig.HANDLER.generateGui().generateScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = Button
                    .builder(Component.translatable("screen.starrailexpress.settings.tmm"), (bbtn) -> {
                        var screen = SREConfig.getScreen(this, SRE.MOD_ID);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = Button
                    .builder(Component.translatable("screen.starrailexpress.settings.harpymodloader"), (bbtn) -> {
                        var screen = HarpyModLoaderConfig.HANDLER.generateGui().generateScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = Button
                    .builder(Component.translatable("screen.starrailexpress.settings.role_modifier"), (bbtn) -> {
                        var screen = RoleManageConfigUI.getScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = Button
                    .builder(Component.translatable("screen.starrailexpress.settings.introduction"), (bbtn) -> {
                        var screen = new RoleIntroduceScreen(this);
                        this.minecraft.setScreen(screen);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
        {
            var btn1 = Button
                    .builder(Component.translatable("gui.back"), (bbtn) -> {
                        this.minecraft.setScreen(parent);
                    }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(btn1);
            buttonY += (BUTTON_HEIGHT + MARGIN);
        }
    }
}