package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeBat;
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

public class ShortShotgunItem extends Item implements HeldLikeBat {
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

        // 扇形范围检测：击杀范围随蓄力从2格提升到4格，达到3格后外扩2格造成1点伤害并击退。
        Vec3 look = player.getLookAngle();
        Vec3 l2 = new Vec3(look.x, 0, look.z);
        double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
        if (llen > 0) {
            Vec3 nlook = l2.scale(1.0 / llen);
            double cosHalfAngle = Math.cos(Math.toRadians(FAN_HALF_ANGLE_DEGREES)); // 70度扇形

            java.util.Set<Integer> processed = new java.util.HashSet<>();
            applyFanEffect(world, player, nlook, cosHalfAngle, 0.0, killRange, true, processed);
            if (killRange >= KNOCKBACK_UNLOCK_RANGE) {
                applyFanEffect(world, player, nlook, cosHalfAngle,
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

    private static void applyFanEffect(Level world, Player player, Vec3 nlook, double cosHalfAngle,
                                       double minRange, double maxRange, boolean lethal,
                                       java.util.Set<Integer> processed) {
        int pBlockX = player.blockPosition().getX();
        int pBlockZ = player.blockPosition().getZ();
        int pBlockY = player.blockPosition().getY();
        int blockRange = (int) Math.ceil(maxRange);

        for (int dx = -blockRange; dx <= blockRange; dx++) {
            for (int dz = -blockRange; dz <= blockRange; dz++) {
                int bx = pBlockX + dx;
                int bz = pBlockZ + dz;

                if (dx * nlook.x + dz * nlook.z <= 0.1) {
                    continue;
                }

                if (!isBlockInFan(bx, bz, player.getX(), player.getZ(), nlook, cosHalfAngle, maxRange)) {
                    continue;
                }
                if (minRange > 0.0
                        && isBlockInFan(bx, bz, player.getX(), player.getZ(), nlook, cosHalfAngle, minRange)) {
                    continue;
                }

                AABB tileBox = new AABB(bx, pBlockY - 1, bz, bx + 1, pBlockY + 2, bz + 1);
                List<Player> tilePlayers = world.getEntitiesOfClass(Player.class, tileBox,
                        p -> p != player && GameUtils.isPlayerAliveAndSurvival(p));
                for (Player target : tilePlayers) {
                    if (processed.contains(target.getId()) || !canSeeTarget(world, player, target)) {
                        continue;
                    }
                    processed.add(target.getId());
                    if (lethal) {
                        io.wifi.starrailexpress.game.GameUtils.killPlayer(target, true, player,
                                Noellesroles.id("short_shotgun"));
                    } else {
                        target.hurt(player.damageSources().playerAttack(player), 1.0F);
                        target.knockback(0.5F, player.getX() - target.getX(), player.getZ() - target.getZ());
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
        double startX = player.getX() + look.x * 0.5;
        double startY = player.getY() + player.getEyeHeight() * 0.5;
        double startZ = player.getZ() + look.z * 0.5;

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
                    look.x * speed + (serverLevel.random.nextDouble() - 0.5) * 0.05,
                    look.y * speed + (serverLevel.random.nextDouble() - 0.5) * 0.05,
                    look.z * speed + (serverLevel.random.nextDouble() - 0.5) * 0.05,
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
                    look.x * speed,
                    look.y * speed + 0.02,
                    look.z * speed,
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
                    look.x * speed + (serverLevel.random.nextDouble() - 0.5) * 0.03,
                    look.y * speed + 0.03,
                    look.z * speed + (serverLevel.random.nextDouble() - 0.5) * 0.03,
                    0.01);
        }

        Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
        double lookLength = horizontalLook.length();
        if (lookLength <= 0.0) {
            return;
        }

        Vec3 nlook = horizontalLook.scale(1.0 / lookLength);
        spawnFanParticles(serverLevel, player, nlook, 0.4, killRange, ParticleTypes.FLAME, 0.02);
        spawnFanBoundaryParticles(serverLevel, player, nlook, killRange, ParticleTypes.SOUL_FIRE_FLAME);

        if (killRange >= KNOCKBACK_UNLOCK_RANGE) {
            double knockbackRange = killRange + KNOCKBACK_RANGE_EXTENSION;
            spawnFanParticles(serverLevel, player, nlook, killRange, knockbackRange, ParticleTypes.SMOKE, 0.01);
            spawnFanBoundaryParticles(serverLevel, player, nlook, knockbackRange, ParticleTypes.SMOKE);
        }
    }

    private static void spawnFanParticles(ServerLevel serverLevel, Player player, Vec3 nlook,
                                          double minRange, double maxRange,
                                          net.minecraft.core.particles.ParticleOptions particle,
                                          double speed) {
        double y = player.getY() + 0.85;
        for (double distance = minRange; distance <= maxRange + 0.001; distance += 0.35) {
            for (double angle = -FAN_HALF_ANGLE_DEGREES; angle <= FAN_HALF_ANGLE_DEGREES + 0.001; angle += 7.0) {
                Vec3 dir = rotateHorizontal(nlook, Math.toRadians(angle));
                double jitter = (serverLevel.random.nextDouble() - 0.5) * 0.12;
                double x = player.getX() + dir.x * distance + jitter;
                double z = player.getZ() + dir.z * distance + jitter;
                serverLevel.sendParticles(particle, x, y, z, 1, 0.03, 0.02, 0.03, speed);
            }
        }
    }

    private static void spawnFanBoundaryParticles(ServerLevel serverLevel, Player player, Vec3 nlook,
                                                  double range,
                                                  net.minecraft.core.particles.ParticleOptions particle) {
        double y = player.getY() + 0.9;
        for (double angle = -FAN_HALF_ANGLE_DEGREES; angle <= FAN_HALF_ANGLE_DEGREES + 0.001; angle += 3.5) {
            Vec3 dir = rotateHorizontal(nlook, Math.toRadians(angle));
            double x = player.getX() + dir.x * range;
            double z = player.getZ() + dir.z * range;
            serverLevel.sendParticles(particle, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static Vec3 rotateHorizontal(Vec3 vec, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos - vec.z * sin, 0.0, vec.x * sin + vec.z * cos);
    }

    /**
     * 检查方块是否与扇形区域相交。
     * 检查方块的四个角和中心点，只要有一个点在扇形内（距离<=maxRange 且 角度<=半角），
     * 就认为该方块命中——即"不完整的部分也算作一格"。
     *
     * @param bx           方块X坐标
     * @param bz           方块Z坐标
     * @param playerX      玩家精确X
     * @param playerZ      玩家精确Z
     * @param nlook        玩家视线方向单位向量（XZ平面）
     * @param cosHalfAngle 扇形半角的余弦值
     * @param maxRange     最大射程（格）
     * @return 方块是否与扇形相交
     */
    private static boolean isBlockInFan(int bx, int bz, double playerX, double playerZ,
                                         Vec3 nlook, double cosHalfAngle, double maxRange) {
        // 检查方块的4个角和中心点
        double[][] checkPoints = {
            {bx + 0.0, bz + 0.0},
            {bx + 1.0, bz + 0.0},
            {bx + 0.0, bz + 1.0},
            {bx + 1.0, bz + 1.0},
            {bx + 0.5, bz + 0.5}
        };

        for (double[] point : checkPoints) {
            double cx = point[0];
            double cz = point[1];
            double dx = cx - playerX;
            double dz = cz - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist == 0)
                continue;
            if (dist > maxRange)
                continue;
            double dot = nlook.x * (dx / dist) + nlook.z * (dz / dist);
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
