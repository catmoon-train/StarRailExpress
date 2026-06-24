package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.packet.HurricaneDeviceConfigC2SPacket;
import org.jetbrains.annotations.NotNull;

public class HurricaneDeviceConfigScreen extends Screen {
    private final BlockPos pos;
    private final int radius;
    private boolean persistent;
    private final int intervalSeconds;
    private final int durationSeconds;
    private EditBox radiusInput;
    private EditBox intervalInput;
    private EditBox durationInput;
    private Button persistentButton;

    public HurricaneDeviceConfigScreen(BlockPos pos, int radius, boolean persistent, int intervalSeconds, int durationSeconds) {
        super(Component.translatable("screen.noellesroles.hurricane_device.title"));
        this.pos = pos;
        this.radius = radius;
        this.persistent = persistent;
        this.intervalSeconds = intervalSeconds;
        this.durationSeconds = durationSeconds;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 - 72;
        radiusInput = new EditBox(font, centerX - 78, y, 70, 20, Component.empty());
        radiusInput.setValue(String.valueOf(radius));
        addRenderableWidget(radiusInput);

        persistentButton = Button.builder(persistentText(), btn -> {
            persistent = !persistent;
            persistentButton.setMessage(persistentText());
            intervalInput.visible = !persistent;
            durationInput.visible = !persistent;
        }).bounds(centerX - 78, y + 34, 156, 20).build();
        addRenderableWidget(persistentButton);

        intervalInput = new EditBox(font, centerX - 78, y + 76, 70, 20, Component.empty());
        intervalInput.setValue(String.valueOf(intervalSeconds));
        intervalInput.visible = !persistent;
        addRenderableWidget(intervalInput);

        durationInput = new EditBox(font, centerX - 78, y + 110, 70, 20, Component.empty());
        durationInput.setValue(String.valueOf(durationSeconds));
        durationInput.visible = !persistent;
        addRenderableWidget(durationInput);

        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.hurricane_device.save"), btn -> save())
                .bounds(centerX - 40, y + 144, 80, 20).build());
    }

    private Component persistentText() {
        return Component.translatable("screen.noellesroles.hurricane_device.persistent." + (persistent ? "yes" : "no"));
    }

    private void save() {
        try {
            int r = Math.clamp(Integer.parseInt(radiusInput.getValue()), 1, 64);
            int interval = Math.clamp(Integer.parseInt(intervalInput.getValue()), 1, 3600);
            int duration = Math.clamp(Integer.parseInt(durationInput.getValue()), 1, 3600);
            ClientPlayNetworking.send(new HurricaneDeviceConfigC2SPacket(pos, r, persistent, interval, duration));
            Minecraft.getInstance().setScreen(null);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
        int centerX = width / 2;
        int y = height / 2 - 72;
        g.drawCenteredString(font, title, centerX, y - 24, 0xFFFFFF);
        g.drawString(font, Component.translatable("screen.noellesroles.hurricane_device.radius"), centerX - 78, y - 11, 0xAAAAAA);
        if (!persistent) {
            g.drawString(font, Component.translatable("screen.noellesroles.hurricane_device.interval"), centerX - 78, y + 65, 0xAAAAAA);
            g.drawString(font, Component.translatable("screen.noellesroles.hurricane_device.duration"), centerX - 78, y + 99, 0xAAAAAA);
        }
    }
}
