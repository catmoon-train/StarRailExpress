package org.agmas.noellesroles.game.modes.echo.map.manor;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.MapManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.map.EchoActiveMap;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapGate;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapRepository;
import org.agmas.noellesroles.game.modes.echo.map.EchoRouteCheck;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Layout;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoPreflight;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 用代码生成回声货运测试地图「庄园」，并把它接到当前 Areas 地图上。
 *
 * <p>一次生成分成几个服务器 tick 切片推进，避免一次写十几万方块把主线程卡住：
 * <ol>
 * <li>加载模板区块，同时统计会被覆盖的旧方块；超过阈值且没有 {@code confirm} 就停下，
 * 不静默毁掉别人正在用的地图模板。</li>
 * <li>按布局整块覆盖模板区域（空气也写），写块只带 {@code UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE}，
 * 不触发邻居更新。</li>
 * <li>把模板区域整块复制到实战区，让管理员立刻可以走图、跑 {@code map validate}。</li>
 * <li>写侧文件、补 Areas 设置（关会议/初始物品/强制职业、声明 echo_freight）、保存地图配置。</li>
 * <li>跑 6–32 人预检，再用 {@link EchoRouteCheck} 复用开局路线准入的四阶段步行检查，
 * 在生成阶段就证明「关门进不去核心、开门进得去、撤离门开时到得了撤离区」。</li>
 * </ol>
 *
 * <p>生成器不注册新按键、不改普通地图加载流程；校验失败时只报错并保留已建好的方块，
 * 不会把地图标记成可用。
 */
public final class EchoManorGenerator {
    /** 200×200×22 ≈ 88 万格，切片预算比 96×96 时略降，避免单 tick 卡顿。 */
    private static final int BLOCKS_PER_TICK = 5000;
    private static final int CHUNKS_PER_TICK = 8;
    /** 模板里允许被覆盖的非空气方块上限；超过就要求 confirm。 */
    private static final int CONFLICT_LIMIT = 64;
    private static final int ROUTE_NODES_PER_TICK = 512;
    private static final int ROUTE_TICK_LIMIT = 2400;

    private enum Phase { LOAD_TEMPLATE, BUILD, LOAD_PASTE, PASTE, CONFIGURE, VALIDATE, DONE }

    private static final class Job {
        final CommandSourceStack source;
        final ServerLevel level;
        final String mapId;
        final boolean confirmed;
        final Layout layout;
        final EchoMapConfig config;
        final BlockPos templateOrigin;
        final BlockPos pasteOrigin;
        final int[] chunkX;
        final int[] chunkZ;
        Phase phase = Phase.LOAD_TEMPLATE;
        int index;
        int cursor;
        int written;
        int pasted;
        int conflicts;
        int routeTicks;
        int countIndex;
        EchoRouteCheck route;

        Job(CommandSourceStack source, ServerLevel level, String mapId, boolean confirmed, Layout layout,
            EchoMapConfig config, BlockPos templateOrigin, BlockPos pasteOrigin, int[] chunkX, int[] chunkZ) {
            this.source = source; this.level = level; this.mapId = mapId; this.confirmed = confirmed;
            this.layout = layout; this.config = config; this.templateOrigin = templateOrigin;
            this.pasteOrigin = pasteOrigin; this.chunkX = chunkX; this.chunkZ = chunkZ;
        }

        int total() { return EchoManorLayout.WIDTH * EchoManorLayout.HEIGHT * EchoManorLayout.DEPTH; }

        int chunkCount() { return chunkX.length * chunkZ.length; }

        int progress() {
            return switch (phase) {
                case LOAD_TEMPLATE, LOAD_PASTE -> cursor;
                case BUILD, PASTE -> index;
                default -> total();
            };
        }
    }

    private static Job job;

    private EchoManorGenerator() { }

    // ==================== 命令入口 ====================

    public static int start(CommandSourceStack source, String mapId, boolean confirmed) throws IOException {
        if (job != null) throw new IOException(Component.translatable("echo.manor.error.busy").getString());
        ServerLevel level = source.getLevel();
        if (SREGameWorldComponent.KEY.get(level).getGameStatus() != SREGameWorldComponent.GameStatus.INACTIVE)
            throw new IOException(Component.translatable("echo.manor.error.busy_game").getString());
        EchoMapRepository.requireMapId(mapId);
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank())
            throw new IOException(Component.translatable("echo.manor.error.no_map").getString());

        AABB template = areas.getResetTemplateArea();
        requireIntegral(template, "resetTemplateArea");
        int spanX = (int) Math.round(template.maxX - template.minX) + 1;
        int spanY = (int) Math.round(template.maxY - template.minY) + 1;
        int spanZ = (int) Math.round(template.maxZ - template.minZ) + 1;
        if (spanX < EchoManorLayout.WIDTH || spanY < EchoManorLayout.HEIGHT || spanZ < EchoManorLayout.DEPTH) {
            throw new IOException(Component.translatable("echo.manor.error.template_small",
                    EchoManorLayout.WIDTH, EchoManorLayout.HEIGHT, EchoManorLayout.DEPTH,
                    spanX, spanY, spanZ).getString());
        }
        EchoMapConfig.Vec offset = EchoPreflight.offset(areas);
        BlockPos templateMin = BlockPos.containing(template.minX, template.minY, template.minZ);
        BlockPos origin = new BlockPos(templateMin.getX() + (spanX - EchoManorLayout.WIDTH) / 2, templateMin.getY(),
                templateMin.getZ() + (spanZ - EchoManorLayout.DEPTH) / 2);
        BlockPos pasteOrigin = origin.offset((int) offset.x(), (int) offset.y(), (int) offset.z());
        BlockPos far = pasteOrigin.offset(EchoManorLayout.WIDTH - 1, EchoManorLayout.HEIGHT - 1, EchoManorLayout.DEPTH - 1);
        if (!level.getWorldBorder().isWithinBounds(origin) || !level.getWorldBorder().isWithinBounds(far))
            throw new IOException(Component.translatable("echo.manor.error.border").getString());

        Layout layout = EchoManorLayout.build();
        EchoMapConfig config = EchoManorPlan.toConfig(layout, mapId,
                new EchoManorPlan.Base(origin.getX(), origin.getY(), origin.getZ()));
        List<String> issues = EchoManorPlan.validate(config);
        if (!issues.isEmpty()) throw new IOException(Component.translatable("echo.manor.error.layout",
                String.join("; ", issues.subList(0, Math.min(6, issues.size())))).getString());

        int minCX = origin.getX() >> 4, maxCX = (origin.getX() + EchoManorLayout.WIDTH - 1) >> 4;
        int minCZ = origin.getZ() >> 4, maxCZ = (origin.getZ() + EchoManorLayout.DEPTH - 1) >> 4;
        int[] chunkX = new int[maxCX - minCX + 1], chunkZ = new int[maxCZ - minCZ + 1];
        for (int n = 0; n < chunkX.length; n++) chunkX[n] = minCX + n;
        for (int n = 0; n < chunkZ.length; n++) chunkZ[n] = minCZ + n;

        job = new Job(source, level, mapId, confirmed, layout, config, origin, pasteOrigin, chunkX, chunkZ);
        send(source, Component.translatable("echo.manor.started", mapId,
                origin.getX() + " " + origin.getY() + " " + origin.getZ(),
                pasteOrigin.getX() + " " + pasteOrigin.getY() + " " + pasteOrigin.getZ()));
        return 1;
    }

    public static int cancel(CommandSourceStack source) {
        if (job == null) {
            source.sendFailure(Component.translatable("echo.manor.error.no_job"));
            return 0;
        }
        job = null;
        send(source, Component.translatable("echo.manor.cancelled"));
        return 1;
    }

    public static int status(CommandSourceStack source) {
        Job current = job;
        if (current == null) {
            source.sendFailure(Component.translatable("echo.manor.error.no_job"));
            return 0;
        }
        send(source, Component.translatable("echo.manor.status", current.mapId,
                current.phase.name().toLowerCase(Locale.ROOT), current.progress(), current.total(),
                current.written, current.pasted));
        return 1;
    }

    // ==================== tick 推进 ====================

    public static void tick(MinecraftServer server) {
        Job current = job;
        if (current == null) return;
        try {
            if (SREGameWorldComponent.KEY.get(current.level).getGameStatus() != SREGameWorldComponent.GameStatus.INACTIVE) {
                fail(current, Component.translatable("echo.manor.error.started_game"));
                return;
            }
            switch (current.phase) {
                case LOAD_TEMPLATE -> loadChunks(current, true);
                case BUILD -> build(current);
                case LOAD_PASTE -> loadChunks(current, false);
                case PASTE -> paste(current);
                case CONFIGURE -> configure(current);
                case VALIDATE -> validate(current);
                default -> job = null;
            }
        } catch (IOException | RuntimeException failure) {
            Noellesroles.LOGGER.error("回声庄园生成失败: map={}", current.mapId, failure);
            fail(current, Component.translatable("echo.manor.error.failed", String.valueOf(failure.getMessage())));
        }
    }

    private static void loadChunks(Job current, boolean template) {
        int budget = CHUNKS_PER_TICK;
        while (budget-- > 0 && current.cursor < current.chunkCount()) {
            int n = current.cursor++;
            int cx = current.chunkX[n % current.chunkX.length];
            int cz = current.chunkZ[n / current.chunkX.length];
            current.level.getChunk(cx, cz);
            if (template) countConflicts(current, cx, cz);
        }
        if (current.cursor < current.chunkCount()) return;
        current.cursor = 0;
        if (template) {
            if (current.conflicts > CONFLICT_LIMIT && !current.confirmed) {
                String command = "/sre:echo map generate " + current.mapId + " confirm";
                send(current.source, Component.translatable("echo.manor.conflict", current.conflicts, command)
                        .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
                job = null;
                return;
            }
            current.phase = Phase.BUILD;
        } else {
            current.phase = Phase.PASTE;
        }
    }

    /** 统计模板里会被覆盖的非空气方块：只用于确认提示，不改世界。 */
    private static void countConflicts(Job current, int chunkX, int chunkZ) {
        int originX = current.templateOrigin.getX(), originZ = current.templateOrigin.getZ();
        int x0 = Math.max(originX, chunkX << 4);
        int x1 = Math.min(originX + EchoManorLayout.WIDTH - 1, (chunkX << 4) + 15);
        int z0 = Math.max(originZ, chunkZ << 4);
        int z1 = Math.min(originZ + EchoManorLayout.DEPTH - 1, (chunkZ << 4) + 15);
        if (x0 > x1 || z0 > z1) return;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y < EchoManorLayout.HEIGHT; y++) {
                    BlockPos pos = new BlockPos(x, current.templateOrigin.getY() + y, z);
                    BlockState present = current.level.getBlockState(pos);
                    if (present.isAir()) continue;
                    int lx = x - originX, lz = z - originZ;
                    if (!present.equals(EchoManorScene.state(current.layout, lx, y, lz))) current.conflicts++;
                }
            }
        }
    }

    private static void build(Job current) {
        int budget = BLOCKS_PER_TICK;
        while (budget-- > 0 && current.index < current.total()) {
            int n = current.index++;
            int z = n % EchoManorLayout.DEPTH;
            int rest = n / EchoManorLayout.DEPTH;
            int x = rest % EchoManorLayout.WIDTH;
            int y = rest / EchoManorLayout.WIDTH;
            BlockPos pos = current.templateOrigin.offset(x, y, z);
            BlockState desired = current.layout.machineAt(x,y,z)!=null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : EchoManorScene.state(current.layout, x, y, z);
            if (current.level.getBlockState(pos).equals(desired)) continue;
            org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.mutate(() ->
                    current.level.setBlock(pos, desired, EchoManorScene.WRITE_FLAGS));
            current.level.getLightEngine().checkBlock(pos);
            current.written++;
        }
        if (current.index < current.total()) return;
        writeMachines(current,current.templateOrigin,false);
        current.index = 0;
        current.phase = Phase.LOAD_PASTE;
        send(current.source, Component.translatable("echo.manor.built", current.written));
    }

    private static void paste(Job current) {
        int budget = BLOCKS_PER_TICK;
        while (budget-- > 0 && current.index < current.total()) {
            int n = current.index++;
            int z = n % EchoManorLayout.DEPTH;
            int rest = n / EchoManorLayout.DEPTH;
            int x = rest % EchoManorLayout.WIDTH;
            int y = rest / EchoManorLayout.WIDTH;
            // 预览、模板与实战区使用同一映射（包括所有 PART），不从可能被编辑的半成品反读。
            BlockState state = current.layout.machineAt(x,y,z)!=null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : EchoManorScene.state(current.layout, x, y, z);
            BlockPos to = current.pasteOrigin.offset(x, y, z);
            if (current.level.getBlockState(to).equals(state)) continue;
            org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.mutate(() ->
                    current.level.setBlock(to, state, EchoManorScene.WRITE_FLAGS));
            current.level.getLightEngine().checkBlock(to);
            current.pasted++;
        }
        if (current.index < current.total()) return;
        writeMachines(current,current.pasteOrigin,true);
        current.index = 0;
        current.phase = Phase.CONFIGURE;
        ejectPlayers(current);
        send(current.source, Component.translatable("echo.manor.pasted", current.pasted));
    }

    /** 地形全部完成后同步写齐机器，既不连拆旧结构，也不让分帧半成品被定时自检清理。 */
    private static void writeMachines(Job current,BlockPos origin,boolean paste) {
        org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.mutate(() -> {
            for(var marker:current.layout.markers()) {
                var kind=EchoManorScene.machineKind(current.layout,marker.x(),marker.y(),marker.z());
                var facing=EchoManorScene.machineFacing(current.layout,marker.x(),marker.y(),marker.z());
                for(var cell:org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.cells(kind)) {
                    var offset=org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.offset(cell.part(),facing);
                    int x=marker.x()+offset.getX(),y=marker.y()+offset.getY(),z=marker.z()+offset.getZ();
                    var pos=origin.offset(x,y,z);
                    var desired=EchoManorScene.state(current.layout,x,y,z);
                    if(current.level.getBlockState(pos).equals(desired)) continue;
                    current.level.setBlock(pos,desired,EchoManorScene.WRITE_FLAGS);
                    current.level.getLightEngine().checkBlock(pos);
                    if(paste) current.pasted++; else current.written++;
                }
            }
        });
    }
    /** 粘贴可能把方块写进站在实战区里的玩家；先把他们抬到地图正中上方，避免被埋。 */
    private static void ejectPlayers(Job current) {
        double x0 = current.pasteOrigin.getX(), y0 = current.pasteOrigin.getY(), z0 = current.pasteOrigin.getZ();
        double x1 = x0 + EchoManorLayout.WIDTH, y1 = y0 + EchoManorLayout.HEIGHT, z1 = z0 + EchoManorLayout.DEPTH;
        double centerX = x0 + EchoManorLayout.WIDTH / 2.0D, centerZ = z0 + EchoManorLayout.DEPTH / 2.0D;
        for (net.minecraft.server.level.ServerPlayer player : current.level.players()) {
            if (player.isSpectator()) continue;
            var pos = player.position();
            if (pos.x < x0 || pos.x > x1 || pos.y < y0 || pos.y > y1 || pos.z < z0 || pos.z > z1) continue;
            player.teleportTo(current.level, centerX, y1 + 2, centerZ, player.getYRot(), player.getXRot());
        }
    }


    private static void configure(Job current) throws IOException {
        ServerLevel level = current.level;
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        String digest = EchoMapRepository.digest(worldRoot, current.mapId);
        EchoMapRepository.save(worldRoot, current.config, digest);
        EchoActiveMap.set(worldRoot, current.mapId, current.source.getTextName());

        var settings = areas.areasSettings;
        settings.initialItems.clear();
        settings.meetingEnabled = false;
        settings.bellMeetingEnabled = false;
        settings.minigameQuestEnabled = false;
        settings.planeCrashEventEnabled = false;
        settings.enableSceneTask.clear();
        settings.forcedRoles.clear();
        settings.forcedModifiers.clear();
        settings.enabledRoles.clear();
        settings.enabledModifiers.clear();
        settings.noReset = false;
        EchoMapGate.setSupported(areas, true);
        extendPlayArea(areas, current.pasteOrigin);
        areas.sync();
        if (!MapManager.saveCurrentMap(level, areas.mapName, true))
            throw new IOException(Component.translatable("echo.manor.error.save", areas.mapName).getString());
        current.phase = Phase.VALIDATE;
        current.countIndex = EchoRosterPolicy.MIN_PLAYERS;
        send(current.source, Component.translatable("echo.manor.saved", current.mapId, areas.mapName));
    }

    /** 实战区必须包含整座庄园；不够就并入，而不是让预检在开局时才报错。 */
    private static void extendPlayArea(AreasWorldComponent areas, BlockPos origin) {
        AABB play = areas.getPlayArea();
        double minX = Math.min(play.minX, origin.getX() - 1);
        double minY = Math.min(play.minY, origin.getY());
        double minZ = Math.min(play.minZ, origin.getZ() - 1);
        double maxX = Math.max(play.maxX, origin.getX() + EchoManorLayout.WIDTH + 1);
        double maxY = Math.max(play.maxY, origin.getY() + EchoManorLayout.HEIGHT + 2);
        double maxZ = Math.max(play.maxZ, origin.getZ() + EchoManorLayout.DEPTH + 1);
        if (minX != play.minX || minY != play.minY || minZ != play.minZ
                || maxX != play.maxX || maxY != play.maxY || maxZ != play.maxZ) {
            areas.setPlayArea(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static void validate(Job current) throws IOException {
        if (current.countIndex <= EchoRosterPolicy.MAX_PLAYERS) {
            var check = EchoPreflight.check(current.level, current.countIndex, false);
            if (!check.valid()) {
                check.errors().stream().limit(6).forEach(error ->
                        send(current.source, Component.translatable("echo.manor.error.detail", error.getString())));
                fail(current, Component.translatable("echo.manor.error.preflight", current.countIndex));
                return;
            }
            if (current.countIndex == 6) current.route = new EchoRouteCheck(current.level, check.prepared().map());
            current.countIndex++;
            return;
        }
        if (current.route == null) {
            fail(current, Component.translatable("echo.manor.error.preflight", 6));
            return;
        }
        current.routeTicks++;
        var status = current.route.advance(ROUTE_NODES_PER_TICK);
        if (status == EchoRouteCheck.Status.RUNNING) {
            if (current.routeTicks <= ROUTE_TICK_LIMIT) return;
            fail(current, Component.translatable("echo.manor.route_timeout"));
            return;
        }
        if (status == EchoRouteCheck.Status.PASSED) {
            send(current.source, Component.translatable("echo.manor.route_passed", current.route.visited()));
        } else {
            String issues = String.join("; ", current.route.issues().stream().limit(8).toList());
            send(current.source, Component.translatable(status == EchoRouteCheck.Status.MANUAL
                    ? "echo.manor.route_manual" : "echo.manor.route_failed", issues));
        }
        send(current.source, Component.translatable("echo.manor.done", current.mapId));
        current.phase = Phase.DONE;
        job = null;
    }

    // ==================== 工具 ====================

    private static void requireIntegral(AABB box, String name) throws IOException {
        if (box.minX != Math.rint(box.minX) || box.minY != Math.rint(box.minY) || box.minZ != Math.rint(box.minZ)
                || box.maxX != Math.rint(box.maxX) || box.maxY != Math.rint(box.maxY) || box.maxZ != Math.rint(box.maxZ)) {
            throw new IOException(Component.translatable("echo.manor.error.nonintegral", name).getString());
        }
        if (box.maxX <= box.minX || box.maxY <= box.minY || box.maxZ <= box.minZ) {
            throw new IOException(Component.translatable("echo.manor.error.empty_area", name).getString());
        }
    }

    private static void fail(Job current, Component reason) {
        job = null;
        send(current.source, Component.translatable("echo.manor.failed", reason));
    }

    private static void send(CommandSourceStack source, Component message) {
        try {
            source.sendSuccess(() -> message, false);
        } catch (RuntimeException ignored) {
            // 发起者可能已经断线或换维度；任务结果仍然写在服务器日志里。
        }
    }
}
