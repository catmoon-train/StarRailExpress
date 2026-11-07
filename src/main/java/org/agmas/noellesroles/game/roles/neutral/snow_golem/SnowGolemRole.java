package org.agmas.noellesroles.game.roles.neutral.snow_golem;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.event.CanCollideWith;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.scene.MapStatusBarRuntime;
import org.agmas.noellesroles.client.scene.SceneTaskManager;
import org.agmas.noellesroles.content.block.scene.StoveBlock;
import org.agmas.noellesroles.content.entity.SnowballRollEntity;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.SnowGolemRoleData;
import org.jetbrains.annotations.Nullable;

/**
 * 雪傀儡 —— 只在「拥有保暖值状态栏」的地图刷新的好人方中立职业。
 *
 * <p>
 * 特性一览（注册见 {@code ModRoles.SNOW_GOLEM}）：
 * <ul>
 * <li>常态伪装成原版雪傀儡（同骷髅伪装成原版骷髅的写法）；</li>
 * <li>不再拥有保暖值，也不会被保暖值结算冻死；</li>
 * <li>靠近点着的炉灶 3 格内、或身上着火时，每秒扣 1 点虚拟生命，归零时死因为「融化」
 * （着火致死时击杀者为当初点燃他的人）；</li>
 * <li>在雪/雪块上每移动 4 格推出一颗大雪球，之后每 4 格把雪球撑大 0.1（上限 6）；</li>
 * <li>技能【推出雪球】：把正在推的雪球猛推出去，撞到玩家的伤害 / 击退 / 缓慢按尺寸结算，
 * 撞墙反弹并缩小 1，可以被重新接住；</li>
 * <li>不刷新「取暖」任务（复用 {@link SRERole#addUnrefreshableTasks}，同老人不刷锻炼）。</li>
 * </ul>
 *
 * <p>
 * 每个玩家的推动进度放在 {@link SnowGolemRoleData} 里；所有事件都只在
 * {@link #registerEvents()} 注册一次（不要给每个实例注册事件）。
 */
public class SnowGolemRole extends NormalRole {

    /** 虚拟生命上限（与 Dream 虚拟血量同一套组件，默认 20）。 */
    public static final int MAX_VIRTUAL_HEALTH = 20;

    /** 靠近点着的炉灶多少格内会开始融化。 */
    public static final int STOVE_RANGE = 3;
    /** 融化判定间隔（tick）：每秒扣 1 点。 */
    public static final int MELT_INTERVAL = 20;
    /** 在雪地上移动多少格推出 / 撑大一次雪球。 */
    public static final double SNOW_STEP_DISTANCE = 4.0D;
    /** 判定「站在雪上」时向下看几格（雪球 / 顶层雪都只有 0.125~1 格高）。 */
    private static final int GROUND_SCAN_DEPTH = 3;

    public SnowGolemRole(ResourceLocation identifier, int color, RoleTeam team,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, team, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 判定 ====================

    /** 该玩家是否是雪傀儡。 */
    public static boolean isSnowGolem(@Nullable Player player) {
        if (player == null || player.level() == null || SRE.isLobby) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.SNOW_GOLEM);
    }

    /**
     * 该地图是否让雪傀儡登场：只有地图配置里状态条为 {@link MapStatusBarType#WARMTH} 的才算。
     *
     * <p>
     * 职业刷新条件本身已经由 {@code ModRoles.SNOW_GOLEM} 的 {@code setCanSpawnInMap}
     * 按同一字段判过一遍，这里是给「开局回调」用的运行时读取（拿不到 {@code AreasSettings} 的场合）。
     */
    public static boolean isWarmthMap(@Nullable ServerLevel level) {
        if (level == null) {
            return false;
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas == null || areas.areasSettings == null) {
            return false;
        }
        return areas.areasSettings.mapStatusBar == MapStatusBarType.WARMTH;
    }

    /** 保暖值地图里不该有保暖值：把该玩家的保暖值清掉。 */
    private static void clearWarmth(ServerPlayer player) {
        var type = MapStatusBarRuntime.getTrackedType(player);
        if (type == MapStatusBarType.WARMTH) {
            MapStatusBarRuntime.setValue(player, MapStatusBarType.WARMTH, 0);
        }
    }

    /** 玩家脚下是否是雪 / 雪块（能推出雪球的地面）。 */
    public static boolean isOnSnow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos below = player.blockPosition().below();
        for (int dy = 0; dy < GROUND_SCAN_DEPTH; dy++) {
            BlockState state = level.getBlockState(below.below(dy));
            if (!state.isAir()) {
                return SnowballRollEntity.isSnowGround(state);
            }
        }
        return false;
    }

    // ==================== 伪装 ====================

    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        super.onInit(server, serverPlayer);
        applySnowGolemDisguise(serverPlayer);
        clearWarmth(serverPlayer);
        // 职业刚分配时身上可能还挂着上一轮刷出的「取暖」任务，立刻拿掉
        dropWarmthTask(serverPlayer);
    }

    @Override
    public void onRemove(ServerPlayer player) {
        super.onRemove(player);
        // 职业被移除时解除伪装；局末清理由核心统一负责
        EntityDisguise.clear(player);
        // 立刻松开正在推的雪球：否则雪球会保留 ownerId，新职业接手后会继续被推着走
        releaseHeldBall(player);
    }

    /** 让该玩家正在推的雪球原地停下（转职 / 退出时调用）。 */
    public static void releaseHeldBall(ServerPlayer player) {
        if (player == null || player.level() == null || player.level().isClientSide) {
            return;
        }
        for (SnowballRollEntity ball : player.serverLevel()
                .getEntities(ModEntities.SNOWBALL_ROLL, entity -> true)) {
            if (ball.isRemoved() || !ball.isHeld()) {
                continue;
            }
            if (player.getUUID().equals(ball.getOwnerId())) {
                ball.releaseInPlace();
            }
        }
    }

    /** 未伪装时补上雪傀儡外观（掉线重连 / 开局重置会丢伪装状态，需要兜底）。 */
    public static void applySnowGolemDisguise(ServerPlayer player) {
        if (player == null || player.level() == null || player.level().isClientSide) {
            return;
        }
        if (!EntityDisguise.isDisguised(player)) {
            EntityDisguise.disguise(player, EntityType.SNOW_GOLEM);
        }
    }

    // ==================== 融化被动 ====================

    /**
     * 记录「是谁点燃了这名玩家」，用于把融化的击杀者算给施火者。
     *
     * <p>
     * 放在统一的伤害事件里（不是每个职业实例各注册一个），只对雪傀儡生效。
     */
    private static void onPlayerDamaged(net.minecraft.world.entity.LivingEntity entity,
            net.minecraft.world.damagesource.DamageSource source, float baseDamage, float damageTaken) {
        if (!(entity instanceof ServerPlayer player) || !isSnowGolem(player)) {
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != player) {
            SnowGolemRoleData data = SnowGolemRoleData.get(player);
            if (data != null) {
                data.igniter = attacker.getUUID();
                data.igniterGameTime = player.level().getGameTime();
            }
        }
    }

    /** 每秒一次的融化结算：靠近点着的炉灶或身上着火就扣 1 点虚拟生命。 */
    public static void tickMelt(ServerPlayer player) {
        SnowGolemRoleData data = SnowGolemRoleData.get(player);
        if (data == null) {
            return;
        }
        if (++data.meltTicks < MELT_INTERVAL) {
            return;
        }
        data.meltTicks = 0;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        boolean nearStove = nearLitStove(player);
        boolean burning = player.isOnFire();
        if (!nearStove && !burning) {
            return;
        }
        ServerPlayer killer = burning ? data.resolveIgniter(player) : null;
        DreamHealthComponent.KEY.get(player).hurt(killer, 1, GameConstants.DeathReasons.MELT);
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.PLAYERS,
                    0.8F, 1.4F);
        }
    }

    /** 3 格内是否有点着的炉灶（范围与保暖值的恢复判定一致）。 */
    public static boolean nearLitStove(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -STOVE_RANGE; x <= STOVE_RANGE; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -STOVE_RANGE; z <= STOVE_RANGE; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModSceneBlocks.STOVE) && state.hasProperty(StoveBlock.LIT)
                            && state.getValue(StoveBlock.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ==================== 推雪球 ====================

    /** 每 tick 累计「在雪上走了多远」，走满 4 格推出 / 撑大雪球。 */
    public static void tickSnowballPush(ServerPlayer player) {
        SnowGolemRoleData data = SnowGolemRoleData.get(player);
        if (data == null) {
            return;
        }
        Vec3 pos = player.position();
        if (data.lastX == Double.MIN_VALUE) {
            data.lastX = pos.x;
            data.lastZ = pos.z;
            return;
        }
        double moved = Math.sqrt((pos.x - data.lastX) * (pos.x - data.lastX)
                + (pos.z - data.lastZ) * (pos.z - data.lastZ));
        data.lastX = pos.x;
        data.lastZ = pos.z;
        if (moved <= 1.0E-4D || !isOnSnow(player)) {
            return;
        }
        data.snowDistance += moved;
        if (data.snowDistance < SNOW_STEP_DISTANCE) {
            // 绝大多数 tick 走这里：还没走满 4 格，不需要知道雪球在哪
            return;
        }
        // 只有真的要推 / 撑大时，才去维度里查一次雪球。
        // heldBall 内部是 level.getEntities(...)，会分配一个 List；放在这里可以
        // 把「每 tick 一次实体查询」降到「每 4 格一次」。
        SnowballRollEntity ball = data.heldBall(player.serverLevel());
        while (data.snowDistance >= SNOW_STEP_DISTANCE) {
            data.snowDistance -= SNOW_STEP_DISTANCE;
            if (ball == null || ball.isRemoved()) {
                ball = SnowballRollEntity.spawnHeld(player.serverLevel(), player);
            } else {
                ball.grow(SnowballRollEntity.GROW_STEP);
            }
        }
    }

    /**
     * 技能【推出雪球】：把正在推的雪球猛地推出去。
     *
     * @return 是否真的推出了（false 不消耗冷却）
     */
    public static boolean launchSnowball(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SnowGolemRoleData data = SnowGolemRoleData.get(player);
        if (data == null) {
            return false;
        }
        SnowballRollEntity ball = data.heldBall(player.serverLevel());
        if (ball == null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component
                            .translatable("message.noellesroles.snow_golem.no_snowball"),
                    true);
            return false;
        }
        ball.launch(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
                1.0F, 0.6F);
        return true;
    }

    // ==================== 任务屏蔽 ====================

    /**
     * 把已经刷出来的「取暖」任务直接移除（运行期兜底）。
     *
     * <p>
     * 注册时的 {@code addUnrefreshableTasks(LIGHT_STOVE)} 只能拦住「之后新刷」的任务：
     * 职业分配发生在开局之后，此时玩家身上可能已经挂着上一轮刷出的取暖任务；
     * 而场景任务一旦 {@code SceneTaskManager.assign} 过，就会一直挂在任务栏里等玩家去点炉灶。
     * 所以这里像老人处理锻炼任务那样，每 tick 检查一次并直接拿掉（不给奖励，也不占任务位）。
     *
     * <p>
     * 与老人不同的地方：老人是把计时器清零让任务「瞬间完成」（还会照常发奖励），
     * 雪傀儡要的是「完全不出现」，所以这里走移除而不是完成。
     *
     * @return 是否移除掉了任务
     */
    public static boolean dropWarmthTask(ServerPlayer player) {
        var tasks = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.KEY.get(player);
        if (tasks == null || tasks.tasks == null) {
            return false;
        }
        var taskType = io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task.LIGHT_STOVE;
        if (tasks.tasks.remove(taskType) == null) {
            return false;
        }
        // 并列任务标记与场景任务登记都要一起清，否则 SceneTaskManager 会把它自愈回来
        tasks.parallelTaskTypes.remove(taskType);
        SceneTaskManager.clear(player,
                SceneTaskManager.Type.LIGHT_STOVE);
        tasks.sync();
        return true;
    }

    /**
     * 注册该职业需要的事件（由 {@code NREventRegister} 统一调用一次）。
     *
     * <p>
     * 注意：<b>不要</b>在职业实例上注册事件，也不要在 {@code static {}} 里引用
     * {@code ModRoles.XXX}（会触发类初始化循环），所以这里都写在方法体里。
     */
    public static void registerEvents() {
        // 记录火焰施加者：用于融化致死的击杀者归属
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!blocked && damageTaken > 0.0F) {
                onPlayerDamaged(entity, source, baseDamage, damageTaken);
            }
        });

        // 雪球是实心的（见 SnowballRollEntity#canBeCollidedWith），玩家不能穿过去。
        // 这里只关掉两种不该碰撞的组合，其余一律 PASS 交给原版/其它规则决定：
        // 1) 两颗雪球之间：否则会互相顶住、叠起来；
        // 2) 雪球与自己人（正在推它的人 / 把它推出去的人）：否则雪傀儡会顶着自己的球走不动。
        CanCollideWith.ENTITY.register((self, other) -> {
            if (self instanceof SnowballRollEntity ball) {
                if (other instanceof SnowballRollEntity || ball.isOwnedBy(other)) {
                    return TrueFalseResult.FALSE;
                }
            }
            if (other instanceof SnowballRollEntity ball && ball.isOwnedBy(self)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });

        // 开局：清掉上一局残留的雪球，并给雪傀儡补上伪装 / 清保暖值
        OnGameInitialized.EVENT.register(serverLevel -> {
            clearSnowballs(serverLevel);
            if (!isWarmthMap(serverLevel)) {
                return;
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            if (gameWorldComponent == null) {
                return;
            }
            for (ServerPlayer player : serverLevel.players()) {
                if (gameWorldComponent.isRole(player, ModRoles.SNOW_GOLEM)) {
                    applySnowGolemDisguise(player);
                    clearWarmth(player);
                    dropWarmthTask(player);
                }
            }
        });

        // 局末：清掉所有雪球实体
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> clearSnowballs(serverLevel));
    }

    /** 清除当前维度内所有雪球实体（开局 / 结束都要清）。 */
    public static void clearSnowballs(ServerLevel level) {
        for (SnowballRollEntity ball : level.getEntities(ModEntities.SNOWBALL_ROLL, entity -> true)) {
            ball.discard();
        }
    }
}
