package io.wifi.rhythm.client.screen;

import io.wifi.rhythm.client.RhythmMapManager;
import io.wifi.rhythm.data.RhythmMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class RhythmGameListScreen extends Screen {
    private final Screen parent;
    private MapList mapList;

    private static final int MAX_BUTTON_WIDTH = 300; // 加宽后的最大宽度
    private static final int LIST_TOP = 48;
    private static final int LIST_BOTTOM_MARGIN = 48;
    private static final int BACK_BUTTON_HEIGHT = 20;

    public RhythmGameListScreen(Screen parent) {
        super(Component.translatable("rhythm.screen.list.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // 列表区域
        int listHeight = this.height - LIST_TOP - LIST_BOTTOM_MARGIN;
        this.mapList = new MapList(this.minecraft, this.width, listHeight, LIST_TOP, 25);
        this.addRenderableWidget(this.mapList);

        // 添加地图条目
        for (Map.Entry<ResourceLocation, RhythmMapData> entry : RhythmMapManager.MAP_NAMES.entrySet()) {
            this.mapList.addMapEntry(new MapEntry(entry.getKey(), entry.getValue(), false));
        }
        // 添加随机条目
        this.mapList.addMapEntry(new MapEntry(null, null, true));

        // 添加返回按钮，位于列表下方居中
        int buttonWidth = 100;
        int buttonX = (this.width - buttonWidth) / 2;
        int buttonY = this.height - LIST_BOTTOM_MARGIN + (LIST_BOTTOM_MARGIN - BACK_BUTTON_HEIGHT) / 2;
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.back"), (btn) -> this.onClose())
                        .bounds(buttonX, buttonY, buttonWidth, BACK_BUTTON_HEIGHT)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // 内部列表类，提供公共添加方法以绕过 protected 访问限制
    class MapList extends ObjectSelectionList<MapEntry> {
        public MapList(Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height, y0, itemHeight);
        }

        public void addMapEntry(MapEntry entry) {
            super.addEntry(entry);
        }
    }

    class MapEntry extends ObjectSelectionList.Entry<MapEntry> {
        @Nullable
        private final ResourceLocation mapId;
        @Nullable
        private final RhythmMapData mapData;
        private final boolean isRandom;

        public MapEntry(@Nullable ResourceLocation mapId, @Nullable RhythmMapData mapData, boolean isRandom) {
            this.mapId = mapId;
            this.mapData = mapData;
            this.isRandom = isRandom;
        }

        @Override
        public Component getNarration() {
            if (isRandom) {
                return Component.translatable("rhythm.screen.list.random");
            }
            if (mapId != null) {
                String key = "rhythm.map." + mapId.getNamespace() + "." + mapId.getPath();
                return I18n.exists(key) ? Component.translatable(key) : Component.literal(mapId.toString());
            }
            return Component.literal("???");
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            int actualWidth = Math.min(width - 4, MAX_BUTTON_WIDTH);
            int x = left + (width - actualWidth) / 2;

            int bgColor = isMouseOver ? 0x80FFFFFF : 0x80000000;
            guiGraphics.fill(x, top, x + actualWidth, top + height, bgColor);

            Component displayName;
            if (isRandom) {
                displayName = Component.translatable("rhythm.screen.list.random");
            } else if (mapId != null) {
                String key = "rhythm.map." + mapId.getNamespace() + "." + mapId.getPath();
                displayName = I18n.exists(key) ? Component.translatable(key) : Component.literal(mapId.toString());
            } else {
                displayName = Component.literal("Unknown");
            }

            guiGraphics.drawCenteredString(RhythmGameListScreen.this.font, displayName,
                    x + actualWidth / 2, top + (height - 8) / 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.isMouseOver(mouseX, mouseY)) {
                // 播放点击音效
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                );

                if (isRandom) {
                    RhythmMapManager.randomMap().ifPresent(map ->
                            RhythmGameScreen.open(RhythmGameListScreen.this, map));
                } else if (mapData != null) {
                    RhythmGameScreen.open(RhythmGameListScreen.this, mapData);
                }
                return true;
            }
            return false;
        }
    }
}