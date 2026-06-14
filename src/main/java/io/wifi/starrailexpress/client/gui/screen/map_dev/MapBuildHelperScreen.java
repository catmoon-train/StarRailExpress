package io.wifi.starrailexpress.client.gui.screen.map_dev;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.scenery.SceneGeometry;
import io.wifi.starrailexpress.scenery.client.SceneAssetClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MapBuildHelperScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 偏移量（静态 + 文件持久化）
    // ══════════════════════════════════════════════════════════════════

    private static double offsetX = 0.5;
    private static double offsetY = 1;
    private static double offsetZ = 0.5;

    // ══════════════════════════════════════════════════════════════════
    // 实例字段
    // ══════════════════════════════════════════════════════════════════

    private final BlockPos position;
    private int activeTab = 0;

    private EditBox dxBox, dyBox, dzBox;
    private EditBox sceneIdBox;
    private EditBox sceneOffsetXBox, sceneOffsetYBox, sceneOffsetZBox;
    private EditBox mapNameBox, mapImportBox;

    private final List<AbstractWidget> tabWidgets0 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets1 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets2 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets3 = new ArrayList<>(); // 新增：房间选项卡
    private final List<AbstractWidget> tabWidgets4 = new ArrayList<>(); // 新增：环境选项卡
    private final List<AbstractWidget> tabWidgets5 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets6 = new ArrayList<>();

    // 面板居中定位
    private int panelLeftX;
    private int panelTopY;
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 454;

    // ══════════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════════

    public MapBuildHelperScreen(BlockPos position) {
        this(position, 0);
    }

    public MapBuildHelperScreen(BlockPos position, int initialTab) {
        super(Component.translatable("sre.map_helper.title"));
        this.position = position;
        this.activeTab = Math.max(0, Math.min(6, initialTab));
    }

    // ══════════════════════════════════════════════════════════════════
    // 坐标计算
    // ══════════════════════════════════════════════════════════════════

    private double ax() {
        return position.getX() + offsetX;
    }

    private double ay() {
        return position.getY() + offsetY;
    }

    private double az() {
        return position.getZ() + offsetZ;
    }

    private float playerYaw() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getYRot() : 0f;
    }

    private float playerPitch() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getXRot() : 0f;
    }

    // ══════════════════════════════════════════════════════════════════
    // 命令发送（可选是否关闭界面）
    // ══════════════════════════════════════════════════════════════════

    private void sendAndClose(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
        onClose();
    }

    private void sendOnly(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
    }

    // ══════════════════════════════════════════════════════════════════
    // init – 全部 UI 构建
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        tabWidgets0.clear();
        tabWidgets1.clear();
        tabWidgets2.clear();
        tabWidgets3.clear();
        tabWidgets4.clear();
        tabWidgets5.clear();
        tabWidgets6.clear();

        final int bw = 158;
        final int gap = 12;
        final int bh = 22;

        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        buildOffsetRow();
        buildTabBar();

        final int cy = panelTopY + 114;

        // ---------- Tab 0: Positions ----------
        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.translatable("sre.map_helper.set_spawn"),
                b -> sendAndClose(String.format("sre:area_manager set spawnPos %f %f %f %.1f %.1f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(panelLeftX + 6, cy, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build());

        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.translatable("sre.map_helper.set_spectator_spawn"),
                b -> sendAndClose(String.format("sre:area_manager set spectatorSpawnPos %f %f %f %.1f %.1f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(panelLeftX + 6 + bw + gap, cy, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build());

        // ---------- Tab 1: Areas ----------
        String[] areaKeys = { "readyArea", "playArea", "sceneArea", "resetTemplateArea", "resetPasteArea" };
        for (int i = 0; i < areaKeys.length; i++) {
            final String cmd = areaKeys[i];
            final Component areaName = Component.translatable("sre.area." + cmd);
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.translatable("sre.map_helper.area.set_min", areaName),
                    b -> sendAndClose(
                            String.format("sre:area_manager set %s min %.0f %.0f %.0f", cmd, Math.floor(ax()),
                                    Math.floor(ay()), Math.floor(az()))))
                    .bounds(panelLeftX + 6, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.translatable("sre.map_helper.area.set_max", areaName),
                    b -> sendAndClose(
                            String.format("sre:area_manager set %s max %.0f %.0f %.0f", cmd, Math.floor(ax()),
                                    Math.floor(ay()), Math.floor(az()))))
                    .bounds(panelLeftX + 6 + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // ---------- Tab 2: Settings ----------
        String[] boolFields = { "canJump", "canSwim", "noReset", "haveOutsideSound", "sceneOffsetEnabled", "mustCopy" };
        String[] boolFieldKeys = {
                "sre.field.canJump",
                "sre.field.canSwim",
                "sre.field.noReset",
                "sre.field.haveOutsideSound",
                "sre.field.sceneOffsetEnabled",
                "sre.field.mustCopy"
        };
        for (int i = 0; i < boolFields.length; i++) {
            final String field = boolFields[i];
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.translatable("sre.map_helper.set_true", Component.translatable(boolFieldKeys[i])),
                    b -> sendOnly("sre:area_manager set " + field + " true"))
                    .bounds(panelLeftX + 6, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());
            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.translatable("sre.map_helper.set_false", Component.translatable(boolFieldKeys[i])),
                    b -> sendOnly("sre:area_manager set " + field + " false"))
                    .bounds(panelLeftX + 6 + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // ---------- Tab 3: Rooms (新增) ----------
        buildRoomsTab(cy, bw, gap, bh);

        // ---------- Tab 4: Environment (新增) ----------
        buildEnvironmentTab(cy, bw, gap, bh);
        buildSceneTab(cy, bw, gap, bh);
        buildMapTab(cy, bw, gap, bh);

        tabWidgets0.forEach(this::addRenderableWidget);
        tabWidgets1.forEach(this::addRenderableWidget);
        tabWidgets2.forEach(this::addRenderableWidget);
        tabWidgets3.forEach(this::addRenderableWidget);
        tabWidgets4.forEach(this::addRenderableWidget);
        tabWidgets5.forEach(this::addRenderableWidget);
        tabWidgets6.forEach(this::addRenderableWidget);
        syncTabVisibility();
    }

    private void buildMapTab(int startY, int bw, int gap, int bh) {
        int left = panelLeftX + 6;
        int right = left + bw + gap;
        int fullWidth = PANEL_WIDTH - 12;

        AreasWorldComponent areas = SREClient.areaComponent;
        String currentName = areas == null || areas.mapName == null ? "" : areas.mapName;

        mapNameBox = new EditBox(font, left, startY, 230, bh, Component.literal("地图名称"));
        mapNameBox.setMaxLength(128);
        mapNameBox.setValue(currentName);
        addTabWidget(tabWidgets6, mapNameBox);

        int row1 = startY + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("保存为新地图"), b -> {
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map save " + quoteCommandArgument(name));
            }
        }).bounds(left, row1, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("覆盖保存地图"), b -> {
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map save " + quoteCommandArgument(name) + " force");
            }
        }).bounds(right, row1, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row2 = row1 + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("载入此地图配置"), b -> {
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map load " + quoteCommandArgument(name));
            }
        }).bounds(left, row2, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("列出已有地图"),
                b -> sendOnly("sre:area_manager map list"))
                .bounds(right, row2, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row3 = row2 + bh + gap;
        mapImportBox = new EditBox(font, left, row3, fullWidth, bh,
                Component.literal("map_imports 中的 JSON 文件名"));
        mapImportBox.setMaxLength(128);
        addTabWidget(tabWidgets6, mapImportBox);

        int row4 = row3 + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("导入为此地图"),
                b -> importMapConfig(false))
                .bounds(left, row4, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("覆盖导入"),
                b -> importMapConfig(true))
                .bounds(right, row4, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row5 = row4 + bh + gap;
        addTabWidget(tabWidgets6, ModernButton.builder(Component.literal("创建空白地图配置"), b -> {
            sendOnly("sre:area_manager create_new");
            String name = mapNameBox.getValue().trim();
            if (!name.isEmpty()) {
                sendOnly("sre:area_manager map name " + quoteCommandArgument(name));
            }
        }).bounds(left, row5, fullWidth, bh).accentBar(AccentSide.BOTTOM).build());
    }

    private void importMapConfig(boolean force) {
        String filename = mapImportBox.getValue().trim();
        String mapName = mapNameBox.getValue().trim();
        if (filename.isEmpty() || mapName.isEmpty()) {
            return;
        }
        sendOnly("sre:area_manager map import " + quoteCommandArgument(filename)
                + " as " + quoteCommandArgument(mapName)
                + (force ? " force" : ""));
    }

    private void buildSceneTab(int startY, int bw, int gap, int bh) {
        int left = panelLeftX + 6;
        int right = left + bw + gap;
        AreasWorldComponent areas = SREClient.areaComponent;

        sceneIdBox = new EditBox(font, left, startY, 154, bh, Component.literal("场景 ID"));
        sceneIdBox.setMaxLength(128);
        sceneIdBox.setValue(areas == null ? "" : areas.getSceneId());
        addTabWidget(tabWidgets5, sceneIdBox);
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("指定场景"), b -> {
            String id = sceneIdBox.getValue().trim();
            if (!id.isEmpty()) {
                sendOnly("sre:scene library assign " + quoteCommandArgument(id));
            }
        }).bounds(left + 158, startY, 80, bh).accentBar(AccentSide.BOTTOM).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("场景管理"),
                b -> sendOnly("sre:scene manager"))
                .bounds(left + 242, startY, 86, bh).accentBar(AccentSide.RIGHT).build());

        int row1 = startY + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("1. 设置源区域最小角"),
                b -> sendOnly(String.format("sre:scene select source min %.0f %.0f %.0f",
                        Math.floor(ax()), Math.floor(ay()), Math.floor(az()))))
                .bounds(left, row1, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("2. 设置源区域最大角"),
                b -> sendOnly(String.format("sre:scene select source max %.0f %.0f %.0f",
                        Math.floor(ax()), Math.floor(ay()), Math.floor(az()))))
                .bounds(right, row1, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row2 = row1 + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("静态：复制 playArea"),
                b -> sendOnly("sre:scene select source from-play-area"))
                .bounds(left, row2, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("3. 自动选择滚动轴"),
                b -> sendOnly("sre:scene axis auto"))
                .bounds(right, row2, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row3 = row2 + bh + gap;
        int innerWidth = PANEL_WIDTH - 12;
        int axisGap = 6;
        int axisWidth = (innerWidth - axisGap * 3) / 4;
        String[] axisNames = { "X", "Y", "Z", "NONE" };
        String[] axisCommands = { "x", "y", "z", "none" };
        for (int i = 0; i < axisNames.length; i++) {
            final String axisCommand = axisCommands[i];
            addTabWidget(tabWidgets5, ModernButton.builder(Component.literal(axisNames[i]),
                    b -> sendOnly("sre:scene axis " + axisCommand))
                    .bounds(left + i * (axisWidth + axisGap), row3, axisWidth, bh)
                    .accentBar(i == 0 ? AccentSide.LEFT : i == axisNames.length - 1
                            ? AccentSide.RIGHT : AccentSide.BOTTOM)
                    .build());
        }

        int row4 = row3 + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("4. 显示/隐藏投影"), b ->
                SceneAssetClient.setPreviewEnabled(!SceneAssetClient.isPreviewEnabled()))
                .bounds(left, row4, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("暂停/继续滚动"), b ->
                SceneAssetClient.setPreviewPaused(!SceneAssetClient.isPreviewPaused()))
                .bounds(right, row4, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row5 = row4 + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("投影透明度 -"), b ->
                SceneAssetClient.setPreviewAlpha(SceneAssetClient.getPreviewAlpha() - 0.05F))
                .bounds(left, row5, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("投影透明度 +"), b ->
                SceneAssetClient.setPreviewAlpha(SceneAssetClient.getPreviewAlpha() + 0.05F))
                .bounds(right, row5, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row6 = row5 + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("预览速度 -"), b ->
                SceneAssetClient.setPreviewSpeed(SceneAssetClient.getPreviewSpeed() - 0.25F))
                .bounds(left, row6, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("预览速度 +"), b ->
                SceneAssetClient.setPreviewSpeed(SceneAssetClient.getPreviewSpeed() + 0.25F))
                .bounds(right, row6, bw, bh).accentBar(AccentSide.RIGHT).build());

        int row7 = row6 + bh + gap;
        int offsetFieldWidth = 50;
        int offsetGap = 4;
        sceneOffsetXBox = makeField(left, row7, offsetFieldWidth, bh, "0", value -> {});
        sceneOffsetYBox = makeField(left + offsetFieldWidth + offsetGap, row7, offsetFieldWidth, bh, "0",
                value -> {});
        sceneOffsetZBox = makeField(left + (offsetFieldWidth + offsetGap) * 2, row7, offsetFieldWidth, bh, "0",
                value -> {});
        if (areas != null) {
            sceneOffsetXBox.setValue(fmtDouble(areas.getSceneDisplayOffset().x));
            sceneOffsetYBox.setValue(fmtDouble(areas.getSceneDisplayOffset().y));
            sceneOffsetZBox.setValue(fmtDouble(areas.getSceneDisplayOffset().z));
        }
        addTabWidget(tabWidgets5, sceneOffsetXBox);
        addTabWidget(tabWidgets5, sceneOffsetYBox);
        addTabWidget(tabWidgets5, sceneOffsetZBox);
        int offsetButtonX = left + (offsetFieldWidth + offsetGap) * 3;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("应用 XYZ"), b -> applySceneOffset())
                .bounds(offsetButtonX, row7, 78, bh).accentBar(AccentSide.BOTTOM).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("偏移归零"), b -> {
            sceneOffsetXBox.setValue("0");
            sceneOffsetYBox.setValue("0");
            sceneOffsetZBox.setValue("0");
            sendOnly("sre:scene offset reset");
        }).bounds(offsetButtonX + 82, row7, 78, bh).accentBar(AccentSide.RIGHT).build());

        int row8 = row7 + bh + gap;
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("5. 刷新投影"),
                b -> sendOnly("sre:scene preview refresh"))
                .bounds(left, row8, bw, bh).accentBar(AccentSide.LEFT).build());
        addTabWidget(tabWidgets5, ModernButton.builder(Component.literal("6. 一键发布并保存"),
                b -> {
                    String id = sceneIdBox.getValue().trim();
                    if (!id.isEmpty()) {
                        sendOnly("sre:scene publish-save " + quoteCommandArgument(id) + " force");
                    } else if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(
                                Component.translatable("sre.scene.publish.missing_id"), false);
                    }
                })
                .bounds(right, row8, bw, bh).accentBar(AccentSide.RIGHT).build());
    }

    private void applySceneOffset() {
        try {
            double x = Double.parseDouble(sceneOffsetXBox.getValue().trim());
            double y = Double.parseDouble(sceneOffsetYBox.getValue().trim());
            double z = Double.parseDouble(sceneOffsetZBox.getValue().trim());
            sendOnly(String.format(java.util.Locale.ROOT, "sre:scene offset %.6f %.6f %.6f", x, y, z));
        } catch (NumberFormatException ignored) {
        }
    }

    // ── 房间选项卡 UI ────────────────────────────────────────────────
    private void buildRoomsTab(int startY, int bw, int gap, int bh) {
        // 第一行：房间数量设置
        final int row1 = startY;
        final int fieldWidth = 60;
        EditBox roomCountBox = makeField(panelLeftX + 6, row1, fieldWidth, bh, "0",
                v -> {
                    /* 不需要实时响应，点击按钮时读取 */ });
        addTabWidget(tabWidgets3, roomCountBox);

        ModernButton setCountBtn = ModernButton.builder(
                Component.literal("设置房间数量"),
                b -> {
                    String count = roomCountBox.getValue().trim();
                    if (!count.isEmpty()) {
                        sendOnly("sre:area_manager set roomCount " + count);
                    }
                })
                .bounds(panelLeftX + 6 + fieldWidth + gap, row1, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets3, setCountBtn);

        // 第二行：房间 ID 输入框 + 添加按钮
        final int row2 = startY + (bh + gap);
        EditBox roomIdBox = makeField(panelLeftX + 6, row2, fieldWidth, bh, "0",
                v -> {
                });
        addTabWidget(tabWidgets3, roomIdBox);

        ModernButton addRoomBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.add_to_room"),
                b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            long x = (long) Math.floor(ax());
                            long y = (long) Math.floor(ay());
                            long z = (long) Math.floor(az());
                            sendOnly(String.format("sre:area_manager set roomPositions add %d %d %d %d", id, x, y, z));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .bounds(panelLeftX + 6 + fieldWidth + gap, row2, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets3, addRoomBtn);

        // 第三行：移除房间按钮（使用相同的 ID 输入框）
        final int row3 = startY + 2 * (bh + gap);
        ModernButton removeRoomBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.remove_room"),
                b -> {
                    String idStr = roomIdBox.getValue().trim();
                    if (!idStr.isEmpty()) {
                        try {
                            int id = Integer.parseInt(idStr);
                            sendOnly("sre:area_manager set roomPositions remove " + id);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .bounds(panelLeftX + 6 + fieldWidth + gap, row3, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets3, removeRoomBtn);
    }

    // ── 环境选项卡 UI ────────────────────────────────────────────────
    private void buildEnvironmentTab(int startY, int bw, int gap, int bh) {
        final int halfW = (bw - gap) / 2;
        final int smallH = 18;
        final int fieldW = 120;

        // 第0行：天气按钮
        final int row0 = startY;
        ModernButton weatherClearBtn = ModernButton.builder(
                Component.translatable("screen.game_manage.btn.weather_clear"),
                b -> sendOnly("sre:area_manager set weather clear"))
                .bounds(panelLeftX + 6, row0, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets4, weatherClearBtn);

        ModernButton weatherRainBtn = ModernButton.builder(
                Component.translatable("screen.game_manage.btn.weather_rain"),
                b -> sendOnly("sre:area_manager set weather rain"))
                .bounds(panelLeftX + 6 + bw + gap, row0, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, weatherRainBtn);

        // 第1行：雷暴按钮
        final int row1 = startY + (bh + gap);
        ModernButton weatherThunderBtn = ModernButton.builder(
                Component.translatable("screen.game_manage.btn.weather_thunder"),
                b -> sendOnly("sre:area_manager set weather thunder"))
                .bounds(panelLeftX + 6, row1, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets4, weatherThunderBtn);

        // 第2行：重力输入
        final int row2 = startY + 2 * (bh + gap);
        EditBox gravityBox = makeField(panelLeftX + 6, row2, fieldW, smallH, "0.08",
                v -> {});
        addTabWidget(tabWidgets4, gravityBox);

        ModernButton gravityBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_gravity"),
                b -> {
                    String val = gravityBox.getValue().trim();
                    if (!val.isEmpty())
                        sendOnly("sre:area_manager set gravity " + val);
                })
                .bounds(panelLeftX + 6 + fieldW + gap, row2, bw - fieldW - gap + gap, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, gravityBtn);

        // 第3行：时间输入
        final int row3 = startY + 3 * (bh + gap);
        EditBox timeBox = makeField(panelLeftX + 6, row3, fieldW, smallH, "18000",
                v -> {});
        addTabWidget(tabWidgets4, timeBox);

        ModernButton timeBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_time"),
                b -> {
                    String val = timeBox.getValue().trim();
                    if (!val.isEmpty())
                        sendOnly("sre:area_manager set time " + val);
                })
                .bounds(panelLeftX + 6 + fieldW + gap, row3, bw - fieldW - gap + gap, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, timeBtn);

        // 第4行：药水效果输入
        final int row4 = startY + 4 * (bh + gap);
        EditBox effectBox = makeField(panelLeftX + 6, row4, fieldW, smallH, "",
                v -> {});
        addTabWidget(tabWidgets4, effectBox);

        ModernButton effectBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_effect"),
                b -> {
                    String val = effectBox.getValue().trim();
                    sendOnly("sre:area_manager set effect " + (val.isEmpty() ? "\"\"" : val));
                })
                .bounds(panelLeftX + 6 + fieldW + gap, row4, bw - fieldW - gap + gap, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, effectBtn);

        // 第5行：雪花效果开关
        final int row5 = startY + 5 * (bh + gap);
        ModernButton snowEnableBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_true", Component.translatable("sre.field.snowEnabled")),
                b -> sendOnly("sre:area_manager set snowEnabled true"))
                .bounds(panelLeftX + 6, row5, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets4, snowEnableBtn);

        ModernButton snowDisableBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_false", Component.translatable("sre.field.snowEnabled")),
                b -> sendOnly("sre:area_manager set snowEnabled false"))
                .bounds(panelLeftX + 6 + bw + gap, row5, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, snowDisableBtn);

        // 第6行：昼夜循环开关
        final int row6 = startY + 6 * (bh + gap);
        ModernButton daylightEnableBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_true", Component.translatable("sre.field.daylightCycle")),
                b -> sendOnly("sre:area_manager set daylightCycle true"))
                .bounds(panelLeftX + 6, row6, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets4, daylightEnableBtn);

        ModernButton daylightDisableBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_false", Component.translatable("sre.field.daylightCycle")),
                b -> sendOnly("sre:area_manager set daylightCycle false"))
                .bounds(panelLeftX + 6 + bw + gap, row6, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, daylightDisableBtn);

        // 第7行：天气循环开关
        final int row7 = startY + 7 * (bh + gap);
        ModernButton weatherCycleEnableBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_true", Component.translatable("sre.field.weatherCycle")),
                b -> sendOnly("sre:area_manager set weatherCycle true"))
                .bounds(panelLeftX + 6, row7, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build();
        addTabWidget(tabWidgets4, weatherCycleEnableBtn);

        ModernButton weatherCycleDisableBtn = ModernButton.builder(
                Component.translatable("sre.map_helper.set_false", Component.translatable("sre.field.weatherCycle")),
                b -> sendOnly("sre:area_manager set weatherCycle false"))
                .bounds(panelLeftX + 6 + bw + gap, row7, bw, bh)
                .accentBar(AccentSide.RIGHT)
                .build();
        addTabWidget(tabWidgets4, weatherCycleDisableBtn);
    }

    // ── 偏移量行 ─────────────────────────────────────────────────────
    private void buildOffsetRow() {
        final int oy = panelTopY + 52;
        final int fh = 18;
        final int labelW = 14;
        final int fieldW = 64;
        final int smallGap = 6;
        final int bigGap = 12;
        final int groupW = labelW + smallGap + fieldW;
        final int resetW = 48;
        final int totalW = groupW * 3 + bigGap * 2 + resetW;
        final int startX = panelLeftX + (PANEL_WIDTH - totalW) / 2;

        dxBox = makeField(startX + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetX = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        dxBox.setValue(fmtDouble(offsetX));
        addRenderableWidget(dxBox);

        int yStart = startX + groupW + bigGap;
        dyBox = makeField(yStart + labelW + smallGap, oy, fieldW, fh, "1",
                v -> {
                    try {
                        offsetY = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        dyBox.setValue(fmtDouble(offsetY));
        addRenderableWidget(dyBox);

        int zStart = yStart + groupW + bigGap;
        dzBox = makeField(zStart + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetZ = Double.parseDouble(v);
                    } catch (Exception ignored) {
                    }
                });
        dzBox.setValue(fmtDouble(offsetZ));
        addRenderableWidget(dzBox);

        int resetX = zStart + groupW + bigGap;
        addRenderableWidget(ModernButton.builder(Component.translatable("sre.map_helper.reset"), b -> {
            offsetX = offsetZ = 0.5;
            offsetY = 1;
            dxBox.setValue("0.5");
            dyBox.setValue("1");
            dzBox.setValue("0.5");
        }).bounds(resetX, oy, resetW, fh)
                .accentBar(AccentSide.BOTTOM)
                .build());
    }

    // ── Tab 栏 ───────────────────────────────────────────────────────
    private void buildTabBar() {
        final int tabY = panelTopY + 74;
        final int tabH = 22;
        final int tabW = 42;
        final int tabGap = 5;
        final int totalTabW = tabW * 7 + tabGap * 6;
        final int startX = panelLeftX + (PANEL_WIDTH - totalTabW) / 2;

        String[] tabKeys = { "positions", "areas", "settings", "rooms", "environment", "scene", "map" };
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            var builder = ModernButton.builder(Component.translatable("sre.map_helper.tab." + tabKeys[i]), b -> {
                activeTab = idx;
                init(minecraft, width, height);
            }).bounds(startX + i * (tabW + tabGap), tabY, tabW, tabH);

            if (activeTab == i)
                builder.accentBar(AccentSide.BOTTOM);
            else
                builder.accentBar();
            addRenderableWidget(builder.build());
        }
    }

    // ── 辅助方法 ────────────────────────────────────────────────────
    private void addTabWidget(List<AbstractWidget> list, AbstractWidget widget) {
        list.add(widget);
    }

    private void syncTabVisibility() {
        tabWidgets0.forEach(w -> w.visible = (activeTab == 0));
        tabWidgets1.forEach(w -> w.visible = (activeTab == 1));
        tabWidgets2.forEach(w -> w.visible = (activeTab == 2));
        tabWidgets3.forEach(w -> w.visible = (activeTab == 3));
        tabWidgets4.forEach(w -> w.visible = (activeTab == 4));
        tabWidgets5.forEach(w -> w.visible = (activeTab == 5));
        tabWidgets6.forEach(w -> w.visible = (activeTab == 6));
    }

    private EditBox makeField(int x, int y, int w, int h, String defaultVal, Consumer<String> responder) {
        var box = new EditBox(font, x, y, w, h, Component.empty());
        box.setValue(defaultVal);
        box.setMaxLength(20);
        box.setResponder(responder);
        return box;
    }

    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9)
            return String.valueOf((long) v);
        String s = String.format("%.4f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private static String quoteCommandArgument(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY + PANEL_HEIGHT + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY - 2, 0xFF5577CC);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        final int cx = panelLeftX + PANEL_WIDTH / 2;

        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.title").withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
                cx, panelTopY + 10, 0xFFFFFF);

        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.source_pos", position.getX(), position.getY(), position.getZ())
                        .withStyle(s -> s.withColor(0x778899)),
                cx, panelTopY + 22, 0xFFFFFF);

        boolean hasOffset = offsetX != 0 || offsetY != 0 || offsetZ != 0;
        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.applied_pos", ax(), ay(), az())
                        .withStyle(s -> s.withColor(hasOffset ? 0x55DD88 : 0x445566)),
                cx, panelTopY + 32, 0xFFFFFF);

        // 偏移量标签
        final int oy = panelTopY + 52;
        final int fh = 18;
        final int labelW = 14;
        final int fieldW = 64;
        final int smallGap = 6;
        final int bigGap = 12;
        int groupW = labelW + smallGap + fieldW;
        int resetW = 48;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = panelLeftX + (PANEL_WIDTH - totalW) / 2;

        g.drawString(font, Component.translatable("sre.map_helper.dx"), startX, oy + 4, 0xAABBCC, false);
        int yStart = startX + groupW + bigGap;
        g.drawString(font, Component.translatable("sre.map_helper.dy"), yStart, oy + 4, 0xAABBCC, false);
        int zStart = yStart + groupW + bigGap;
        g.drawString(font, Component.translatable("sre.map_helper.dz"), zStart, oy + 4, 0xAABBCC, false);

        g.fill(panelLeftX, panelTopY + 70, panelLeftX + PANEL_WIDTH, panelTopY + 71, 0x33AABBCC);
        g.fill(panelLeftX, panelTopY + 94, panelLeftX + PANEL_WIDTH, panelTopY + 95, 0x33AABBCC);

        String[] tabTitlesKeys = {
                "spawn_offset", "aabb_areas", "boolean_settings", "rooms_config", "environment", "scene", "map"
        };
        g.drawString(font,
                Component.translatable("sre.map_helper.tab_title." + tabTitlesKeys[activeTab])
                        .withStyle(Style.EMPTY.withColor(0x5577CC).withBold(true)),
                panelLeftX + 6, panelTopY + 100, 0xFFFFFF, false);

        if (activeTab == 1) {
            g.drawString(font,
                    Component.translatable("sre.map_helper.areas.hint", ax(), ay(), az())
                            .withStyle(s -> s.withColor(0x445566)),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0xFFFFFF, false);
        } else if (activeTab == 3) {
            g.drawString(font,
                    Component.literal("房间 ID 必须为整数，坐标自动取整为当前偏移位置")
                            .withStyle(s -> s.withColor(0x445566)),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0xFFFFFF, false);
        } else if (activeTab == 5) {
            renderSceneSummary(g);
        } else if (activeTab == 6) {
            String mapName = SREClient.areaComponent == null || SREClient.areaComponent.mapName == null
                    ? "-"
                    : SREClient.areaComponent.mapName;
            g.drawString(font, Component.literal("当前地图: " + mapName),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 24, 0x88DDFF, false);
            g.drawString(font, Component.literal("导入目录: <world>/map_imports，仅接受单个 JSON 文件名"),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0x88CC99, false);
        }
    }

    private void renderSceneSummary(GuiGraphics g) {
        AreasWorldComponent areas = SREClient.areaComponent;
        if (areas == null) {
            return;
        }
        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(areas.getSceneArea());
        var status = SceneAssetClient.cacheStatus();
        String hash = SceneAssetClient.currentHash();
        g.drawString(font, Component.literal(String.format(
                "场景=%s 选区=%s 发布=%s 轴=%s 区段=%d 投影=%.0f%% 速度=%.2f%s",
                areas.getSceneId().isBlank() ? "未指定" : areas.getSceneId(),
                areas.isSceneAreaConfigured() ? "完成" : "未完成",
                areas.getSceneAssetHash().isBlank() ? "未完成" : "完成",
                areas.getSceneScroll(), bounds.sectionCount(),
                SceneAssetClient.getPreviewAlpha() * 100.0F, SceneAssetClient.getPreviewSpeed(),
                SceneAssetClient.isPreviewPaused() ? " [暂停]" : "")),
                panelLeftX + 6, panelTopY + PANEL_HEIGHT - 24, 0x88DDFF, false);
        g.drawString(font, Component.literal(String.format(
                "缓存=%d 个 %.1f/%.1f MiB 当前=%s 客户端=%s 远程=%s%s",
                status.entries(), status.bytes() / 1048576.0D, status.limitBytes() / 1048576.0D,
                hash.isBlank() ? "-" : hash.substring(0, Math.min(12, hash.length())),
                SceneAssetClient.isMovingSceneEnabled() ? "开" : "关",
                areas.getSceneAssetRemoteUrl().isBlank() ? "关" : "开",
                SceneAssetClient.isRemoteDownloading() ? " [远程下载中]" : "")),
                panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0x88CC99, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        SceneAssetClient.closeEditor();
        super.onClose();
    }
}
