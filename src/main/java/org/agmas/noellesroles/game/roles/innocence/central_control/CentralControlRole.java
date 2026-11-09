package org.agmas.noellesroles.game.roles.innocence.central_control;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.CentralControlRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREMonitorWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.content.block.CameraBlock;
import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 中控员：平民阵营的监控改造职业。
 *
 * <p>
 * 本职业的全部规则都集中在这个类里，网络包 / 客户端 HUD 只做薄转发：
 * <ul>
 * <li>商店：{@link #getShopEntries()}，可花 100 金币购买远程监控终端（与保安商店一致）；</li>
 * <li>任务点透视：摄像头与控制台对中控员可见，见 {@code CameraBlock#shouldRenderTaskInstinct}；</li>
 * <li>一技能「动态模块」：{@link #installDynamicModule(ServerPlayer)}，冷却 20 秒；</li>
 * <li>二技能「红外线扫描仪」：{@link #installInfraredScanner(ServerPlayer)}，150 金币，冷却 90 秒；</li>
 * <li>三技能「激光模块」：{@link #installLaserModule(ServerPlayer)}，200 金币，一局仅 1 次；</li>
 * <li>激光发射：{@link #fireLaser(ServerPlayer, BlockPos, float, float)}，由监控页面的技能键触发；</li>
 * <li>红外线扫描的区域发光：{@link #tickInfraredScan(ServerLevel)}。</li>
 * </ul>
 *
 * <p>
 * 三个改造技能的作用范围都是「视野可见的 15 格锥形范围」，判定见
 * {@link #collectTargetCameras(ServerPlayer)}；同一台摄像头可以叠加多个模块。
 * 模块状态存在 {@link CameraBlockEntity} 上（方块状态），随每局开始 / 结束由
 * {@code CameraBlock#onResetBlockEntity} 统一重置。
 */
public class CentralControlRole extends NormalRole {

    // ==================== 数值 ====================

    /** 改造技能的作用半径（格）。 */
    public static final double TARGET_RADIUS = 15.0D;
    /** 锥形半角的余弦值：cos 30° ≈ 0.866，即左右各 30° 的锥形。 */
    public static final double TARGET_CONE_COS = 0.866D;

    /** 一技能冷却（秒）。 */
    public static final int DYNAMIC_MODULE_COOLDOWN_SECONDS = 20;
    /** 二技能冷却（秒）。 */
    public static final int INFRARED_SCANNER_COOLDOWN_SECONDS = 90;
    /** 二技能金币消耗。 */
    public static final int INFRARED_SCANNER_PRICE = 150;
    /** 三技能金币消耗。 */
    public static final int LASER_MODULE_PRICE = 200;
    /** 远程监控终端价格（与保安商店一致）。 */
    public static final int MONITORING_TERMINAL_PRICE = 100;

    /** 激光命中后的控制（傀儡）与发光时长（秒）。 */
    public static final int LASER_CONTROL_SECONDS = 5;
    /** 激光最大射程（格）。 */
    public static final double LASER_MAX_DISTANCE = 64.0D;
    /**
     * 服务端允许「操作这台摄像头」的最大距离（格）。
     * <p>
     * 只为挡住客户端伪造坐标，所以放得很松；调大不会影响正常玩法。
     */
    public static final double LASER_MAX_OPERATE_DISTANCE = 32.0D;

    /** 红外线扫描仪的作用半径（格）：以摄像头为锥顶的圆锥。 */
    public static final double INFRARED_RANGE = 15.0D;
    /** 红外线扫描仪给玩家刷新发光的间隔（tick）。 */
    public static final int INFRARED_GLOW_REFRESH_TICKS = 10;
    /** 单次施加发光的时长（tick），比刷新间隔长一点避免闪烁。 */
    public static final int INFRARED_GLOW_DURATION_TICKS = 30;

    /** 红外扫描仪生效时，监控画面的夜视亮度（伽马值覆盖，越大越亮）。 */
    public static final float INFRARED_GAMMA = 1.0F;

    // ==================== 观察者名册（红外线扫描仪用） ====================
    public CentralControlRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 一技能：动态模块 ====================

    /**
     * 一技能：为视野可见、15 格锥形范围内的摄像头安装「动态模块」，
     * 使其监控画面可以上下左右 / 斜向转动视角（不改变摄像头位置）。
     *
     * @return true 表示至少改造了一台摄像头（返回 true 才会进入冷却）
     */
    public static boolean installDynamicModule(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return false;
        }
        if (isMonitorBroken(level)) {
            return fail(player, "message.noellesroles.central_control.monitor_broken");
        }
        List<BlockPos> targets = collectTargetCameras(player);
        if (targets.isEmpty()) {
            return fail(player, "message.noellesroles.central_control.no_camera");
        }
        int changed = 0;
        for (BlockPos pos : targets) {
            if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
                continue;
            }
            if (camera.isBroken() || camera.hasDynamicModule()) {
                continue;
            }
            camera.installDynamicModule();
            recordInstall(player, pos, "dynamic_module");
            changed++;
        }
        if (changed == 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.dynamic_module.already")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        playInstallSound(level, player, targets);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.central_control.dynamic_module.success", changed)
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    // ==================== 二技能：红外线扫描仪 ====================

    /**
     * 二技能：花 150 金币为视野可见、15 格锥形范围内的摄像头安装「红外线扫描仪」。
     *
     * <p>
     * 装好后：从监控台看这台摄像头自带夜视；走进该摄像头锥形监控区域（以摄像头为锥顶、
     * 扫描到地板截止）的玩家会自动获得发光效果。
     *
     * @return true 表示至少改造了一台摄像头且金币已扣（返回 true 才会进入冷却）
     */
    public static boolean installInfraredScanner(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return false;
        }
        if (isMonitorBroken(level)) {
            return fail(player, "message.noellesroles.central_control.monitor_broken");
        }
        List<BlockPos> targets = collectTargetCameras(player);
        if (targets.isEmpty()) {
            return fail(player, "message.noellesroles.central_control.no_camera");
        }
        // 先筛出真正能改造的摄像头，避免白白扣钱
        List<BlockPos> installable = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
                continue;
            }
            if (camera.isBroken() || camera.hasInfraredScanner()) {
                continue;
            }
            installable.add(pos);
        }
        if (installable.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.infrared_scanner.already")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        if (!MoneyUtils.cost(player, INFRARED_SCANNER_PRICE)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(player, INFRARED_SCANNER_PRICE);
            return false;
        }
        for (BlockPos pos : installable) {
            if (level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
                camera.installInfraredScanner();
                recordInstall(player, pos, "infrared_scanner");
            }
        }
        playInstallSound(level, player, installable);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.central_control.infrared_scanner.success",
                        INFRARED_SCANNER_PRICE, installable.size())
                        .withStyle(ChatFormatting.AQUA),
                true);
        return true;
    }

    // ==================== 三技能：激光模块 ====================

    /**
     * 三技能：花 200 金币为视野可见、15 格锥形范围内的摄像头安装「激光模块」（一局仅 1 次）。
     *
     * <p>
     * 装好后，对应摄像头在监控页面右下角会显示「已装填激光模块」，
     * 玩家在这一页按技能键就能朝画面准星方向射出激光。
     *
     * @return true 表示至少改造了一台摄像头且金币已扣（返回 true 才会消耗次数）
     */
    public static boolean installLaserModule(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        Level level = player.level();
        if (level.isClientSide()) {
            return false;
        }
        CentralControlRoleData data = RoleData.getNullable(CentralControlRoleData.class, player);
        if (data == null) {
            return false;
        }
        if (data.laserModuleUsed) {
            return fail(player, "message.noellesroles.central_control.laser_module.used");
        }
        if (isMonitorBroken(level)) {
            return fail(player, "message.noellesroles.central_control.monitor_broken");
        }
        List<BlockPos> targets = collectTargetCameras(player);
        if (targets.isEmpty()) {
            return fail(player, "message.noellesroles.central_control.no_camera");
        }
        List<BlockPos> installable = new ArrayList<>();
        for (BlockPos pos : targets) {
            if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity camera)) {
                continue;
            }
            if (camera.isBroken() || camera.hasLaserModule()) {
                continue;
            }
            installable.add(pos);
        }
        if (installable.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.laser_module.already")
                            .withStyle(ChatFormatting.YELLOW),
                    true);
            return false;
        }
        if (!MoneyUtils.cost(player, LASER_MODULE_PRICE)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(player, LASER_MODULE_PRICE);
            return false;
        }
        for (BlockPos pos : installable) {
            if (level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
                camera.installLaserModule();
                recordInstall(player, pos, "laser_module");
            }
        }
        data.laserModuleUsed = true;
        data.sync();
        playInstallSound(level, player, installable);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.central_control.laser_module.success",
                        LASER_MODULE_PRICE, installable.size())
                        .withStyle(ChatFormatting.RED),
                true);
        return true;
    }

    // ==================== 激光发射 ====================

    /**
     * 在监控页面里发射当前摄像头的激光。
     *
     * <p>
     * 客户端只上报「哪台摄像头 + 画面准星方向」，服务端重新校验职业、模块、冷却、监控失灵，
     * 再自己做射线检测（防作弊 + 状态以服务端为准）。
     *
     * @param cameraPos 摄像头方块位置
     * @param lookYaw   监控画面的水平朝向（度）
     * @param lookPitch 监控画面的俯仰（度）
     * @return true 表示成功射出
     */
    public static boolean fireLaser(ServerPlayer player, BlockPos cameraPos, float lookYaw, float lookPitch) {
        if (player == null || cameraPos == null || !isCentralControl(player)) {
            return false;
        }
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (isMonitorBroken(level)) {
            return fail(player, "message.noellesroles.central_control.laser.failed_monitor_broken");
        }
        if (!(level.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera)) {
            return false;
        }
        // 防伪造：只能操作自己身边（监控室）的那台摄像头。
        // 放得很松（32 格），因为监控台与摄像头的实际距离由地图决定；重点是挡住
        // 「拿一个全图任意已改造摄像头的位置直接开火」这种作弊。
        if (player.distanceToSqr(Vec3.atCenterOf(cameraPos)) > LASER_MAX_OPERATE_DISTANCE
                * LASER_MAX_OPERATE_DISTANCE) {
            return false;
        }
        if (!camera.hasLaserModule()) {
            return fail(player, "message.noellesroles.central_control.laser.no_module");
        }
        if (camera.isBroken()) {
            return fail(player, "message.noellesroles.central_control.laser.broken");
        }
        if (camera.getLaserCooldown() > 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.laser.cooldown",
                            (camera.getLaserCooldown() + 19) / 20)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        Vec3 origin = cameraOrigin(cameraPos);
        Vec3 direction = directionFromAngles(lookYaw, lookPitch);
        Vec3 end = origin.add(direction.scale(LASER_MAX_DISTANCE));

        // 方块遮挡：激光打在墙上就停在墙上
        BlockHitResult blockHit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = LASER_MAX_DISTANCE;
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            limit = Math.min(limit, origin.distanceTo(blockHit.getLocation()));
            end = blockHit.getLocation();
        }

        // 沿射线找最近的存活玩家
        ServerPlayer hit = null;
        double best = limit;
        AABB searchBox = new AABB(origin, end).inflate(1.0D);
        for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, searchBox,
                p -> p != player && !p.isSpectator() && GameUtils.isPlayerAliveAndSurvival(p))) {
            var optional = target.getBoundingBox().inflate(0.35D).clip(origin, end);
            if (optional.isEmpty()) {
                continue;
            }
            double distance = origin.distanceTo(optional.get());
            if (distance <= best) {
                best = distance;
                hit = target;
            }
        }

        camera.markLaserFired();
        if (hit != null) {
            end = origin.add(direction.scale(best));
            applyLaserEffect(player, hit);
        }
        renderLaser(level, origin, end);
        if (hit == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.laser.miss")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
        recordLaserFire(player, hit);
        return true;
    }

    /**
     * 激光命中效果：5 秒控制 + 5 秒发光；
     * 若命中处于疯狂模式的玩家，则立即结束其疯狂模式，且不施加控制与发光。
     */
    private static void applyLaserEffect(ServerPlayer shooter, ServerPlayer target) {
        SREPlayerPsychoComponent psycho = SREPlayerPsychoComponent.KEY.get(target);
        if (psycho.psychoTicks > 0) {
            psycho.stopPsychoAndSync();
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.laser.psycho_ended")
                            .withStyle(ChatFormatting.GOLD),
                    true);
            shooter.displayClientMessage(
                    Component.translatable("message.noellesroles.central_control.laser.psycho_ended_shooter",
                            target.getName()).withStyle(ChatFormatting.GOLD),
                    true);
            return;
        }
        int duration = LASER_CONTROL_SECONDS * 20;
        // 「控制」沿用菌菇学者剧毒蘑菇那一套：禁移动 / 禁转向 / 禁使用 / 禁技能 + 傀儡游走
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, true, false));
        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, duration, 0, false, true, false));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, false, true, false));
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, duration, 0, false, true, false));
        target.addEffect(new MobEffectInstance(ModEffects.PUPPET_WANDER, duration, 0, false, true, false));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, true, false));
        target.displayClientMessage(
                Component.translatable("message.noellesroles.central_control.laser.controlled",
                        LASER_CONTROL_SECONDS).withStyle(ChatFormatting.RED),
                true);
        shooter.displayClientMessage(
                Component.translatable("message.noellesroles.central_control.laser.hit",
                        target.getName(), LASER_CONTROL_SECONDS).withStyle(ChatFormatting.RED),
                true);
    }

    /**
     * 红色激光粒子：服务端只发一个自定义形状包，线段由客户端按 id 逐点生成。
     */
    private static void renderLaser(ServerLevel level, Vec3 from, Vec3 to) {
        ParticleFx.sendCustom(level, LASER_BEAM_FX, from, 6,
                (float) to.x, (float) to.y, (float) to.z);
    }

    /** 激光粒子的自定义形状 id（客户端注册处理器时用同一个）。 */
    public static final ResourceLocation LASER_BEAM_FX = SRE.id("central_control_laser_beam");

    // ==================== 红外线扫描仪：监控区域发光 ====================

    /**
     * 登记「谁正在看哪台摄像头」，红外线扫描仪据此决定要不要点亮。
     * <p>
     * 只有摄像头当前真的被人从监控页面观看时，它的扫描仪才会生效；
     * 同时记录观看方向（yaw / pitch），让发光区域跟着相机视角走。
     */
    private record Viewer(BlockPos cameraPos, float lookYaw, float lookPitch) {
    }

    /** 观察者名册：玩家 UUID → 正在看的摄像头与视角。只在服务端用。 */
    private static final Map<UUID, Viewer> VIEWERS = new ConcurrentHashMap<>();

    /** 刚登记 / 刚换镜头的摄像头：进入那一刻要立刻点亮，不必等刷新周期。 */
    private static final Set<BlockPos> NEWLY_VIEWED = ConcurrentHashMap.newKeySet();

    /**
     * 玩家开始 / 切换观看某台摄像头时登记（由 {@code SecurityMonitorBlock} 调用）。
     * 摄像头为 null 时等同于退出观看。
     */
    public static void onCameraViewed(ServerPlayer player, BlockPos cameraPos) {
        if (player == null) {
            return;
        }
        if (cameraPos == null) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        Viewer old = VIEWERS.get(player.getUUID());
        float yaw = old != null && cameraPos.equals(old.cameraPos()) ? old.lookYaw() : 0.0F;
        float pitch = old != null && cameraPos.equals(old.cameraPos()) ? old.lookPitch() : 0.0F;
        VIEWERS.put(player.getUUID(), new Viewer(cameraPos.immutable(), yaw, pitch));
        NEWLY_VIEWED.add(cameraPos.immutable());
    }

    /** 玩家退出监控时注销（由 {@code SecurityMonitorBlock} 调用）。 */
    public static void onCameraViewStopped(ServerPlayer player) {
        if (player != null) {
            VIEWERS.remove(player.getUUID());
        }
    }

    /**
     * 客户端在监控页面按技能键时顺带上报当前视角，用来让红外发光跟着镜头走。
     * 只对正在看这台摄像头的玩家生效。
     */
    public static void updateViewerLook(ServerPlayer player, BlockPos cameraPos, float lookYaw, float lookPitch) {
        if (player == null || cameraPos == null) {
            return;
        }
        Viewer old = VIEWERS.get(player.getUUID());
        if (old == null || !old.cameraPos().equals(cameraPos)) {
            return;
        }
        VIEWERS.put(player.getUUID(), new Viewer(old.cameraPos(), lookYaw, lookPitch));
    }

    /**
     * 服务端每 tick 调一次（由 {@link CentralControlTicker} 统一注册，不是每个玩家注册一次）：
     * 只让「正在被观看」且装了红外线扫描仪的摄像头点亮其监控区域内的玩家。
     *
     * <p>
     * 发光方向跟随该摄像头的画面视角（动态模块转出来的 yaw / pitch），
     * 因此玩家在监控画面里看到的锥形区域，与真正被点亮的区域是同一块。
     * 没有人看这台摄像头时不点亮；监控失灵期间全部失效。
     */
    public static void tickInfraredScan(ServerLevel level) {
        // 先剔除已经不成立的观察者：退出监控、切维度、死亡、摄像头没了
        pruneViewers(level);
        if (VIEWERS.isEmpty() || isMonitorBroken(level)) {
            return;
        }
        boolean refresh = level.getGameTime() % INFRARED_GLOW_REFRESH_TICKS == 0L;
        // 同一台摄像头可能被多人同时观看：每台只需点亮一次
        Set<BlockPos> handled = new HashSet<>();
        for (Viewer viewer : VIEWERS.values()) {
            BlockPos cameraPos = viewer.cameraPos();
            if (!handled.add(cameraPos)) {
                continue;
            }
            if (!(level.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera)
                    || !camera.hasInfraredScanner() || camera.isBroken()) {
                continue;
            }
            // 朝向按「观看方向」算：装了动态模块就是镜头当前朝向，没装则退回摄像头固定朝向。
            // 注意 Viewer 存的是相对摄像头基础朝向的偏移量，必须换成绝对 yaw 才能喂给 directionFromAngles。
            BlockState cameraState = level.getBlockState(cameraPos);
            Vec3 forward = camera.hasDynamicModule()
                    ? directionFromAngles(baseYawOf(cameraState) + viewer.lookYaw(), viewer.lookPitch())
                    : directionFromFacing(cameraState);
            glowPlayersInCone(level, cameraPos, forward, refresh || isNewlyViewed(cameraPos));
        }
        NEWLY_VIEWED.clear();
    }

    /** 这台摄像头本 tick 是否刚被登记 / 刚换过镜头（要立刻点亮一次）。 */
    private static boolean isNewlyViewed(BlockPos pos) {
        return NEWLY_VIEWED.contains(pos);
    }

    /** 剔除不成立的观察者（玩家已退出/死亡/换维度，或摄像头方块已不存在）。 */
    private static void pruneViewers(ServerLevel level) {
        if (VIEWERS.isEmpty()) {
            return;
        }
        VIEWERS.entrySet().removeIf(entry -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
                return true;
            }
            if (!player.serverLevel().dimension().equals(level.dimension())) {
                return true;
            }
            return !(level.getBlockEntity(entry.getValue().cameraPos()) instanceof CameraBlockEntity);
        });
    }

    /**
     * 把锥形监控区域内的存活玩家点亮。
     *
     * @param force true 时无视刷新间隔立刻施加一次（进入镜头 / 切镜头的瞬间）
     */
    private static void glowPlayersInCone(ServerLevel level, BlockPos cameraPos, Vec3 forward, boolean force) {
        if (!force) {
            return;
        }
        Vec3 origin = cameraOrigin(cameraPos);
        for (ServerPlayer target : level.players()) {
            if (target.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (!isInsideCameraCone(level, origin, forward, target)) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, INFRARED_GLOW_DURATION_TICKS,
                    0, false, false, false));
        }
    }
    /**
     * 玩家是否落在摄像头的锥形监控区域内。
     *
     * <p>
     * 以摄像头为锥顶沿朝向张开；摄像头到玩家之间若被实心方块挡住（墙、地板），
     * 视为不在监控区域内，因此地板下面的玩家不会被点亮。
     */
    public static boolean isInsideCameraCone(Level level, Vec3 origin, Vec3 forward, Player target) {
        Vec3 eye = target.getEyePosition();
        Vec3 toTarget = eye.subtract(origin);
        double distance = toTarget.length();
        if (distance <= 0.001D || distance > INFRARED_RANGE) {
            return false;
        }
        if (toTarget.normalize().dot(forward) < TARGET_CONE_COS) {
            return false;
        }
        HitResult hit = level.clip(new ClipContext(origin, eye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * 摄像头被重置（模块清空）时由 {@code CameraBlock#onResetBlockEntity} 调用。
     * 模块标记清空后，观察者会在下一 tick 被 {@link #pruneViewers} 自动剔除，这里无需额外处理；
     * 保留这个入口是为了让重置链路显式通知一次，也顺便清掉「刚装好」的标记。
     */
    public static void onCameraReset(Level level, BlockPos pos) {
        NEWLY_VIEWED.remove(pos);
    }

    // ==================== 锥形范围选摄像头 ====================

    /**
     * 收集「视线上、15 格内、锥形范围内」的摄像头位置。
     *
     * <p>
     * 要求真的看得见（射线不被方块挡住），所以必须把摄像头转进视野里才改造得到。
     */
    public static List<BlockPos> collectTargetCameras(ServerPlayer player) {
        List<BlockPos> result = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int radiusChunks = (int) Math.ceil(TARGET_RADIUS / 16.0D) + 1;
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                if (chunk == null) {
                    continue;
                }
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof CameraBlockEntity)) {
                        continue;
                    }
                    BlockPos pos = entry.getKey();
                    if (!level.getBlockState(pos).is(TMMBlocks.CAMERA)) {
                        continue;
                    }
                    Vec3 center = Vec3.atCenterOf(pos);
                    Vec3 toCamera = center.subtract(eye);
                    double distance = toCamera.length();
                    if (distance <= 0.001D || distance > TARGET_RADIUS) {
                        continue;
                    }
                    if (toCamera.normalize().dot(look) < TARGET_CONE_COS) {
                        continue;
                    }
                    // 视线检查：被墙挡住的摄像头不参与改造
                    HitResult hit = level.clip(new ClipContext(eye, center,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (hit.getType() == HitResult.Type.BLOCK
                            && !((BlockHitResult) hit).getBlockPos().equals(pos)) {
                        continue;
                    }
                    result.add(pos);
                }
            }
        }
        return result;
    }

    // ==================== 监控画面相关（双端可用） ====================

    /** 监控画面里相机所在的位置（与 {@code SecurityCameraClientState} 保持一致）。 */
    public static Vec3 cameraOrigin(BlockPos cameraPos) {
        return Vec3.atCenterOf(cameraPos).add(0.0D, -1.2D, 0.0D);
    }

    /** 摄像头方块朝向对应的水平角度（与监控画面的基础角度一致）。 */
    public static float baseYawOf(BlockState state) {
        if (state.getBlock() instanceof CameraBlock && state.hasProperty(CameraBlock.FACING)) {
            return switch (state.getValue(CameraBlock.FACING)) {
                case SOUTH -> 180.0F;
                case EAST -> 90.0F;
                case WEST -> -90.0F;
                default -> 0.0F;
            };
        }
        return 0.0F;
    }

    /** 摄像头朝向的单位向量。 */
    public static Vec3 directionFromFacing(BlockState state) {
        return directionFromAngles(baseYawOf(state), 0.0F);
    }

    /** yaw / pitch（度）转单位向量，与 Minecraft 的视角约定一致。 */
    public static Vec3 directionFromAngles(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /**
     * 当前监控画面是否带红外夜视（只有真正在看那台摄像头时才该生效）。
     */
    public static boolean hasNightVision(Level level, BlockPos cameraPos) {
        if (level == null || cameraPos == null || isMonitorBroken(level)) {
            return false;
        }
        if (!(level.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera)) {
            return false;
        }
        return camera.hasInfraredScanner() && !camera.isBroken();
    }

    // ==================== 商店 ====================

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(
                org.agmas.noellesroles.init.ModItems.MONITORING_TERMINAL.getDefaultInstance(),
                MONITORING_TERMINAL_PRICE,
                ShopEntry.Type.TOOL));
        return shop;
    }

    // ==================== 判定 / 记录 ====================

    /** 该玩家是不是中控员（双端可用）。 */
    public static boolean isCentralControl(Player player) {
        if (player == null || player.level() == null) {
            return false;
        }
        return SRERoleWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.CENTRAL_CONTROL);
    }

    /** 监控是否失灵（失灵期间三个模块都无法安装 / 全部失效）。 */
    private static boolean isMonitorBroken(Level level) {
        return SREMonitorWorldComponent.KEY.get(level).isBroken();
    }

    /** 统一的失败提示，返回 false 便于一行返回。 */
    private static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        return false;
    }


    /**
     * 改造成功时在每个被改装的摄像头位置播放 {@code block.anvil.use}（铁砧使用的「哐」一声），
     * 让玩家听得出这一下真的装上了。
     * <p>
     * 音源用 {@code SoundSource.BLOCKS}，广播范围由原版音量决定；玩家远在监控室也能听到，
     * 音量略微降一点，避免一次装多台时叠得太响。
     */
    private static void playInstallSound(Level level, ServerPlayer player, List<BlockPos> installed) {
        if (!(level instanceof ServerLevel serverLevel) || installed == null || installed.isEmpty()) {
            return;
        }
        float volume = installed.size() > 1 ? 0.7F : 1.0F;
        for (BlockPos pos : installed) {
            serverLevel.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, volume, 1.2F);
        }
    }
    /** 事件回放：记录「xxx 为一台监控摄像头安装上了 xxx」。 */
    private static void recordInstall(ServerPlayer player, BlockPos pos, String moduleKey) {
        if (SRE.REPLAY_MANAGER == null) {
            return;
        }
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.player.camera_module_install",
                        GameReplayUtils.getReplayPlayerDisplayText(player, true),
                        Component.translatable("camera_module.noellesroles." + moduleKey)
                                .withStyle(ChatFormatting.AQUA)));
    }

    /** 事件回放：记录激光发射（命中时带上目标）。 */
    private static void recordLaserFire(ServerPlayer player, ServerPlayer target) {
        if (SRE.REPLAY_MANAGER == null) {
            return;
        }
        if (target == null) {
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.player.camera_laser_fire",
                            GameReplayUtils.getReplayPlayerDisplayText(player, true)));
        } else {
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.player.camera_laser_hit",
                            GameReplayUtils.getReplayPlayerDisplayText(player, true),
                            GameReplayUtils.getReplayPlayerDisplayText(target, true)));
        }
    }

    /** 本局开始 / 结束时清空观察者名册（防止跨局残留上一局的观看登记）。 */
    public static void clearRegistration() {
        VIEWERS.clear();
        NEWLY_VIEWED.clear();
    }
}
