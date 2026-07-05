package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 绳索
 * <p>
 * - 2点耐久
 * - 右键：将前方直线距离12格内你瞄准的玩家拉到自己身前
 * - 每次右键后进入3秒冷却，成功拉取且非创造模式时进入5秒冷却并消耗1点耐久
 * </p>
 */
public class RopeItem extends Item implements AdventureUsable {
    private static final int MAX_DURABILITY = 2;
    private static final int COOLDOWN = 3 * 20; // 每次右键3秒
    private static final int SUCCESS_COOLDOWN = 5 * 20; // 成功拉取且非创造模式5秒
    private static final int MAX_DISTANCE = 12; // 最大距离12格
    private static final int TARGET_IMMUNITY_DURATION = 10 * 20; // 被拉取后10秒免疫

    // 存储玩家被拉取的时间（UUID -> 时间戳）
    private static final java.util.Map<java.util.UUID, Long> ropeImmunityMap = new java.util.HashMap<>();

    public RopeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查耐久（耐久值为2时已损坏，不能使用）
        if (stack.getDamageValue() >= MAX_DURABILITY) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.rope.no_durability")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 检查冷却
        if (player.getCooldowns().isOnCooldown(this)) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.rope.on_cooldown")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!world.isClientSide && !player.isCreative()) {
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }

        // 查找前方直线距离12格内你瞄准的玩家
        Player target = findTargetedPlayerInView(world, player, MAX_DISTANCE);

        if (target == null) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.rope.no_target")
                                .withStyle(ChatFormatting.YELLOW),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!world.isClientSide) {
            // 记录绳索拉回事件（低频关键事件），替代通用物品使用记录以避免重复刷屏
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordRopePull(player.getUUID(), target.getUUID());
            }

            // 成功拉取后，非创造模式恢复为原有的5秒冷却并消耗耐久
            if (!player.isCreative()) {
                player.getCooldowns().addCooldown(this, SUCCESS_COOLDOWN);
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            // 将目标玩家拉到玩家身前
            pullPlayer(player, target, 1.5);

            // 标记目标玩家，10秒内无法被再次拉取
            ropeImmunityMap.put(target.getUUID(), System.currentTimeMillis());

            // 生成粒子效果
            spawnRopeParticles(world, player, target);

            // 播放声音
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0f, 1.0f);

            player.displayClientMessage(
                    Component.translatable("item.noellesroles.rope.success")
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        return InteractionResultHolder.success(stack);
    }

    /**
     * 查找前方直线距离12格内你瞄准的玩家（使用视线投射）
     */
    public static Player findTargetedPlayerInView(Level world, Player player, double bestDistance) {
        Player targetedPlayer = null;

        // 获取玩家视线起点和方向
        var eyePos = player.getEyePosition(1.0f);
        var viewVector = player.getViewVector(1.0f);

        for (Player target : world.players()) {
            // 跳过自己
            if (target == player)
                continue;

            // 跳过死亡的玩家
            if (!target.isAlive())
                continue;

            // 跳过观察者模式
            if (target.isSpectator())
                continue;

            // 跳过正在免疫冷却中的玩家
            Long lastPulledTime = ropeImmunityMap.get(target.getUUID());
            if (lastPulledTime != null) {
                long timeSincePulled = System.currentTimeMillis() - lastPulledTime;
                if (timeSincePulled < TARGET_IMMUNITY_DURATION * 50) { // 20 tick = 1秒
                    continue;
                }
            }

            // 计算距离
            double distance = player.distanceTo(target);
            if (distance > MAX_DISTANCE)
                continue;

            // 获取目标实体的碰撞箱（稍微扩大一点以提高命中率）
            var targetBB = target.getBoundingBox().inflate(0.5);

            // 检查视线是否与目标的碰撞箱相交
            if (isLineIntersectsBox(eyePos, viewVector, targetBB, distance)) {
                var toEyePos = target.position().subtract(eyePos);

                // 计算视线方向上的投影距离
                double projection = toEyePos.dot(viewVector);
                var closestPointOnRay = eyePos.add(viewVector.scale(projection));
                double perpendicularDistance = target.position().distanceTo(closestPointOnRay);

                // 如果垂直距离小于2.0格，说明准心对准了目标
                if (perpendicularDistance < 2.0 && distance < bestDistance) {
                    bestDistance = distance;
                    targetedPlayer = target;
                }
            }
        }

        return targetedPlayer;
    }

    /**
     * 检查射线是否与方块相交（简化版）
     */
    public static boolean isLineIntersectsBox(net.minecraft.world.phys.Vec3 rayOrigin,
            net.minecraft.world.phys.Vec3 rayDirection,
            net.minecraft.world.phys.AABB box,
            double maxDistance) {
        // 使用方块裁剪算法的简化版本
        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;

        // 检查X轴
        if (Math.abs(rayDirection.x) < 1e-6) {
            if (rayOrigin.x < box.minX || rayOrigin.x > box.maxX) {
                return false;
            }
        } else {
            double t1 = (box.minX - rayOrigin.x) / rayDirection.x;
            double t2 = (box.maxX - rayOrigin.x) / rayDirection.x;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // 检查Y轴
        if (Math.abs(rayDirection.y) < 1e-6) {
            if (rayOrigin.y < box.minY || rayOrigin.y > box.maxY) {
                return false;
            }
        } else {
            double t1 = (box.minY - rayOrigin.y) / rayDirection.y;
            double t2 = (box.maxY - rayOrigin.y) / rayDirection.y;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // 检查Z轴
        if (Math.abs(rayDirection.z) < 1e-6) {
            if (rayOrigin.z < box.minZ || rayOrigin.z > box.maxZ) {
                return false;
            }
        } else {
            double t1 = (box.minZ - rayOrigin.z) / rayDirection.z;
            double t2 = (box.maxZ - rayOrigin.z) / rayDirection.z;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // 检查是否相交且在最大距离内
        return tMax >= tMin && tMin >= 0 && tMin <= maxDistance;
    }

    /**
     * 将目标玩家拉到玩家身前，并避免卡入方块。
     * 从玩家位置开始逐步向外尝试，找到首个无效位置后回退到上一个有效位置。
     *
     * @param player 执行拉取操作的玩家
     * @param target 被拉取的目标玩家
     */
    public static void pullPlayer(Player player, Player target, double maxDistance) {
        // 记录拉拽者为最近攻击者，使被搜救绳拉入列车碾压区的死亡能归属到拉人者（拉拽本身不造成伤害）。
        target.setLastHurtByMob(player);
        if (player instanceof ServerPlayer) {
            target.setLastHurtByPlayer(player);
        }

        var viewVector = player.getViewVector(1.0f);
        double step = 0.2; // 步长
        int steps = (int) Math.ceil(maxDistance / step) + 1;

        Level level = player.level();
        AABB lastValidBox = null;
        var lastValidPos = player.position(); // 默认回退位置（玩家自身）

        // 从距离 0 开始，逐步增加距离
        for (int i = 0; i <= steps; i++) {
            double currentDistance = Math.min(i * step, maxDistance);
            var targetPos = player.position().add(
                    viewVector.x * currentDistance,
                    0,
                    viewVector.z * currentDistance);

            // 获取目标玩家碰撞箱
            var pose = target.getPose();
            var dimensions = target.getDimensions(pose);
            double width = dimensions.width();
            double height = dimensions.height();
            var candidateBox = new AABB(
                    targetPos.x - width / 2, targetPos.y,
                    targetPos.z - width / 2,
                    targetPos.x + width / 2, targetPos.y + height,
                    targetPos.z + width / 2);

            // 检查该位置是否有效（无方块碰撞、无其他实体碰撞，排除 target 自身）
            if (level.noCollision(target, candidateBox)) {
                // 有效：记录为最后一个有效位置
                lastValidBox = candidateBox;
                lastValidPos = targetPos;
            } else {
                // 遇到第一个无效位置 -> 使用上一个有效位置（如果存在）
                if (lastValidBox != null) {
                    teleportPlayer(target, lastValidPos.x, lastValidPos.y, lastValidPos.z);
                } else {
                    // 连距离 0 都无效（极罕见，例如目标与玩家完全重叠且玩家实体无法忽略？）
                    // 回退到玩家位置
                    var fallbackPos = player.position();
                    teleportPlayer(target, fallbackPos.x, fallbackPos.y, fallbackPos.z);
                }
                return;
            }
        }

        // 所有尝试距离均有效 -> 使用最远距离（maxDistance）
        teleportPlayer(target, lastValidPos.x, lastValidPos.y, lastValidPos.z);
    }

    /**
     * 通用的玩家传送方法。
     */
    public static void teleportPlayer(Player target, double x, double y, double z) {
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.teleportTo(x, y, z);
        } else {
            target.moveTo(x, y, z);
        }
    }

    /**
     * 生成绳子拉拽的粒子效果
     */
    private void spawnRopeParticles(Level world, Player player, Player target) {
        if (!(world instanceof ServerLevel serverLevel))
            return;

        // 在玩家和目标之间生成绳索粒子
        int particleCount = 20; // 粒子数量

        for (int i = 0; i < particleCount; i++) {
            // 计算从玩家到目标的插值位置
            double ratio = i / (double) particleCount;
            var particlePos = player.position().lerp(target.position(), ratio);

            // 添加一些随机偏移，让粒子更自然
            double offsetX = (world.random.nextDouble() - 0.5) * 0.3;
            double offsetY = (world.random.nextDouble() - 0.5) * 0.3 + 0.5; // 稍微向上
            double offsetZ = (world.random.nextDouble() - 0.5) * 0.3;

            particlePos = particlePos.add(offsetX, offsetY, offsetZ);

            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    particlePos.x,
                    particlePos.y,
                    particlePos.z,
                    1, // 每个位置 1 个粒子
                    0.0, 0.0, 0.0, // 无速度
                    0.0 // 无额外参数
            );
        }

        // 在玩家位置生成烟雾粒子
        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                10, // 10 个烟雾粒子
                0.3, 0.3, 0.3, // 扩散范围
                0.02 // 粒子速度
        );

        // 在目标位置生成烟雾粒子
        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                target.getX(),
                target.getY() + 1.0,
                target.getZ(),
                10, // 10 个烟雾粒子
                0.3, 0.3, 0.3, // 扩散范围
                0.02 // 粒子速度
        );

        // 生成拉力线粒子（云团，表示力量）
        for (int i = 0; i < 5; i++) {
            var midPos = player.position().lerp(target.position(), 0.5);
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    midPos.x,
                    midPos.y + 1.0,
                    midPos.z,
                    1,
                    (world.random.nextDouble() - 0.5) * 0.5,
                    (world.random.nextDouble() - 0.5) * 0.5,
                    (world.random.nextDouble() - 0.5) * 0.5,
                    0.0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.rope.tooltip.durability",
                MAX_DURABILITY - stack.getDamageValue(), MAX_DURABILITY)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.rope.tooltip.use")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
