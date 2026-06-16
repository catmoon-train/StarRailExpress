package io.wifi.starrailexpress.client.gui.screen.map_dev;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

public final class SceneManagerScreen extends Screen {
    private static final int PAGE_SIZE = 5;

    private final List<String> sceneIds;
    private final String currentSceneId;
    private EditBox sceneIdBox;
    private EditBox remoteUrlBox;
    private int page;

    public SceneManagerScreen(List<String> sceneIds, String currentSceneId) {
        super(Component.literal("场景管理"));
        this.sceneIds = List.copyOf(sceneIds);
        this.currentSceneId = currentSceneId == null ? "" : currentSceneId;
        int currentIndex = this.sceneIds.indexOf(this.currentSceneId);
        this.page = currentIndex < 0 ? 0 : currentIndex / PAGE_SIZE;
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelWidth = 360;
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - 382) / 2);

        sceneIdBox = new EditBox(font, left + 10, top + 35, 220, 20, Component.literal("场景 ID"));
        sceneIdBox.setMaxLength(128);
        sceneIdBox.setValue(currentSceneId);
        addRenderableWidget(sceneIdBox);

        addRenderableWidget(Button.builder(Component.literal("指定给当前地图"),
                button -> runAndRefresh("sre:scene library assign " + quotedId()))
                .bounds(left + 236, top + 35, 114, 20).build());

        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = start + row;
            if (index >= sceneIds.size()) {
                break;
            }
            String id = sceneIds.get(index);
            String label = id.equals(currentSceneId) ? "▶ " + id : id;
            addRenderableWidget(Button.builder(Component.literal(label), button -> sceneIdBox.setValue(id))
                    .bounds(left + 10, top + 66 + row * 24, 340, 20).build());
        }

        int navY = top + 190;
        Button previous = Button.builder(Component.literal("上一页"), button -> {
            page--;
            init(minecraft, width, height);
        }).bounds(left + 10, navY, 80, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal("下一页"), button -> {
            page++;
            init(minecraft, width, height);
        }).bounds(left + 100, navY, 80, 20).build();
        next.active = (page + 1) * PAGE_SIZE < sceneIds.size();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(
                Component.literal(SceneAssetClient.isMovingSceneEnabled() ? "客户端场景：开" : "客户端场景：关"),
                button -> {
                    SceneAssetClient.setMovingSceneEnabled(!SceneAssetClient.isMovingSceneEnabled());
                    init(minecraft, width, height);
                }).bounds(left + 190, navY, 160, 20).build());

        int actionY = top + 220;
        addRenderableWidget(Button.builder(Component.literal("保存为新场景"),
                button -> runAndRefresh("sre:scene library save " + quotedId()))
                .bounds(left + 10, actionY, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("覆盖场景"),
                button -> runAndRefresh("sre:scene library save " + quotedId() + " force"))
                .bounds(left + 122, actionY, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("删除场景"),
                button -> runAndRefresh("sre:scene library delete " + quotedId()))
                .bounds(left + 234, actionY, 116, 20).build());

        int editY = top + 250;
        addRenderableWidget(Button.builder(Component.literal("源区域最小角"),
                button -> setSourceCorner("min"))
                .bounds(left + 10, editY, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("源区域最大角"),
                button -> setSourceCorner("max"))
                .bounds(left + 122, editY, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("发布并保存"),
                button -> runAndRefresh("sre:scene publish-save " + quotedId() + " force"))
                .bounds(left + 234, editY, 116, 20).build());

        int axisY = top + 276;
        addRenderableWidget(Button.builder(Component.literal("复制 playArea"),
                button -> sendCommand("sre:scene select source from-play-area"))
                .bounds(left + 10, axisY, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("自动滚动轴"),
                button -> sendCommand("sre:scene axis auto"))
                .bounds(left + 122, axisY, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("滚动轴 X/Y/Z/N"),
                button -> cycleAxis())
                .bounds(left + 234, axisY, 116, 20).build());

        int remoteY = top + 302;
        remoteUrlBox = new EditBox(font, left + 10, remoteY, 238, 20, Component.literal("远程资产 URL"));
        remoteUrlBox.setMaxLength(4096);
        remoteUrlBox.setValue(SREClient.areaComponent == null
                ? ""
                : SREClient.areaComponent.getSceneAssetRemoteUrl());
        addRenderableWidget(remoteUrlBox);
        addRenderableWidget(Button.builder(Component.literal("保存远程 URL"), button -> {
            String url = remoteUrlBox.getValue().trim();
            sendCommand(url.isEmpty() ? "sre:scene remote off" : "sre:scene remote " + url);
        }).bounds(left + 254, remoteY, 96, 20).build());

        boolean trusted = SREClient.areaComponent != null && SREClient.areaComponent.isSceneAssetTrusted();
        addRenderableWidget(Button.builder(
                Component.literal(trusted ? "可信快速模式：开" : "可信快速模式：关"),
                button -> {
                    sendCommand("sre:scene trust " + (trusted ? "off" : "on"));
                    sendCommand("sre:scene manager");
                }).bounds(left + 10, top + 328, 165, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消地图场景指定"), button -> {
            sendCommand("sre:scene library detach");
            sendCommand("sre:scene manager");
        })
                .bounds(left + 185, top + 328, 165, 20).build());

        addRenderableWidget(Button.builder(Component.literal("返回地图助手"), button -> {
            if (minecraft.player != null) {
                minecraft.setScreen(new MapBuildHelperScreen(
                        minecraft.player.blockPosition().below(), 5));
            }
        }).bounds(left + 10, top + 356, 165, 20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新列表"),
                button -> sendCommand("sre:scene manager"))
                .bounds(left + 185, top + 356, 165, 20).build());
    }

    private void setSourceCorner(String corner) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        BlockPos pos = Minecraft.getInstance().player.blockPosition().below();
        sendCommand(String.format("sre:scene select source %s %d %d %d", corner, pos.getX(), pos.getY(), pos.getZ()));
    }

    private void cycleAxis() {
        if (SREClient.areaComponent == null) {
            sendCommand("sre:scene axis x");
            return;
        }
        switch (SREClient.areaComponent.getSceneScroll()) {
            case X -> sendCommand("sre:scene axis y");
            case Y -> sendCommand("sre:scene axis z");
            case Z -> sendCommand("sre:scene axis none");
            case NONE -> sendCommand("sre:scene axis x");
        }
    }

    private void runAndRefresh(String command) {
        if (sceneIdBox.getValue().trim().isEmpty()) {
            return;
        }
        sendCommand(command);
        sendCommand("sre:scene manager");
    }

    private String quotedId() {
        String value = sceneIdBox.getValue().trim();
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void sendCommand(String command) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.connection.sendCommand(command);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = 360;
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - 382) / 2);
        graphics.fill(left, top, left + panelWidth, top + 380, 0xE0101524);
        graphics.fill(left, top, left + panelWidth, top + 1, 0xFF55AADD);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelWidth = 360;
        int left = (width - panelWidth) / 2;
        int top = Math.max(24, (height - 382) / 2);
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(font,
                Component.literal("当前地图场景: " + (currentSceneId.isBlank() ? "未指定" : currentSceneId)),
                left + 10, top + 23, 0x88DDFF, false);
        int pages = Math.max(1, (sceneIds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawString(font,
                Component.literal("场景库: " + sceneIds.size() + " 个  第 " + (page + 1) + "/" + pages + " 页"),
                left + 190, top + 57, 0x99AACC, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
