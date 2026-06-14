package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.scenery.SceneGeometry;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import io.wifi.starrailexpress.scenery.server.SceneAssetServer;
import io.wifi.starrailexpress.scenery.server.SceneLibrary;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SceneCommand {
    private SceneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:scene")
                .then(Commands.literal("editor")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> openEditor(context.getSource())))
                .then(Commands.literal("wizard")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> openEditor(context.getSource())))
                .then(Commands.literal("manager")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> openManager(context.getSource())))
                .then(Commands.literal("help")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> help(context.getSource())))
                .then(Commands.literal("select")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("source")
                                .then(selectionCorner("min"))
                                .then(selectionCorner("max"))
                                .then(Commands.literal("from-play-area")
                                        .executes(context -> copyPlayArea(context.getSource())))))
                .then(Commands.literal("axis")
                        .requires(source -> source.hasPermission(3))
                        .then(axis("x", AreasWorldComponent.ScrollAxis.X))
                        .then(axis("y", AreasWorldComponent.ScrollAxis.Y))
                        .then(axis("z", AreasWorldComponent.ScrollAxis.Z))
                        .then(axis("none", AreasWorldComponent.ScrollAxis.NONE))
                        .then(Commands.literal("auto")
                                .executes(context -> autoAxis(context.getSource()))))
                .then(Commands.literal("offset")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("reset")
                                .executes(context -> setOffset(context.getSource(), Vec3.ZERO)))
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(context -> setOffset(context.getSource(), new Vec3(
                                                        DoubleArgumentType.getDouble(context, "x"),
                                                        DoubleArgumentType.getDouble(context, "y"),
                                                        DoubleArgumentType.getDouble(context, "z"))))))))
                .then(Commands.literal("remote")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("off")
                                .executes(context -> setRemote(context.getSource(), "")))
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(context -> setRemote(context.getSource(),
                                        StringArgumentType.getString(context, "url")))))
                .then(Commands.literal("trust")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("on")
                                .executes(context -> setTrusted(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setTrusted(context.getSource(), false))))
                .then(Commands.literal("library")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("save")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> saveScene(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"), false))
                                        .then(Commands.literal("force")
                                                .executes(context -> saveScene(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"), true)))))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> assignScene(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> deleteScene(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("detach")
                                .executes(context -> detachScene(context.getSource())))
                        .then(Commands.literal("list")
                                .executes(context -> listScenes(context.getSource()))))
                .then(Commands.literal("preview")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("refresh")
                                .executes(context -> refreshPreview(context.getSource()))))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> status(context.getSource()))
                        .then(Commands.argument("map", StringArgumentType.word())
                                .executes(context -> status(context.getSource()))))
                .then(Commands.literal("validate")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> validate(context.getSource()))
                        .then(Commands.argument("map", StringArgumentType.word())
                                .executes(context -> validate(context.getSource()))))
                .then(Commands.literal("publish")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> publish(context.getSource(), currentMap(context.getSource()), false))
                        .then(Commands.literal("force")
                                .executes(context -> publish(context.getSource(), currentMap(context.getSource()), true)))
                        .then(Commands.argument("map", StringArgumentType.word())
                                .executes(context -> publish(context.getSource(),
                                        StringArgumentType.getString(context, "map"), false))
                                .then(Commands.literal("force")
                                        .executes(context -> publish(context.getSource(),
                                                StringArgumentType.getString(context, "map"), true)))))
                .then(Commands.literal("publish-save")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> publishAndSave(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "id"), false))
                                .then(Commands.literal("force")
                                        .executes(context -> publishAndSave(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"), true)))))
                .then(Commands.literal("invalidate")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> invalidate(context.getSource()))
                        .then(Commands.argument("map", StringArgumentType.word())
                                .executes(context -> invalidate(context.getSource())))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> selectionCorner(String corner) {
        return Commands.literal(corner)
                .executes(context -> select(context.getSource(), corner,
                        BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> select(context.getSource(), corner,
                                BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> axis(
            String name, AreasWorldComponent.ScrollAxis axis) {
        return Commands.literal(name).executes(context -> {
            ServerLevel level = context.getSource().getLevel();
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
            areas.setSceneScroll(axis);
            areas.sync();
            persistSceneConfig(level, areas);
            SceneAssetNetwork.sendManifestToAll(level);
            context.getSource().sendSuccess(() -> Component.literal("场景滚动轴已设为 " + axis), true);
            return 1;
        });
    }

    private static int openEditor(CommandSourceStack source) {
        try {


            SceneAssetNetwork.openEditor(source.getPlayerOrException());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static int openManager(CommandSourceStack source) {
        try {
            SceneAssetNetwork.openSceneManager(source.getPlayerOrException());
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法打开场景管理器: " + e.getMessage()));
            return 0;
        }
    }

    private static int saveScene(CommandSourceStack source, String id, boolean overwrite) {
        ServerLevel level = source.getLevel();
        SceneLibrary.Result result = SceneLibrary.saveCurrent(level, id, overwrite);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        try {
            if (areas.mapName != null && !areas.mapName.isBlank()) {
                MapManager.updateMapSceneReference(level, areas.mapName, result.id());
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal(result.message() + "，但写入当前地图引用失败"));
            return 0;
        }
        SceneAssetServer.activate(level);
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal(result.message() + "，并已指定给当前地图"), true);
        return 1;
    }

    private static int assignScene(CommandSourceStack source, String id) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank()) {
            source.sendFailure(Component.literal("请先载入或保存一张地图"));
            return 0;
        }
        SceneLibrary.Result result = SceneLibrary.loadInto(level, id);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        try {
            MapManager.updateMapSceneReference(level, areas.mapName, result.id());
        } catch (Exception e) {
            source.sendFailure(Component.literal("场景已载入，但写入地图引用失败: " + e.getMessage()));
            return 0;
        }
        areas.sync();
        SceneAssetServer.activate(level);
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal("地图 " + areas.mapName + " 已指定场景 " + result.id()), true);
        return 1;
    }

    private static int deleteScene(CommandSourceStack source, String id) {
        SceneLibrary.Result result = SceneLibrary.delete(source.getLevel(), id);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int detachScene(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        try {
            if (areas.mapName != null && !areas.mapName.isBlank()) {
                MapManager.updateMapSceneReference(level, areas.mapName, "");
            }
            SceneLibrary.clearScene(areas);
            areas.sync();
            SceneAssetServer.activate(level);
            SceneAssetNetwork.sendManifestToAll(level);
            source.sendSuccess(() -> Component.literal("已取消当前地图的场景指定"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("取消场景失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int listScenes(CommandSourceStack source) {
        java.util.List<String> scenes = SceneLibrary.list(source.getLevel());
        source.sendSuccess(() -> Component.literal("场景库 (" + scenes.size() + "):"), false);
        scenes.forEach(id -> source.sendSuccess(() -> Component.literal(" - " + id), false));
        return Math.max(1, scenes.size());
    }

    private static int refreshPreview(CommandSourceStack source) {
        try {


            SceneAssetNetwork.openEditor(source.getPlayerOrException());
            source.sendSuccess(() -> Component.literal("已刷新场景预览"), false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 1;
    }

    private static int select(CommandSourceStack source, String corner, BlockPos pos) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AABB old = areas.getSceneArea();
        AABB next;
        if ("min".equals(corner)) {
            next = normalized(pos.getX(), pos.getY(), pos.getZ(), old.maxX, old.maxY, old.maxZ);
        } else {
            next = normalized(old.minX, old.minY, old.minZ,
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        }
        areas.setSceneArea(next);
        areas.setSceneAreaConfigured(true);
        if (areas.getSceneScroll() == AreasWorldComponent.ScrollAxis.NONE) {
            areas.setSceneScroll(AreasWorldComponent.ScrollAxis.X);
        }
        areas.sync();
        SceneAssetServer.invalidate(level, "场景源区域已修改");
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal("场景源区域 " + corner + " 已设为 " + pos.toShortString()), true);
        return 1;
    }

    private static int copyPlayArea(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.setSceneArea(areas.getPlayArea());
        areas.setSceneAreaConfigured(true);
        areas.setSceneScroll(AreasWorldComponent.ScrollAxis.NONE);
        areas.sync();
        SceneAssetServer.invalidate(level, "场景源区域已从 playArea 复制");
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal(
                "已使用 playArea 作为静态场景源区域，滚动轴设为 NONE；滚动场景请重新选择不重叠的窗外源区域"), true);
        return 1;
    }

    private static int autoAxis(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AreasWorldComponent.ScrollAxis axis = suggestedAxis(areas.getSceneArea());
        areas.setSceneScroll(axis);
        areas.sync();
        persistSceneConfig(level, areas);
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal("已根据场景尺寸自动选择滚动轴 " + axis), true);
        return 1;
    }

    private static AreasWorldComponent.ScrollAxis suggestedAxis(AABB area) {
        double x = area.getXsize();
        double y = area.getYsize();
        double z = area.getZsize();
        if (y > x && y > z) {
            return AreasWorldComponent.ScrollAxis.Y;
        }
        return z > x ? AreasWorldComponent.ScrollAxis.Z : AreasWorldComponent.ScrollAxis.X;
    }

    private static int setOffset(CommandSourceStack source, Vec3 offset) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.setSceneDisplayOffset(offset);
        areas.sync();
        if (!persistSceneConfig(level, areas)) {
            source.sendFailure(Component.literal("显示偏移已应用，但写入地图 JSON 失败"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "场景显示偏移已设为 %.2f %.2f %.2f", offset.x, offset.y, offset.z)), true);
        return 1;
    }

    private static int setRemote(CommandSourceStack source, String value) {
        String url = value == null ? "" : value.trim();
        if (!url.isBlank() && !isRemoteTemplate(url)) {
            source.sendFailure(Component.literal("远程地址必须是 http/https URL，可使用 {sha256} 和 {map}"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.setSceneAssetRemoteUrl(url);
        areas.sync();
        if (!persistSceneConfig(level, areas)) {
            source.sendFailure(Component.literal("远程地址已应用，但写入地图 JSON 失败"));
            return 0;
        }
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal(url.isBlank()
                ? "已关闭场景远程下载"
                : "场景远程地址已保存: " + url), true);
        return 1;
    }

    private static int setTrusted(CommandSourceStack source, boolean trusted) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        areas.setSceneAssetTrusted(trusted);
        areas.sync();
        if (!persistSceneConfig(level, areas)) {
            source.sendFailure(Component.literal("可信模式已应用，但写入地图 JSON 失败"));
            return 0;
        }
        SceneAssetNetwork.sendManifestToAll(level);
        source.sendSuccess(() -> Component.literal(trusted
                ? "已启用可信快速模式：客户端跳过完整哈希和注册表复扫"
                : "已关闭可信快速模式：客户端恢复完整校验"), true);
        return 1;
    }

    private static boolean isRemoteTemplate(String value) {
        if (value.length() > 4096 || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        try {
            String sample = value.replace("{sha256}", "0".repeat(64)).replace("{map}", "map");
            java.net.URI uri = java.net.URI.create(sample);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean persistSceneConfig(ServerLevel level, AreasWorldComponent areas) {
        if (areas.mapName == null || areas.mapName.isBlank()) {
            return false;
        }
        try {
            MapManager.updateSceneAssetMetadata(
                    level, areas.mapName, areas.getSceneAssetHash(), areas.getSceneScroll());
            return true;
        } catch (Exception e) {
            io.wifi.starrailexpress.SRE.LOGGER.error("Failed to persist scene map settings", e);
            return false;
        }
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("场景快速向导："), false);
        source.sendSuccess(() -> Component.literal("1. /sre:scene wizard 打开“场景”页"), false);
        source.sendSuccess(() -> Component.literal("2. 新建场景并设置源区域、滚动轴与显示偏移"), false);
        source.sendSuccess(() -> Component.literal("3. 刷新投影，确认紫色目标框与 playArea 对齐"), false);
        source.sendSuccess(() -> Component.literal("4. 在场景管理器保存场景，并指定给当前地图"), false);
        source.sendSuccess(() -> Component.literal("5. 发布资产；地图 JSON 只保存场景 ID"), false);
        return 1;
    }

    private static AABB normalized(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    private static int status(CommandSourceStack source) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
        SceneAssetServer.Status status = SceneAssetServer.status(source.getLevel());
        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(areas.getSceneArea());
        source.sendSuccess(() -> Component.literal(String.format(
                "场景: id=%s configured=%s axis=%s offset=[%.2f %.2f %.2f] source=[%.0f %.0f %.0f -> %.0f %.0f %.0f] sections=%d chunks=%d size=%.1f MiB hash=%s remote=%s trusted=%s state=%s",
                areas.getSceneId().isBlank() ? "-" : areas.getSceneId(),
                areas.isSceneAreaConfigured(), areas.getSceneScroll(),
                areas.getSceneDisplayOffset().x, areas.getSceneDisplayOffset().y,
                areas.getSceneDisplayOffset().z,
                status.sourceArea().minX, status.sourceArea().minY, status.sourceArea().minZ,
                status.sourceArea().maxX, status.sourceArea().maxY, status.sourceArea().maxZ,
                bounds.sectionCount(), bounds.chunkCount(), status.size() / 1048576.0D,
                shortHash(status.hash()),
                areas.getSceneAssetRemoteUrl().isBlank() ? "off" : "on",
                areas.isSceneAssetTrusted(),
                status.publishing() ? "publishing" : status.dirty() ? "stale: " + status.dirtyReason() : "ready")),
                false);
        return 1;
    }

    private static int validate(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("正在完整校验场景资产..."), false);
        SceneAssetServer.validate(source.getLevel()).thenAccept(result -> source.getServer().execute(() -> {
            if (result.success()) {
                source.sendSuccess(() -> Component.literal(result.message() + " " + shortHash(result.currentHash())),
                        false);
            } else {
                source.sendFailure(Component.literal(result.message()));
            }
        }));
        return 1;
    }

    private static int publish(CommandSourceStack source, String mapName, boolean force) {
        if (mapName == null || mapName.isBlank()) {
            source.sendFailure(Component.literal("当前地图没有 mapName"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("sre.scene.publish.start"), false);
        SceneAssetServer.publish(source.getLevel(), mapName, force).whenComplete((result, error) ->
                source.getServer().execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable(
                                "sre.scene.publish.failed", rootMessage(error)));
                        return;
                    }
                    if (result.success()) {
                        source.sendSuccess(() -> Component.translatable(
                                "sre.scene.publish.complete", shortHash(result.hash()),
                                String.format(java.util.Locale.ROOT, "%.1f", result.size() / 1048576.0D)), true);
                    } else if ("场景资产正在发布".equals(result.message())) {
                        source.sendFailure(Component.translatable("sre.scene.publish.busy"));
                    } else {
                        source.sendFailure(Component.literal(result.message()));
                    }
                }));
        return 1;
    }

    private static int publishAndSave(CommandSourceStack source, String sceneId, boolean force) {
        ServerLevel level = source.getLevel();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        String mapName = currentMap(source);
        if (mapName == null || mapName.isBlank()) {
            source.sendFailure(Component.literal("请先载入或保存一张地图"));
            return 0;
        }

        SceneLibrary.Result saved = SceneLibrary.saveCurrent(level, sceneId, true);
        if (!saved.success()) {
            source.sendFailure(Component.literal(saved.message()));
            return 0;
        }
        try {
            MapManager.updateMapSceneReference(level, mapName, saved.id());
        } catch (Exception e) {
            source.sendFailure(Component.literal("场景已保存，但写入地图引用失败: " + e.getMessage()));
            return 0;
        }

        areas.sync();
        source.sendSuccess(() -> Component.translatable(
                "sre.scene.publish.saved", saved.id(), mapName), false);
        return publish(source, mapName, force);
    }

    private static int invalidate(CommandSourceStack source) {
        SceneAssetServer.invalidate(source.getLevel(), "管理员手动失效");
        SceneAssetNetwork.sendManifestToAll(source.getLevel());
        source.sendSuccess(() -> Component.literal("场景资产已标记为过期"), true);
        return 1;
    }

    private static String currentMap(CommandSourceStack source) {
        return AreasWorldComponent.KEY.get(source.getLevel()).mapName;
    }

    private static String shortHash(String hash) {
        return hash == null || hash.isBlank() ? "-" : hash.substring(0, Math.min(12, hash.length()));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
