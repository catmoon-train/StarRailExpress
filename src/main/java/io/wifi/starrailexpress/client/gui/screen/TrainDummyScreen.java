/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.network.original.TrainDummySpawnC2SPayload;
import io.wifi.starrailexpress.traindummy.TrainDummyEntity;
import io.wifi.starrailexpress.traindummy.TrainDummySettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 列车假人工具界面。风格对齐自定义职业工具：深棕面板、金色标题、翻译键文案。
 * 不保存配置，关掉再开回到默认值。
 */
public class TrainDummyScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 280;
    private static final int ROW = 22;
    private static final int REASON_PAGE = 8;

    private final TrainDummySettings settings = new TrainDummySettings();
    private ItemStack held = ItemStack.EMPTY;
    private boolean pickingReason;
    private int scroll;
    private int contentHeight;
    private String reasonSearch = "";
    private int reasonPage;

    private EditBox skinBox;
    private EditBox hpBox;
    private EditBox virtualShieldBox;
    private EditBox shieldBox;
    private EditBox timedLayersBox;
    private EditBox timedTicksBox;
    private EditBox weakLayersBox;
    private EditBox weakTicksBox;
    private EditBox distanceBox;
    private EditBox breakpointBox;
    private EditBox intervalBox;
    private EditBox scaleBox;
    private EditBox searchBox;

    public TrainDummyScreen() {
        super(Component.translatable("screen.starrailexpress.train_dummy.title"));
        settings.virtualHp = Math.max(1, NoellesRolesConfig.HANDLER.instance().dreamMaxHealth);
    }

    @Override
    protected void init() {
        if (pickingReason) {
            buildReasonPicker();
        } else {
            buildForm();
        }
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    private void buildForm() {
        clearWidgets();
        int x = panelX() + 12;
        int inputX = panelX() + PANEL_W - 132;
        skinBox = box(inputX, 0, 112, settings.skinName);
        hpBox = box(inputX, 0, 112, Integer.toString(settings.virtualHp));
        virtualShieldBox = box(inputX, 0, 112, Integer.toString(settings.virtualShield));
        shieldBox = box(inputX, 0, 112, Integer.toString(settings.shieldLayers));
        timedLayersBox = box(inputX, 0, 52, Integer.toString(settings.timedLayers));
        timedTicksBox = box(inputX + 58, 0, 54, Integer.toString(settings.timedTicks));
        weakLayersBox = box(inputX, 0, 52, Integer.toString(settings.weakLayers));
        weakTicksBox = box(inputX + 58, 0, 54, Integer.toString(settings.weakTicks));
        distanceBox = box(inputX, 0, 112, Integer.toString(settings.distance));
        breakpointBox = box(inputX, 0, 112, Integer.toString(settings.breakpoints));
        intervalBox = box(inputX, 0, 112, Integer.toString(settings.blinkInterval));
        scaleBox = box(inputX, 0, 112, Float.toString(settings.scale));
        addRenderableWidget(skinBox);
        addRenderableWidget(hpBox);
        addRenderableWidget(virtualShieldBox);
        addRenderableWidget(shieldBox);
        addRenderableWidget(timedLayersBox);
        addRenderableWidget(timedTicksBox);
        addRenderableWidget(weakLayersBox);
        addRenderableWidget(weakTicksBox);
        addRenderableWidget(distanceBox);
        addRenderableWidget(breakpointBox);
        addRenderableWidget(intervalBox);
        addRenderableWidget(scaleBox);
        addRenderableWidget(Button.builder(yesNo("screen.starrailexpress.train_dummy.corpse", settings.corpseOnDeath),
                button -> {
                    readForm();
                    settings.corpseOnDeath = !settings.corpseOnDeath;
                    button.setMessage(yesNo("screen.starrailexpress.train_dummy.corpse", settings.corpseOnDeath));
                }).bounds(x, 0, 150, 20).build());
        addRenderableWidget(Button.builder(reasonLabel(), button -> {
            readForm();
            pickingReason = true;
            rebuildWidgets();
        }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(yesNo("screen.starrailexpress.train_dummy.moving", settings.moving),
                button -> {
                    readForm();
                    settings.moving = !settings.moving;
                    rebuildWidgets();
                }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(directionLabel(), button -> {
            readForm();
            settings.direction = Math.floorMod(settings.direction + 1, 6);
            button.setMessage(directionLabel());
        }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(yesNo("screen.starrailexpress.train_dummy.loop", settings.loop),
                button -> {
                    readForm();
                    settings.loop = !settings.loop;
                    button.setMessage(yesNo("screen.starrailexpress.train_dummy.loop", settings.loop));
                }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(yesNo("screen.starrailexpress.train_dummy.random", settings.randomMove),
                button -> {
                    readForm();
                    settings.randomMove = !settings.randomMove;
                    button.setMessage(yesNo("screen.starrailexpress.train_dummy.random", settings.randomMove));
                }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(yesNo("screen.starrailexpress.train_dummy.blink", settings.blink),
                button -> {
                    readForm();
                    settings.blink = !settings.blink;
                    rebuildWidgets();
                }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(heldLabel(), button -> {
            readForm();
            if (this.minecraft != null && this.minecraft.player != null) {
                held = this.minecraft.player.getOffhandItem().copy();
                settings.held = held;
                button.setMessage(heldLabel());
            }
        }).bounds(inputX, 0, 112, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.train_dummy.cancel"),
                button -> onClose()).bounds(panelX() + 16, 0, 140, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.train_dummy.spawn"),
                button -> spawn()).bounds(panelX() + PANEL_W - 156, 0, 140, 20).build());
        layoutForm();
    }

    private void layoutForm() {
        int x = panelX() + 12;
        int inputX = panelX() + PANEL_W - 132;
        int y = panelY() + 28 - scroll;
        int bottom = panelY() + PANEL_H - 36;
        place(skinBox, inputX, y);
        y += ROW;
        place(children().get(12), x, y);
        y += ROW;
        place(hpBox, inputX, y);
        y += ROW;
        place(virtualShieldBox, inputX, y);
        y += ROW;
        place(shieldBox, inputX, y);
        y += ROW;
        place(timedLayersBox, inputX, y);
        place(timedTicksBox, inputX + 58, y);
        y += ROW;
        place(children().get(13), inputX, y);
        y += ROW;
        place(weakLayersBox, inputX, y);
        place(weakTicksBox, inputX + 58, y);
        y += ROW;
        place(children().get(14), inputX, y);
        y += ROW;
        boolean move = settings.moving;
        setShown(children().get(15), move, inputX, y);
        if (move) {
            y += ROW;
        }
        setShown(children().get(16), move, inputX, y);
        if (move) {
            y += ROW;
        }
        setShown(distanceBox, move, inputX, y);
        if (move) {
            y += ROW;
        }
        setShown(children().get(17), move, inputX, y);
        if (move) {
            y += ROW;
        }
        setShown(children().get(18), move, inputX, y);
        if (move) {
            y += ROW;
        }
        boolean blink = move && settings.blink;
        setShown(breakpointBox, blink, inputX, y);
        if (blink) {
            y += ROW;
        }
        setShown(intervalBox, blink, inputX, y);
        if (blink) {
            y += ROW;
        }
        place(children().get(19), inputX, y);
        y += ROW;
        place(scaleBox, inputX, y);
        y += ROW + 8;
        contentHeight = y + scroll - (panelY() + 28);
        int maxScroll = Math.max(0, contentHeight - (PANEL_H - 72));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        place(children().get(20), panelX() + 16, panelY() + PANEL_H - 28);
        place(children().get(21), panelX() + PANEL_W - 156, panelY() + PANEL_H - 28);
        clipInputs(bottom);
    }

    private void clipInputs(int bottom) {
        int top = panelY() + 24;
        for (var child : children()) {
            if (child instanceof EditBox box) {
                box.setEditable(box.getY() >= top && box.getY() <= bottom);
            }
        }
    }

    private void buildReasonPicker() {
        clearWidgets();
        int left = panelX() + 16;
        int top = panelY() + 28;
        searchBox = new EditBox(this.font, left, top, PANEL_W - 32, 18,
                Component.translatable("screen.starrailexpress.train_dummy.reason.search"));
        searchBox.setValue(reasonSearch);
        searchBox.setResponder(value -> {
            reasonSearch = value;
            reasonPage = 0;
            rebuildWidgets();
        });
        searchBox.setTextColor(SREPanelStyle.TEXT);
        searchBox.setHint(SREPanelStyle.hint(Component.translatable("screen.starrailexpress.train_dummy.reason.search")));
        addRenderableWidget(searchBox);
        List<String> reasons = filteredReasons();
        int pages = Math.max(1, (reasons.size() + REASON_PAGE - 1) / REASON_PAGE);
        reasonPage = Mth.clamp(reasonPage, 0, pages - 1);
        int from = reasonPage * REASON_PAGE;
        int y = top + 24;
        for (int i = from; i < Math.min(reasons.size(), from + REASON_PAGE); i++) {
            String id = reasons.get(i);
            addRenderableWidget(Button.builder(reasonName(id), button -> {
                settings.weakReason = id;
                pickingReason = false;
                rebuildWidgets();
            }).bounds(left, y, PANEL_W - 32, 18).build());
            y += 20;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.train_dummy.reason.prev"),
                button -> {
                    reasonPage--;
                    rebuildWidgets();
                }).bounds(left, panelY() + PANEL_H - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.train_dummy.reason.next"),
                button -> {
                    reasonPage++;
                    rebuildWidgets();
                }).bounds(left + 76, panelY() + PANEL_H - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.train_dummy.reason.clear"),
                button -> {
                    settings.weakReason = "";
                    pickingReason = false;
                    rebuildWidgets();
                }).bounds(left + 152, panelY() + PANEL_H - 28, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.train_dummy.reason.back"),
                button -> {
                    pickingReason = false;
                    rebuildWidgets();
                }).bounds(panelX() + PANEL_W - 86, panelY() + PANEL_H - 28, 70, 20).build());
    }

    private List<String> filteredReasons() {
        List<String> reasons = new ArrayList<>(GameConstants.DeathReasons.getAllDeathReasonIds());
        reasons.sort(Comparator.naturalOrder());
        if (reasonSearch == null || reasonSearch.isBlank()) {
            return reasons;
        }
        String needle = reasonSearch.toLowerCase(Locale.ROOT);
        reasons.removeIf(id -> !id.toLowerCase(Locale.ROOT).contains(needle)
                && !reasonName(id).getString().toLowerCase(Locale.ROOT).contains(needle));
        return reasons;
    }

    private void spawn() {
        readForm();
        settings.clamp();
        ClientPlayNetworking.send(new TrainDummySpawnC2SPayload(
                settings.skinName(),
                settings.corpseOnDeath(),
                settings.virtualHp(),
                settings.virtualShield(),
                settings.shieldLayers(),
                settings.timedLayers(),
                settings.timedTicks(),
                settings.weakLayers(),
                settings.weakTicks(),
                settings.weakReason(),
                settings.moving(),
                settings.direction(),
                settings.loop(),
                settings.distance(),
                settings.randomMove(),
                settings.blink(),
                settings.breakpoints(),
                settings.blinkInterval(),
                settings.scale(),
                held.copy()));
        onClose();
    }

    private void readForm() {
        if (skinBox == null) {
            return;
        }
        settings.skinName = skinBox.getValue();
        settings.virtualHp = TrainDummySettings.parseInt(hpBox.getValue(), settings.virtualHp);
        settings.virtualShield = TrainDummySettings.parseInt(virtualShieldBox.getValue(), 0);
        settings.shieldLayers = TrainDummySettings.parseInt(shieldBox.getValue(), 0);
        settings.timedLayers = TrainDummySettings.parseInt(timedLayersBox.getValue(), 0);
        settings.timedTicks = TrainDummySettings.parseInt(timedTicksBox.getValue(), 0);
        settings.weakLayers = TrainDummySettings.parseInt(weakLayersBox.getValue(), 0);
        settings.weakTicks = TrainDummySettings.parseInt(weakTicksBox.getValue(), 0);
        settings.distance = TrainDummySettings.parseInt(distanceBox.getValue(), 1);
        settings.breakpoints = TrainDummySettings.parseInt(breakpointBox.getValue(), 1);
        settings.blinkInterval = TrainDummySettings.parseInt(intervalBox.getValue(), 20);
        settings.scale = TrainDummySettings.parseFloat(scaleBox.getValue(), 1.0F);
        settings.held = held;
        settings.clamp();
    }

    private EditBox box(int x, int y, int width, String value) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.empty());
        box.setMaxLength(32);
        box.setValue(value == null ? "" : value);
        box.setTextColor(SREPanelStyle.TEXT);
        box.setTextColorUneditable(SREPanelStyle.MUTED);
        return box;
    }

    private static void place(net.minecraft.client.gui.components.events.GuiEventListener widget, int x, int y) {
        if (widget instanceof net.minecraft.client.gui.components.AbstractWidget abstractWidget) {
            abstractWidget.setX(x);
            abstractWidget.setY(y);
            abstractWidget.visible = true;
        }
    }

    private static void setShown(net.minecraft.client.gui.components.events.GuiEventListener widget, boolean shown,
            int x, int y) {
        if (widget instanceof net.minecraft.client.gui.components.AbstractWidget abstractWidget) {
            abstractWidget.visible = shown;
            abstractWidget.active = shown;
            if (shown) {
                abstractWidget.setX(x);
                abstractWidget.setY(y);
            }
        }
    }

    private Component yesNo(String key, boolean on) {
        return Component.translatable(key).append(Component.literal(" "))
                .append(Component.translatable(on
                        ? "screen.starrailexpress.train_dummy.yes"
                        : "screen.starrailexpress.train_dummy.no"));
    }

    private Component directionLabel() {
        String key = switch (Math.floorMod(settings.direction, 6)) {
            case 0 -> "screen.starrailexpress.train_dummy.direction.east";
            case 1 -> "screen.starrailexpress.train_dummy.direction.south";
            case 2 -> "screen.starrailexpress.train_dummy.direction.west";
            case 3 -> "screen.starrailexpress.train_dummy.direction.north";
            case 4 -> "screen.starrailexpress.train_dummy.direction.up";
            default -> "screen.starrailexpress.train_dummy.direction.down";
        };
        return Component.translatable("screen.starrailexpress.train_dummy.direction")
                .append(Component.literal(" "))
                .append(Component.translatable(key));
    }

    private Component reasonLabel() {
        if (settings.weakReason == null || settings.weakReason.isBlank()) {
            return Component.translatable("screen.starrailexpress.train_dummy.weak_reason.none");
        }
        return reasonName(settings.weakReason);
    }

    private Component heldLabel() {
        if (held.isEmpty()) {
            return Component.translatable("screen.starrailexpress.train_dummy.held.empty");
        }
        return Component.translatable("screen.starrailexpress.train_dummy.held.set", held.getHoverName());
    }

    public static Component reasonName(String id) {
        if (id == null || id.isBlank()) {
            return Component.translatable("screen.starrailexpress.train_dummy.weak_reason.none");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return Component.literal(id);
        }
        return Component.translatable("death_reason." + parsed.getNamespace() + "." + parsed.getPath());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!pickingReason) {
            scroll = (int) (scroll - scrollY * 12);
            layoutForm();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, SREPanelStyle.SCREEN_BG_TOP, SREPanelStyle.SCREEN_BG_BOTTOM);
        SREPanelStyle.drawPanel(graphics, panelX(), panelY(), PANEL_W, PANEL_H);
        graphics.drawString(this.font, this.title, panelX() + 12, panelY() + 8, SREPanelStyle.GOLD, false);
        SREPanelStyle.drawFooterLine(graphics, panelX() + 1, panelY() + 22, PANEL_W - 2);
        if (!pickingReason) {
            drawFormLabels(graphics);
        } else {
            graphics.drawString(this.font, Component.translatable("screen.starrailexpress.train_dummy.reason.title"),
                    panelX() + 16, panelY() + 28 - 12, SREPanelStyle.MUTED, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawFormLabels(GuiGraphics graphics) {
        int x = panelX() + 12;
        int y = panelY() + 32 - scroll;
        label(graphics, "screen.starrailexpress.train_dummy.skin", x, y);
        y += ROW;
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.virtual_hp", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.virtual_shield", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.shield", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.timed", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.weak_reason", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.weak", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.moving", x, y);
        if (settings.moving) {
            y += ROW;
            label(graphics, "screen.starrailexpress.train_dummy.direction", x, y);
            y += ROW;
            label(graphics, "screen.starrailexpress.train_dummy.loop", x, y);
            y += ROW;
            label(graphics, "screen.starrailexpress.train_dummy.distance", x, y);
            y += ROW;
            label(graphics, "screen.starrailexpress.train_dummy.random", x, y);
            y += ROW;
            label(graphics, "screen.starrailexpress.train_dummy.blink", x, y);
            if (settings.blink) {
                y += ROW;
                label(graphics, "screen.starrailexpress.train_dummy.breakpoints", x, y);
                y += ROW;
                label(graphics, "screen.starrailexpress.train_dummy.blink_interval", x, y);
            }
        }
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.held", x, y);
        y += ROW;
        label(graphics, "screen.starrailexpress.train_dummy.scale", x, y);
    }

    private void label(GuiGraphics graphics, String key, int x, int y) {
        if (y < panelY() + 24 || y > panelY() + PANEL_H - 40) {
            return;
        }
        graphics.drawString(this.font, Component.translatable(key), x, y + 4, SREPanelStyle.TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
