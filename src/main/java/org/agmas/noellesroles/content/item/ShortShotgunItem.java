/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeBat;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;

import java.util.List;

public class ShortShotgunItem extends Item implements HeldLikeBat, TrainWeapon {
    /** 最小蓄力时间：0.2秒 = 4刻 */
    private static final int MIN_CHARGE_TICKS = 4;
    private static final int MAX_CHARGE_TICKS = 40;
    private static final double MIN_KILL_RANGE = 2.0;
    private static final double MAX_KILL_RANGE = 4.0;
    private static final double KNOCKBACK_UNLOCK_RANGE = 3.0;
    private static final double KNOCKBACK_RANGE_EXTENSION = 2.0;
    private static final double FAN_HALF_ANGLE_DEGREES = 35.0;

    public ShortShotgunItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        // 检查玩家状态：旁观者或已死亡时不能使用
        if (user.isSpectator() || !user.isAlive()) {
            return InteractionResultHolder.fail(stack);
        }
        // 右键时播放上膛音效（服务端播放，附近所有玩家都能听到）
        if (!world.isClientSide) {
            world.playSound(null, user.blockPosition(), NRSounds.SHOTGUNU_COCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.short_shotgun.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.short_shotgun.tooltip2")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000; // 最大持续时间，确保 releaseUsing 能被正确调用
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (world.isClientSide) {
            return;
        }

        // 检查玩家状态：旁观者或已死亡时不发射，防止蓄力期间死亡仍能开枪
        Player player = (Player) user;
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }

        // 使用 remainingUseTicks 判断蓄力是否完成
        // remainingUseTicks < getUseDuration - MIN_CHARGE_TICKS 表示已蓄力足够时间
        if (remainingUseTicks > this.getUseDuration(stack, user) - MIN_CHARGE_TICKS) {
            // 蓄力不足，直接停止使用
            return;
        }

        ServerLevel serverLevel = (ServerLevel) world;

        int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
        double killRange = getKillRange(chargeTicks);

        // 播放射击音效
        world.playSound(null, player.blockPosition(), NRSounds.SHOTGUN_FIRE, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 生成与实际扇形射程一致的粒子效果
        spawnFlameParticles(serverLevel, player, killRange);

        // 锥形范围检测：以玩家视线（含俯仰角）为轴，
        // 击杀范围随蓄力从2格提升到4格，达到3格后外扩2格造成1点伤害并击退。
        Vec3 look = player.getLookAngle();
        double lookLength = look.length();
        if (lookLength > 1e-6) {
            Vec3 aim = look.scale(1.0 / lookLength);
            Vec3 origin = player.getEyePosition();
            double cosHalfAngle = Math.cos(Math.toRadians(FAN_HALF_ANGLE_DEGREES)); // 70度锥角

            java.util.Set<Integer> processed = new java.util.HashSet<>();
            applyFanEffect(world, player, origin, aim, cosHalfAngle, 0.0, killRange, true, processed);
            if (killRange >= KNOCKBACK_UNLOCK_RANGE) {
                applyFanEffect(world, player, origin, aim, cosHalfAngle,
                        killRange, killRange + KNOCKBACK_RANGE_EXTENSION, false, processed);
            }
        }

        if (!player.isCreative()) {
            InteractionHand usedHand = player.getUsedItemHand();
            stack.hurtAndBreak(1, player,
                    usedHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.getCooldowns().addCooldown(ModItems.SHORT_SHOTGUN, 30 * 20);
        }
    }

    private static double getKillRange(int chargeTicks) {
        double chargeProgress = Math.min(1.0, Math.max(0.0,
                (double) (chargeTicks - MIN_CHARGE_TICKS) / (MAX_CHARGE_TICKS - MIN_CHARGE_TICKS)));
        return MIN_KILL_RANGE + (MAX_KILL_RANGE - MIN_KILL_RANGE) * chargeProgress;
    }

    /**
     * 锥形范围效果：以玩家视线（含俯仰角）为轴进行三维判定，向上或向下看时锥形随之倾斜。
     *
     * @param origin       锥形顶点（玩家眼睛位置）
     * @param aim          玩家视线单位向量（含俯仰）
     * @param cosHalfAngle 锥形半角的余弦值
     */
    private static void applyFanEffect(Level world, Player player, Vec3 origin, Vec3 aim, double cosHalfAngle,
                                       double minRange, double maxRange, boolean lethal,
                                       java.util.Set<Integer> processed) {
        int pBlockX = player.blockPosition().getX();
        int pBlockY = player.blockPosition().getY();
        int pBlockZ = player.blockPosition().getZ();
        int blockRange = (int) Math.ceil(maxRange);
        double reach = maxRange + 1.5;
        double reachSq = reach * reach;

        for (int dx = -blockRange; dx <= blockRange; dx++) {
            for (int dy = -blockRange; dy <= blockRange; dy++) {
                for (int dz = -blockRange; dz <= blockRange; dz++) {
                    if (dx * dx + dy * dy + dz * dz > reachSq) {
                        continue;
                    }

                    int bx = pBlockX + dx;
                    int by = pBlockY + dy;
                    int bz = pBlockZ + dz;

                    if (!isBlockInCone(bx, by, bz, origin, aim, cosHalfAngle, maxRange)) {
                        continue;
                    }
                    if (minRange > 0.0
                            && isBlockInCone(bx, by, bz, origin, aim, cosHalfAngle, minRange)) {
                        continue;
                    }

                    // 玩家高度接近2格，向上多取一格以确保覆盖站在该方块上的目标
                    AABB tileBox = new AABB(bx, by, bz, bx + 1, by + 2, bz + 1);
                    List<Player> tilePlayers = world.getEntitiesOfClass(Player.class, tileBox,
                            p -> p != player && GameUtils.isPlayerAliveAndSurvival(p));
                    for (Player target : tilePlayers) {
                        if (processed.contains(target.getId()) || !canSeeTarget(world, player, target)) {
                            continue;
                        }
                        processed.add(target.getId());
                        // 防暴盾牌格挡：无论本次是致命扇形还是击退扇形，正举盾正面朝向枪口的目标
                        // 都会被挡下（消耗盾牌 1 点耐久），不吃击杀也不吃伤害与击退
                        if (RiotShieldHandler.tryBlockAttack(target, player)) {
                            continue;
                        }
                        if (lethal) {
                            io.wifi.starrailexpress.game.GameUtils.killPlayer(target, true, player,
                                    Noellesroles.id("short_shotgun"));
                        } else {
                            target.hurt(player.damageSources().playerAttack(player), 1.0F);
                            // 沿射击方向击退（knockback 内部取反，故传入 -aim）
                            target.knockback(0.5F, -aim.x, -aim.z);
                        }
                    }
                }
            }
        }
    }

    /**
     * 生成烈焰弹粒子效果
     */
    private void spawnFlameParticles(ServerLevel serverLevel, Player player, double killRange) {
        Vec3 look = player.getLookAngle();
        Vec3 aim = look.length() > 1e-6 ? look.normalize() : new Vec3(0.0, 0.0, 1.0);
        Vec3 right = getFanRight(aim);
        // 粒子从持枪的手部（枪口）发射，而不是头部
        Vec3 origin = getHandPosition(player, aim, right);
        double startX = origin.x + aim.x * 0.5;
        double startY = origin.y + aim.y * 0.5;
        double startZ = origin.z + aim.z * 0.5;

        // 发射方向的火焰粒子
        for (int i = 0; i < 15; i++) {
            double spread = 0.3;
            double speed = 0.15 + serverLevel.random.nextDouble() * 0.1;
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * spread;
            double offsetY = (serverLevel.random.nextDouble() - 0.5) * spread;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * spread;

            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    startX + offsetX, startY + offsetY, startZ + offsetZ,
                    1,
                    aim.x * speed + (serverLevel.random.nextDouble() - 0.5) * 0.05,
                    aim.y * speed + (serverLevel.random.nextDouble() - 0.5) * 0.05,
                    aim.z * speed + (serverLevel.random.nextDouble() - 0.5) * 0.05,
                    0.02);
        }

        // 添加烟雾粒子
        for (int i = 0; i < 8; i++) {
            double spread = 0.4;
            double speed = 0.08 + serverLevel.random.nextDouble() * 0.05;
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * spread;
            double offsetY = (serverLevel.random.nextDouble() - 0.5) * spread;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * spread;

            serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    startX + offsetX, startY + offsetY, startZ + offsetZ,
                    1,
                    aim.x * speed,
                    aim.y * speed + 0.02,
                    aim.z * speed,
                    0.01);
        }

        // 添加余烬粒子
        for (int i = 0; i < 10; i++) {
            double spread = 0.2;
            double speed = 0.12 + serverLevel.random.nextDouble() * 0.08;
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * spread;
            double offsetY = (serverLevel.random.nextDouble() - 0.5) * spread;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * spread;

            serverLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    startX + offsetX, startY + offsetY, startZ + offsetZ,
                    1,
                    aim.x * speed + (serverLevel.random.nextDouble() - 0.5) * 0.03,
                    aim.y * speed + 0.03,
                    aim.z * speed + (serverLevel.random.nextDouble() - 0.5) * 0.03,
                    0.01);
        }

        // 扇面粒子：铺在由"视线"和"水平右方向"张成的平面上，
        // 保持原本的水平扇形外观，但整体随俯仰角倾斜
        spawnFanParticles(serverLevel, origin, aim, right, 0.4, killRange, ParticleTypes.FLAME, 0.02);
        spawnFanBoundaryParticles(serverLevel, origin, aim, right, 0.4, killRange,
                ParticleTypes.SOUL_FIRE_FLAME);

        if (killRange >= KNOCKBACK_UNLOCK_RANGE) {
            double knockbackRange = killRange + KNOCKBACK_RANGE_EXTENSION;
            spawnFanParticles(serverLevel, origin, aim, right, killRange, knockbackRange,
                    ParticleTypes.SMOKE, 0.01);
            spawnFanBoundaryParticles(serverLevel, origin, aim, right, killRange, knockbackRange,
                    ParticleTypes.SMOKE);
        }
    }

    /**
     * 求扇面所在平面内的"右方向"：水平朝向绕 Y 轴右侧 90°，始终水平。
     * 扇面由该向量与视线共同张成，因此抬头/低头时扇面会整体倾斜。
     */
    private static Vec3 getFanRight(Vec3 aim) {
        Vec3 horizontal = new Vec3(aim.x, 0.0, aim.z);
        if (horizontal.lengthSqr() < 1e-8) {
            // 视线近乎垂直时水平朝向退化，取一个固定朝向兜底
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        return horizontal.normalize().cross(new Vec3(0.0, 1.0, 0.0)).normalize();
    }

    /**
     * 在扇面内取方向：以视线为轴、在扇面内左右偏转 angle 弧度。
     */
    private static Vec3 fanDirection(Vec3 aim, Vec3 right, double radians) {
        return aim.scale(Math.cos(radians)).add(right.scale(Math.sin(radians)));
    }

    /**
     * 估算持枪手部（枪口）的世界坐标：由眼睛位置向下移到手部高度，
     * 并向持枪侧（右手）与视线前方各偏移一点。随俯仰/转身一起变化。
     */
    private static Vec3 getHandPosition(Player player, Vec3 aim, Vec3 right) {
        return player.getEyePosition()
                .add(new Vec3(0.0, -0.5, 0.0)) // 眼睛 -> 手部高度
                .add(right.scale(0.25))        // 偏向持枪手（右手）
                .add(aim.scale(0.15));         // 略微前伸
    }

    private static void spawnFanParticles(ServerLevel serverLevel, Vec3 origin, Vec3 aim, Vec3 right,
                                          double minRange, double maxRange,
                                          net.minecraft.core.particles.ParticleOptions particle,
                                          double speed) {
        for (double distance = minRange; distance <= maxRange + 0.001; distance += 0.35) {
            for (double angle = -FAN_HALF_ANGLE_DEGREES; angle <= FAN_HALF_ANGLE_DEGREES + 0.001; angle += 7.0) {
                Vec3 dir = fanDirection(aim, right, Math.toRadians(angle));
                Vec3 pos = origin.add(dir.scale(distance));
                double jitter = (serverLevel.random.nextDouble() - 0.5) * 0.12;
                serverLevel.sendParticles(particle,
                        pos.x + jitter, pos.y, pos.z + jitter,
                        1, 0.03, 0.02, 0.03, speed);
            }
        }
    }

    private static void spawnFanBoundaryParticles(ServerLevel serverLevel, Vec3 origin, Vec3 aim, Vec3 right,
                                                  double minRange, double range,
                                                  net.minecraft.core.particles.ParticleOptions particle) {
        // 外弧
        for (double angle = -FAN_HALF_ANGLE_DEGREES; angle <= FAN_HALF_ANGLE_DEGREES + 0.001; angle += 3.5) {
            Vec3 dir = fanDirection(aim, right, Math.toRadians(angle));
            Vec3 pos = origin.add(dir.scale(range));
            serverLevel.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        // 两条直边（随俯仰一起倾斜，用于直观体现扇面的朝向）
        for (double edge : new double[]{-FAN_HALF_ANGLE_DEGREES, FAN_HALF_ANGLE_DEGREES}) {
            Vec3 dir = fanDirection(aim, right, Math.toRadians(edge));
            for (double distance = minRange; distance <= range + 0.001; distance += 0.35) {
                Vec3 pos = origin.add(dir.scale(distance));
                serverLevel.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /** 方块取样的8个角 + 中心点（相对方块最小坐标的偏移） */
    private static final double[][] BLOCK_CORNER_OFFSETS = {
        {0.0, 0.0, 0.0},
        {1.0, 0.0, 0.0},
        {0.0, 1.0, 0.0},
        {1.0, 1.0, 0.0},
        {0.0, 0.0, 1.0},
        {1.0, 0.0, 1.0},
        {0.0, 1.0, 1.0},
        {1.0, 1.0, 1.0},
        {0.5, 0.5, 0.5}
    };

    /**
     * 检查方块是否与锥形区域相交（三维判定，随俯仰角倾斜）。
     * 检查方块的8个角和中心点，只要有一个点在锥体内（距离&lt;=maxRange 且 与视线夹角&lt;=半角），
     * 就认为该方块命中——即"不完整的部分也算作一格"。
     *
     * @param bx           方块X坐标
     * @param by           方块Y坐标
     * @param bz           方块Z坐标
     * @param origin       锥形顶点（玩家眼睛位置）
     * @param aim          玩家视线单位向量（含俯仰）
     * @param cosHalfAngle 锥形半角的余弦值
     * @param maxRange     最大射程（格）
     * @return 方块是否与锥形相交
     */
    private static boolean isBlockInCone(int bx, int by, int bz, Vec3 origin, Vec3 aim,
                                         double cosHalfAngle, double maxRange) {
        for (double[] offset : BLOCK_CORNER_OFFSETS) {
            double dx = bx + offset[0] - origin.x;
            double dy = by + offset[1] - origin.y;
            double dz = bz + offset[2] - origin.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= 1e-6 || dist > maxRange) {
                continue;
            }
            double dot = aim.x * (dx / dist) + aim.y * (dy / dist) + aim.z * (dz / dist);
            if (dot >= cosHalfAngle) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测射击者是否能"看到"目标（视线路径上无固体方块阻挡）
     * 
     * @param world   世界
     * @param shooter 射击者
     * @param target  目标玩家
     * @return true 表示视线畅通，false 表示被方块阻挡
     */
    private static boolean canSeeTarget(Level world, Player shooter, Player target) {
        Vec3 from = shooter.getEyePosition(); // 射击者眼睛位置
        Vec3 to = target.getEyePosition(); // 目标眼睛位置

        // 执行方块碰撞射线检测（忽略流体，只考虑固体碰撞箱）
        BlockHitResult hit = world
                .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (hit.getType() == BlockHitResult.Type.MISS) {
            return true; // 没有击中任何方块 -> 视线畅通
        }

        // 计算击中点到射击者的距离平方，以及目标到射击者的距离平方
        double distToHitSq = from.distanceToSqr(hit.getLocation());
        double distToTargetSq = from.distanceToSqr(to);
        // 如果击中点的距离不小于目标点的距离（允许微小误差），说明射线实际上到达了目标附近，方块在目标身后或内部，仍判定为可见
        return distToHitSq >= distToTargetSq - 1e-5;
    }
}
