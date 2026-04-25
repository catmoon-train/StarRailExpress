package org.agmas.noellesroles.content.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import io.wifi.starrailexpress.game.GameUtils;

import java.util.List;

public class ShortShotgunItem extends Item {
    /** 最小蓄力时间：0.2秒 = 4刻 */
    private static final int MIN_CHARGE_TICKS = 4;

    public ShortShotgunItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
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
        // 使用 remainingUseTicks 判断蓄力是否完成
        // remainingUseTicks < getUseDuration - MIN_CHARGE_TICKS 表示已蓄力足够时间
        if (remainingUseTicks > this.getUseDuration(stack, user) - MIN_CHARGE_TICKS) {
            // 蓄力不足，直接停止使用
            return;
        }

        Player player = (Player) user;
        ServerLevel serverLevel = (ServerLevel) world;

        // 播放射击音效
        world.playSound(null, player.blockPosition(), NRSounds.SHOTGUN_FIRE, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 生成烈焰弹粒子效果
        spawnFlameParticles(serverLevel, player);

        double radius = 2.0D;
        AABB box = new AABB(player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                player.getX() + radius, player.getY() + radius, player.getZ() + radius);
        List<Player> nearby = world.getEntitiesOfClass(Player.class, box,
                p -> p != player && GameUtils.isPlayerAliveAndSurvival(p));

        Vec3 look = player.getLookAngle();
        double cosThreshold = Math.cos(Math.toRadians(35.0)); // 70度扇形
        for (Player target : nearby) {
            Vec3 dir = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
            double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (len == 0)
                continue;
            Vec3 ndir = dir.scale(1.0 / len);
            Vec3 l2 = new Vec3(look.x, 0, look.z);
            double llen = Math.sqrt(l2.x * l2.x + l2.z * l2.z);
            if (llen == 0)
                continue;
            Vec3 nlook = l2.scale(1.0 / llen);
            double dot = nlook.x * ndir.x + nlook.z * ndir.z;
            if (dot >= cosThreshold && canSeeTarget(world, player, target)) {
                io.wifi.starrailexpress.game.GameUtils.killPlayer(target, true, player,
                        Noellesroles.id("short_shotgun"));
            }
        }

        if (!player.isCreative()) {
            InteractionHand usedHand = player.getUsedItemHand();
            stack.hurtAndBreak(1, player,
                    usedHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.getCooldowns().addCooldown(ModItems.SHORT_SHOTGUN, 30 * 20);
        }
    }

    /**
     * 生成烈焰弹粒子效果
     */
    private void spawnFlameParticles(ServerLevel serverLevel, Player player) {
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