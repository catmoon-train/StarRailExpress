package io.wifi.starrailexpress.client.gui.screen.map_dev;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class SceneManagerScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 424;

    private final List<String> sceneIds;
    private final String currentSceneId;
    private EditBox sceneIdBox;
    private EditBox remoteUrlBox;
    private EditBox offsetXBox;
    private EditBox offsetYBox;
    private EditBox offsetZBox;
    private int page;

    public SceneManagerScreen(List<String> sceneIds, String currentSceneId) {
        super(Component.literal("场景编辑器"));
        this.sceneIds = List.copyOf(sceneIds);
        this.currentSceneId = currentSceneId == null ? "" : currentSceneId;
        int currentIndex = this.sceneIds.indexOf(this.currentSceneId);
        this.page = currentIndex < 0 ? 0 : currentIndex / PAGE_SIZE;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = left();
        int top = top();
        int bw = 116;
        int gap = 8;
        int full = PANEL_WIDTH - 20;

        sceneIdBox = new EditBox(font, left + 10, top + 35, 224, 20, Component.literal("场景 ID"));
        sceneIdBox.setMaxLength(128);
        sceneIdBox.setValue(currentSceneId);
        addRenderableWidget(sceneIdBox);

        addRenderableWidget(Button.builder(Component.literal("指定给地图"),
                button -> runAndRefresh("sre:scene library assign " + quotedId()))
                .bounds(left + 242, top + 35, 128, 20).build());

        int listY = top + 66;
        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = start + row;
            if (index >= sceneIds.size()) {
                break;
            }
            String id = sceneIds.get(index);
            String label = id.equals(currentSceneId) ? "> " + id : id;
            addRenderableWidget(Button.builder(Component.literal(label), button -> sceneIdBox.setValue(id))
                    .bounds(left + 10, listY + row * 22, full, 18).build());
        }

        int navY = top + 180;
        Button previous = Button.builder(Component.literal("上一页"), button -> {
            page--;
            init(minecraft, width, height);
        }).bounds(left + 10, navY, 72, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal("下一页"), button -> {
            page++;
            init(minecraft, width, height);
        }).bounds(left + 88, navY, 72, 20).build();
        next.active = (page + 1) * PAGE_SIZE < sceneIds.size();
        addRenderableWidget(next);

        addRenderableWidget(Button.builder(
                Component.literal(SceneAssetClient.isMovingSceneEnabled() ? "客户端场景：开" : "客户端场景：关"),
                button -> {
                    SceneAssetClient.setMovingSceneEnabled(!SceneAssetClient.isMovingSceneEnabled());
                    init(minecraft, width, height);
                }).bounds(left + 170, navY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新列表"),
                button -> sendCommand("sre:scene manager"))
                .bounds(left + 278, navY, 92, 20).build());

        int selectY = top + 210;
        addRenderableWidget(Button.builder(Component.literal("源区域最小角"),
                button -> sendCommandAtToolPos("sre:scene select source min"))
                .bounds(left + 10, selectY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("源区域最大角"),
                button -> sendCommandAtToolPos("sre:scene select source max"))
                .bounds(left + 10 + bw + gap, selectY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("复制 playArea"),
                button -> sendCommand("sre:scene select source from-play-area"))
                .bounds(left + 10 + (bw + gap) * 2, selectY, bw, 20).build());

        int axisY = selectY + 26;
        int axisW = (full - gap * 4) / 5;
        String[] axisNames = { "X", "Y", "Z", "NONE", "AUTO" };
        String[] axisCommands = { "x", "y", "z", "none", "auto" };
        for (int i = 0; i < axisNames.length; i++) {
            final String axisCommand = axisCommands[i];
            addRenderableWidget(Button.builder(Component.literal(axisNames[i]),
                    button -> sendCommand("sre:scene axis " + axisCommand))
                    .bounds(left + 10 + i * (axisW + gap), axisY, axisW, 20).build());
        }

        int offsetY = axisY + 26;
        AreasWorldComponent areas = SREClient.areaComponent;
        offsetXBox = new EditBox(font, left + 10, offsetY, 54, 20, Component.literal("X"));
        offsetYBox = new EditBox(font, left + 70, offsetY, 54, 20, Component.literal("Y"));
        offsetZBox = new EditBox(font, left + 130, offsetY, 54, 20, Component.literal("Z"));
        offsetXBox.setValue(areas == null ? "0" : fmtDouble(areas.getSceneDisplayOffset().x));
        offsetYBox.setValue(areas == null ? "0" : fmtDouble(areas.getSceneDisplayOffset().y));
        offsetZBox.setValue(areas == null ? "0" : fmtDouble(areas.getSceneDisplayOffset().z));
        addRenderableWidget(offsetXBox);
        addRenderableWidget(offsetYBox);
        addRenderableWidget(offsetZBox);
        addRenderableWidget(Button.builder(Component.literal("应用偏移"), button -> applySceneOffset())
                .bounds(left + 192, offsetY, 86, 20).build());
        addRenderableWidget(Button.builder(Component.literal("偏移归零"), button -> {
            offsetXBox.setValue("0");
            offsetYBox.setValue("0");
            offsetZBox.setValue("0");
            sendCommand("sre:scene offset reset");
        }).bounds(left + 284, offsetY, 86, 20).build());

        int actionY = offsetY + 30;
        addRenderableWidget(Button.builder(Component.literal("保存新场景"),
                button -> runAndRefresh("sre:scene library save " + quotedId()))
                .bounds(left + 10, actionY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("覆盖场景"),
                button -> runAndRefresh("sre:scene library save " + quotedId() + " force"))
                .bounds(left + 10 + bw + gap, actionY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("删除场景"),
                button -> runAndRefresh("sre:scene library delete " + quotedId()))
                .bounds(left + 10 + (bw + gap) * 2, actionY, bw, 20).build());

        int publishY = actionY + 26;
        addRenderableWidget(Button.builder(Component.literal("刷新投影"),
                button -> SceneAssetClient.refreshPreview())
                .bounds(left + 10, publishY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("发布资产"),
                button -> sendCommand("sre:scene publish force"))
                .bounds(left + 10 + bw + gap, publishY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存并发布"),
                button -> publishSave())
                .bounds(left + 10 + (bw + gap) * 2, publishY, bw, 20).build());

        int remoteY = publishY + 30;
        remoteUrlBox = new EditBox(font, left + 10, remoteY, 244, 20, Component.literal("远程资产 URL"));
        remoteUrlBox.setMaxLength(4096);
        remoteUrlBox.setValue(areas == null ? "" : areas.getSceneAssetRemoteUrl());
        addRenderableWidget(remoteUrlBox);
        addRenderableWidget(Button.builder(Component.literal("保存远程 URL"), button -> {
            String url = remoteUrlBox.getValue().trim();
            sendCommand(url.isEmpty() ? "sre:scene remote off" : "sre:scene remote " + url);
        }).bounds(left + 262, remoteY, 108, 20).build());

        boolean trusted = areas != null && areas.isSceneAssetTrusted();
        addRenderableWidget(Button.builder(
                Component.literal(trusted ? "可信快速模式：开" : "可信快速模式：关"),
                button -> {
                    sendCommand("sre:scene trust " + (trusted ? "off" : "on"));
                    sendCommand("sre:scene manager");
                }).bounds(left + 10, top + 386, 176, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"),
                button -> onClose())
                .bounds(left + 194, top + 386, 176, 20).build());
    }

    private void publishSave() {
        String id = sceneIdBox.getValue().trim();
        if (id.isEmpty()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("sre.scene.publish.missing_id"), false);
            }
            return;
        }
        sendCommand("sre:scene publish-save " + quotedId() + " force");
    }

    private void applySceneOffset() {
        try {
            double x = Double.parseDouble(offsetXBox.getValue().trim());
            double y = Double.parseDouble(offsetYBox.getValue().trim());
            double z = Double.parseDouble(offsetZBox.getValue().trim());
            sendCommand(String.format(java.util.Locale.ROOT, "sre:scene offset %.6f %.6f %.6f", x, y, z));
        } catch (NumberFormatException ignored) {
        }
    }

    private void sendCommandAtToolPos(String prefix) {
        BlockPos pos = toolPos();
        sendCommand(prefix + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private BlockPos toolPos() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? BlockPos.ZERO : client.player.blockPosition().below();
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

    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9) {
            return String.valueOf((long) v);
        }
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return Math.max(12, (height - PANEL_HEIGHT) / 2);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = left();
        int top = top();
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101524);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF55AADD);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        AreasWorldComponent areas = SREClient.areaComponent;
        BlockPos pos = toolPos();
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF);
        graphics.drawString(font,
                Component.literal("当前地图场景: " + (currentSceneId.isBlank() ? "未指定" : currentSceneId)),
                left + 10, top + 23, 0x88DDFF, false);
        int pages = Math.max(1, (sceneIds.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        graphics.drawString(font,
                Component.literal("场景库: " + sceneIds.size() + " 个  第 " + (page + 1) + "/" + pages + " 页"),
                left + 214, top + 57, 0x99AACC, false);
        graphics.drawString(font,
                Component.literal("编辑坐标: " + pos.toShortString()),
                left + 10, top + 57, 0x99AACC, false);
        if (areas != null) {
            graphics.drawString(font,
                    Component.literal("轴=" + areas.getSceneScroll()
                            + "  选区=" + (areas.isSceneAreaConfigured() ? "完成" : "未完成")
                            + "  资产=" + (areas.getSceneAssetHash().isBlank() ? "未发布" : "已发布")),
                    left + 10, top + PANEL_HEIGHT - 12, 0x88CC99, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
