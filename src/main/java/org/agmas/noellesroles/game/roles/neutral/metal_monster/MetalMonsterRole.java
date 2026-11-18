package org.agmas.noellesroles.game.roles.neutral.metal_monster;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.packet.SteelCoilInputC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.neutral.MetalMonsterRoleData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 金属怪 —— 杀手方中立职业。
 *
 * <p>开局自带一层普通护盾。两个技能由 V 键切换：一技能化身两格宽的钢卷滚动撞击，
 * 二技能在 2.5 秒内让撞击可以真的打死人（死因「钢卷碾压」）。
 *
 * <p>运动模型：初始速度 0.4，按住前进键每 tick 加速；松键按摩擦减速但不会立刻停下；
 * 转向每 tick 最多 30° 且按转角掉速；撞墙用入射速度反向反弹，反弹期间不能前进；
 * 速度超过 1.5 时撞到列车门 / 原版木门铜门会直接开门且不反弹。
 */
public class MetalMonsterRole extends NormalRole {

    /** 钢卷碾压死因。定义在自身包内，避免改动主模组常量表。 */
    public static final ResourceLocation STEEL_COIL_CRUSH = Noellesroles.id("steel_coil_crush");

    /** 初始速度（格 / tick）。 */
    public static final double INITIAL_SPEED = 0.4D;
    /** 速度上限。 */
    public static final double MAX_SPEED = 3.0D;
    /** 每 tick 前进加速度。 */
    private static final double ACCELERATION = 0.04D;
    /** 松开前进键时的摩擦系数。 */
    private static final double FRICTION = 0.98D;
    /** 单 tick 最大转向角（度）。 */
    private static final double MAX_TURN_DEGREES = 45.0D;
    /** 每转 1 度损失的速度。 */
    private static final double TURN_SPEED_LOSS = 0.01D;
    /** 撞墙反弹的恢复系数。 */
    private static final double RESTITUTION = 0.35D;
    /** 反弹后最长锁定期（tick）。 */
    private static final int BOUNCE_LOCK_TICKS = 30;
    /** 能撞开门的最低速度。 */
    private static final double DOOR_BREAK_SPEED = 1.5D;
    /** 二技能致命窗口时长（tick）。 */
    public static final int LETHAL_DURATION_TICKS = 50;
    /** 同一目标两次受击的最小间隔。 */
    private static final int HIT_COOLDOWN_TICKS = 10;
    /** 钢卷撞击判定的半宽（格）：总宽 2 格。 */
    private static final double HIT_HALF_WIDTH = 1.0D;

    public MetalMonsterRole(ResourceLocation identifier, int color, RoleTeam team,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, team, moodType, maxSprintTime, canSeeTime);
    }

    /** 该玩家是否是金属怪。 */
    public static boolean isMetalMonster(@Nullable Player player) {
        if (player == null || player.level() == null || io.wifi.starrailexpress.SRE.isLobby) {
            return false;
        }
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.METAL_MONSTER);
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        super.onInit(server, serverPlayer);
        giveShield(serverPlayer);
    }

    /** 给金属怪一层初始护盾（开局 / 每局重置时调用）。 */
    public static void giveShield(ServerPlayer player) {
        SREArmorPlayerComponent.KEY.get(player).addArmor(1, false);
    }

    // ==================== 技能 ====================

    /**
     * 一技能：开始或结束钢卷形态。再次按下时解除并进入 30 秒冷却（由 RoleSkill 管理）。
     */
    public static boolean toggleRoll(io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext context,
            boolean skillReady) {
        ServerPlayer player = context.player();
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        MetalMonsterRoleData data = RoleData.getNullable(MetalMonsterRoleData.class, player);
        if (data == null) {
            return false;
        }
        if (data.rolling) {
            stopRoll(player);
            return true;
        }
        if (!skillReady) {
            return false;
        }
        startRoll(player, data);
        return true;
    }

    /** 开始钢卷形态。 */
    private static void startRoll(ServerPlayer player, MetalMonsterRoleData data) {
        data.reset();
        data.rolling = true;
        data.speed = INITIAL_SPEED;
        Vec3 look = player.getLookAngle();
        double len = Math.hypot(look.x, look.z);
        if (len > 1.0E-4D) {
            data.dirX = look.x / len;
            data.dirZ = look.z / len;
        } else {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            data.dirX = -Mth.sin(yaw);
            data.dirZ = Mth.cos(yaw);
        }
        EntityDisguise.disguise(player, ModEntities.STEEL_COIL);
        player.setSprinting(false);
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, true, false, true));
        data.sync();
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS,
                1.2F, 0.5F);
    }

    /**
     * 变回人形：立即清除伪装、恢复移动、归零速度并关闭致命窗口。
     *
     * <p>注意：玩家死亡 / 转职后 RoleData 可能已被替换甚至为 null，
     * 因此伪装与视角/速度的恢复必须先做，不能依赖 RoleData 是否存在。
     */
    public static void stopRoll(ServerPlayer player) {
        EntityDisguise.clear(player);
        player.removeEffect(ModEffects.MOVE_BANED);
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = false;
        if (player.connection != null && !player.isRemoved()) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        MetalMonsterRoleData data = RoleData.getNullable(MetalMonsterRoleData.class, player);
        if (data != null) {
            data.reset();
            data.sync();
        }
    }

    /** 二技能：开启 2.5 秒致命窗口。窗口只在钢卷撞击结算时生效。 */
    public static boolean activateLethal(ServerPlayer player) {
        if (player.isSpectator() || !isMetalMonster(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        MetalMonsterRoleData data = RoleData.getNullable(MetalMonsterRoleData.class, player);
        if (data == null) {
            return false;
        }
        data.lethalUntil = player.level().getGameTime() + LETHAL_DURATION_TICKS;
        data.sync();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                1.0F, 0.8F);
        return true;
    }

    /** 客户端输入包的服务端薄处理。 */
    public static void onInput(ServerPlayer player, int bits, float yaw) {
        if (isInvalidRoller(player)) {
            return;
        }
        MetalMonsterRoleData data = RoleData.getNullable(MetalMonsterRoleData.class, player);
        if (data == null || !data.rolling) {
            return;
        }
        data.inputBits = bits;
        data.inputYaw = yaw;
        data.inputGameTime = player.level().getGameTime();
    }

    // ==================== 运动与结算 ====================

    /** 注册一次全局事件，统一驱动所有金属怪的钢卷形态。 */
    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(MetalMonsterRole::tickAll);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopRoll(handler.player));
        // 死亡瞬间立即复原（不等下一 tick），避免以旁观者身份残留伪装 / 惯性
        OnPlayerDeath.EVENT.register((player, reason) -> {
            if (player instanceof ServerPlayer sp) {
                stopRoll(sp);
            }
        });
        OnGameEnd.EVENT.register((level, game) -> {
            for (ServerPlayer player : level.players()) {
                stopRoll(player);
            }
        });
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                if (isMetalMonster(player)) {
                    giveShield(player);
                }
            }
        });
    }

    private static void tickAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MetalMonsterRoleData data = RoleData.getNullable(MetalMonsterRoleData.class, player);
            boolean invalid = isInvalidRoller(player);
            if (data == null || !data.rolling) {
                // 转职 / 死亡后 RoleData 可能已换成别的职业数据：
                // 只要身上还残留钢卷伪装，就强制复原，避免永久卡在钢卷形态
                if (invalid && EntityDisguise.get(player).type() == ModEntities.STEEL_COIL) {
                    stopRoll(player);
                }
                continue;
            }
            if (invalid) {
                stopRoll(player);
                continue;
            }
            tickRoll(player, data);
        }
    }

    /** 钢卷形态的持有者是否已失效：旁观 / 死亡或非生存 / 不再是金属怪。 */
    private static boolean isInvalidRoller(ServerPlayer player) {
        return player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player) || !isMetalMonster(player);
    }

    private static void tickRoll(ServerPlayer player, MetalMonsterRoleData data) {
        ServerLevel level = player.serverLevel();
        long gameTime = level.getGameTime();
        // 短时长续期：断线 / 死亡 / 异常情况时会自动失效，不会永久锁死移动
        player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 10, 0, true, false, true));

        boolean inputFresh = gameTime - data.inputGameTime <= 5;
        boolean canAccelerate = data.bounceTicks <= 0 && inputFresh
                && (data.inputBits & SteelCoilInputC2SPacket.BIT_FORWARD) != 0;
        if (canAccelerate) {
            double current = Math.atan2(-data.dirX, data.dirZ);
            boolean left = (data.inputBits & SteelCoilInputC2SPacket.BIT_LEFT) != 0;
            boolean right = (data.inputBits & SteelCoilInputC2SPacket.BIT_RIGHT) != 0;
            double next;
            if (left != right) {
                // A/D 键直接左右转弯：相对当前朝向偏一个上限角，方向由按键决定
                double turn = (left ? -1.0D : 1.0D) * MAX_TURN_DEGREES;
                next = current + Math.toRadians(turn);
            } else {
                // 未按 A/D 时保持沿鼠标视角方向前进（仍受每 tick 上限限制）
                float targetYaw = data.inputYaw * Mth.DEG_TO_RAD;
                double targetX = -Mth.sin(targetYaw);
                double targetZ = Mth.cos(targetYaw);
                double target = Math.atan2(-targetX, targetZ);
                double diff = Mth.wrapDegrees((float) Math.toDegrees(target - current));
                double turn = Mth.clamp(diff, -MAX_TURN_DEGREES, MAX_TURN_DEGREES);
                next = current + Math.toRadians(turn);
            }
            double turnDeg = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(next - current)));
            data.dirX = -Math.sin(next);
            data.dirZ = Math.cos(next);
            data.speed = Mth.clamp(data.speed + ACCELERATION - turnDeg * TURN_SPEED_LOSS,
                    INITIAL_SPEED, MAX_SPEED);
        } else {
            data.speed = Math.max(INITIAL_SPEED, data.speed * FRICTION);
            if (data.bounceTicks > 0) {
                data.bounceTicks--;
            }
        }

        Vec3 delta = new Vec3(data.dirX * data.speed, 0.0D, data.dirZ * data.speed);
        // 先用入射 AABB 探测「方块」阻挡：level.noCollision 只看方块，不把生物算进去。
        // 这样撞到玩家/生物时不会被当成撞墙，也就不会反弹（只走 hitPlayers 的伤害与击退）。
        boolean horizontalBlocked = !level.noCollision(player,
                player.getBoundingBox().move(delta.x, 0.0D, delta.z));
        player.move(MoverType.SELF, delta);

        // 撞墙先尝试滚上台阶：能上去就不反弹、不丢速。开门优先级更高（门不该被台阶判定吞掉）
        boolean doorOpened = false;
        boolean steppedUp = false;
        if (horizontalBlocked && data.speed > DOOR_BREAK_SPEED) {
            doorOpened = tryOpenDoorsAround(level, player, data);
        }
        if (horizontalBlocked && !doorOpened) {
            steppedUp = tryStepUp(level, player, data);
        }

        player.setDeltaMovement(delta);
        player.hasImpulse = true;
        if (player.connection != null) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }

        if (horizontalBlocked && !doorOpened && !steppedUp) {
            // 反弹必须用「入射速度」：move() 撞墙后该轴速度会被清零
            data.dirX = -data.dirX;
            data.dirZ = -data.dirZ;
            data.speed = Mth.clamp(data.speed * RESTITUTION, INITIAL_SPEED, MAX_SPEED);
            data.bounceTicks = BOUNCE_LOCK_TICKS;
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS,
                    1.4F, 0.7F);
        }

        hitPlayers(level, player, data, gameTime);
    }

    /**
     * 自动滚上台阶：可跳跃地图上一格，不可跳跃地图只上半格（{@code isJumpAvailable()} 由地图配置）。
     *
     * <p>玩家本体碰撞箱 0.6×1.8，伪装不改变碰撞箱，所以抬高后必须用玩家自己的 AABB 检测，
     * 且「抬高后」「抬高再前进一步后」都不能有方块阻挡，避免顺着墙往上爬。
     * 上去后本 tick 的水平移动已在这里完成。
     */
    private static boolean tryStepUp(ServerLevel level, ServerPlayer player, MetalMonsterRoleData data) {
        double vx = data.dirX * data.speed;
        double vz = data.dirZ * data.speed;
        if (vx * vx + vz * vz < 1.0E-6D) {
            return false;
        }
        double stepHeight = SREGameWorldComponent.KEY.get(level).isJumpAvailable() ? 1.0D : 0.5D;
        Vec3 step = new Vec3(vx, 0.0D, vz);
        if (!level.noCollision(player, player.getBoundingBox().move(0.0D, stepHeight, 0.0D))
                || !level.noCollision(player, player.getBoundingBox().move(step.x, stepHeight, step.z))) {
            return false;
        }
        player.setPos(player.getX(), player.getY() + stepHeight, player.getZ());
        player.move(MoverType.SELF, step);
        return true;
    }

    /**
     * 撞击判定覆盖 2 格宽（钢卷尺寸固定 2），但玩家本体碰撞箱仍是 0.6 格，能过一格宽道路。
     */
    private static void hitPlayers(ServerLevel level, ServerPlayer attacker, MetalMonsterRoleData data,
            long gameTime) {
        // 旁观 / 死亡 / 转职后撞击一律不生效（含二技能致命窗口）
        if (isInvalidRoller(attacker) || data.speed < 1.0D) {
            return;
        }
        AABB box = new AABB(
                attacker.getX() - HIT_HALF_WIDTH, attacker.getY(), attacker.getZ() - HIT_HALF_WIDTH,
                attacker.getX() + HIT_HALF_WIDTH, attacker.getY() + 2.0D, attacker.getZ() + HIT_HALF_WIDTH);
        List<Player> victims = level.getEntities(EntityType.PLAYER, box,
                p -> p != attacker && GameUtils.isPlayerAliveAndSurvival(p));
        // 速度每高 0.1 造成 1 点虚拟伤害；速度 1.0 时为 0，3.0 时为 20
        int virtualDamage = (int) Math.floor((data.speed - 1.0D) * 10.0D + 1.0E-6D);
        boolean lethal = gameTime < data.lethalUntil;
        for (Player victim : victims) {
            if (!(victim instanceof ServerPlayer serverVictim)) {
                continue;
            }
            Long last = data.hitTicks.get(serverVictim.getUUID());
            if (last != null && gameTime - last < HIT_COOLDOWN_TICKS) {
                continue;
            }
            data.hitTicks.put(serverVictim.getUUID(), gameTime);

            serverVictim.invulnerableTime = 0;
            serverVictim.hurt(level.damageSources().generic(), 1.0F);
            if (virtualDamage > 0) {
                DreamHealthComponent health = DreamHealthComponent.KEY.get(serverVictim);
                if (lethal) {
                    health.hurt(attacker, virtualDamage, STEEL_COIL_CRUSH);
                } else {
                    health.hurtWithoutKilling(attacker, virtualDamage);
                }
            }
            if (GameUtils.isPlayerAliveAndSurvival(serverVictim)) {
                // 钢卷专属击退公式：随速度线性增长，方向为前进方向的反方向
                double knockback = data.speed * 0.8D;
                serverVictim.knockback(knockback, -data.dirX, -data.dirZ);
                serverVictim.hurtMarked = true;
            }
            level.playSound(null, serverVictim.blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS,
                    1.0F, 0.6F);
        }
    }

    /** 撞门：扫描身前两格宽范围，速度达标时直接开门，返回是否真的开了门。 */
    private static boolean tryOpenDoorsAround(ServerLevel level, ServerPlayer player, MetalMonsterRoleData data) {
        boolean opened = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int feetY = Mth.floor(player.getY());
        for (int lateral = -1; lateral <= 1; lateral++) {
            double px = player.getX() + data.dirX + (-data.dirZ) * lateral;
            double pz = player.getZ() + data.dirZ + data.dirX * lateral;
            for (int dy = 0; dy <= 2; dy++) {
                pos.set(Mth.floor(px), feetY + dy, Mth.floor(pz));
                opened |= tryOpenDoor(level, pos);
            }
        }
        return opened;
    }

    /** 打开列车门或原版木门 / 铜门。 */
    private static boolean tryOpenDoor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SmallDoorBlock door) {
            BlockPos lower = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            if (!(level.getBlockEntity(lower) instanceof SmallDoorBlockEntity entity)
                    || entity.isBlasted() || entity.isJammed()) {
                return false;
            }
            if (!door.isOpen(level.getBlockState(lower))) {
                door.open(level.getBlockState(lower), level, entity, lower);
                return true;
            }
            return false;
        }
        if (state.getBlock() instanceof DoorBlock && !state.getValue(BlockStateProperties.OPEN)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 2);
            return true;
        }
        return false;
    }
}
