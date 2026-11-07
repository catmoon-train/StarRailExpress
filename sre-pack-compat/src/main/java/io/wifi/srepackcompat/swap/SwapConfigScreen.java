package io.wifi.srepackcompat.swap;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** 无额外 UI 依赖的原版控件配置页。修改立即保存，ESC 返回。 */
public final class SwapConfigScreen extends Screen {
    private final Screen parent;
    private final SwapConfig cfg;

    public SwapConfigScreen(Screen parent) {
        super(Component.translatable("config.susinstantswap.title"));
        this.parent = parent;
        this.cfg = InstantSwapClient.config();
    }

    @Override
    protected void init() {
        int w = Math.min(310, width - 20);
        int x = (width - w) / 2;
        int y = Math.max(28, (height - 174) / 2);
        toggle(x, y, w, "modEnabled", () -> cfg.modEnabled, v -> cfg.modEnabled = v);
        addRenderableWidget(new ThresholdSlider(x, y + 22, w));
        toggle(x, y + 44, w, "soundEnabled", () -> cfg.soundEnabled, v -> cfg.soundEnabled = v);
        toggle(x, y + 66, w, "mouseReposition", () -> cfg.mouseReposition, v -> cfg.mouseReposition = v);
        toggle(x, y + 88, w, "guiSwapEnabled", () -> cfg.guiSwapEnabled, v -> cfg.guiSwapEnabled = v);
        toggle(x, y + 110, w, "emptySlotSwapEnabled", () -> cfg.emptySlotSwapEnabled, v -> cfg.emptySlotSwapEnabled = v);
        toggle(x, y + 132, w, "debug", () -> cfg.debug, v -> cfg.debug = v);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds((width - 200) / 2, y + 158, 200, 20).build());
    }

    private void toggle(int x, int y, int w, String field, Supplier<Boolean> get, Consumer<Boolean> set) {
        String key = "config.susinstantswap." + field;
        addRenderableWidget(Button.builder(label(key, get.get()), b -> {
            set.accept(!get.get());
            cfg.save();
            b.setMessage(label(key, get.get()));
        }).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.translatable(key + ".tooltip"))).build());
    }

    private static Component label(String key, boolean on) {
        return Component.translatable(key).append(": ").append(Component.translatable(on ? "options.on" : "options.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private final class ThresholdSlider extends AbstractSliderButton {
        private ThresholdSlider(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(), (cfg.holdThresholdMs - 50) / 950.0);
            setTooltip(Tooltip.create(Component.translatable("config.susinstantswap.holdThresholdMs.tooltip")));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("config.susinstantswap.holdThresholdMs")
                    .append(": " + (int) (50 + value * 950) + "ms"));
        }

        @Override
        protected void applyValue() {
            cfg.holdThresholdMs = (int) (50 + value * 950);
            cfg.save();
        }
    }
}
