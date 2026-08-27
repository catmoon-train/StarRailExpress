package org.agmas.noellesroles.role.bouns.roles;

import org.agmas.noellesroles.init.InitModRolesMax;
import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.EggRoleInterface;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;

public class HengXingTiRole extends ExtraEffectRole implements EggRoleInterface {
    public static final int SKILL_DURATION = 15 * 20;
    public static final int SKILL_RANGE = 10;
    public static final double PULL_RANGE = 1;
    public static final double ROTATION_SPEED = 2 * Math.PI / 100; // 每 100 tick 转一圈

    public HengXingTiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime,
                ModEffects.of(MobEffects.GLOWING, 400, 0, true, false, true));
    }

    @Override
    public boolean canBeRandomed() {
        if (InitModRolesMax.isEggEnabled)
            return super.canBeRandomed();
        return false;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        if (getAbilityComponent(player).duration > 0) {
            final var world = player.serverLevel();
            if (world.getGameTime() % 10 == 5) {
                world.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY(), player.getZ(), 100, 1, 1,
                        1, 0.1);
            }
            if (world.getGameTime() % 2 == 1) {
                for (var p : world.players()) {
                    if (p.isSpectator())
                        continue;
                    if (p.isCreative())
                        continue;
                    if (p.getUUID().equals(player.getUUID()))
                        continue;
                    if (p.distanceToSqr(player) > SKILL_RANGE * SKILL_RANGE)
                        continue;
                    {
                        // 计算旋转角度：基础偏移 + 时间旋转
                        double baseAngle = (p.getUUID().hashCode() % 360) * (Math.PI / 180.0)
                                + (world.getGameTime() * ROTATION_SPEED) % (2 * Math.PI);
                        teleportPlayerToRound(player, p, baseAngle);
                    }
                }
            }
        }
    }

    /**
     * 将目标玩家传送到施法者周围指定半径的圆周上，角度由 baseAngle 决定，
     * 并尝试多个附近角度以避免碰撞。
     *
     * @param player    施法者
     * @param target    被传送的玩家
     * @param baseAngle 基础角度（弧度），已包含玩家偏移和时间旋转
     */
    private void teleportPlayerToRound(ServerPlayer player, ServerPlayer target, double baseAngle) {
        if (player == null || target == null)
            return;

        // 添加短暂控制效果，防止传送后立刻移动
        target.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, 20, 1, false, false, true));
        target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 20, 1, false, false, true));
        target.addEffect(ModEffects.of(ModEffects.SKILL_BANED, 20, 1, false, false, true));

        ServerLevel level = player.serverLevel();

        // 目标玩家碰撞箱尺寸
        var pose = target.getPose();
        var dimensions = target.getDimensions(pose);
        double width = dimensions.width();
        double height = dimensions.height();

        // 施法者位置（脚部坐标）
        double centerX = player.getX();
        double centerY = player.getY();
        double centerZ = player.getZ();

        double radius = PULL_RANGE;

        // 在 baseAngle 基础上尝试 8 个方向（每次增加 45 度）
        for (int i = 0; i < 8; i++) {
            double angle = baseAngle + i * (Math.PI / 4);
            double targetX = centerX + radius * Math.cos(angle);
            double targetZ = centerZ + radius * Math.sin(angle);
            double targetY = centerY; // 可根据需要调整 Y 轴

            AABB candidateBox = new AABB(
                    targetX - width / 2, targetY,
                    targetZ - width / 2,
                    targetX + width / 2, targetY + height,
                    targetZ + width / 2);

            if (level.noCollision(target, candidateBox)) {
                target.teleportTo(targetX, targetY, targetZ);
                float yaw = (float) Math.toDegrees(Math.atan2(targetZ - centerZ, targetX - centerX)) - 90.0F;
                target.setYRot(yaw);
                target.setXRot(0.0F); // 保持水平视线
                return;
            }
        }

        // 所有方向均无效时回退到施法者位置
        target.teleportTo(centerX, centerY, centerZ);
    }

    public static boolean triggerSkill(RoleSkillContext ctx) {
        final var player = ctx.player();
        SREArmorPlayerComponent.KEY.get(player).addTimedArmor(2, SKILL_DURATION, false);
        getAbilityComponent(player).setDuration(SKILL_DURATION);
        player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, SKILL_DURATION + 10, 1, false, false, true));
        return true;
    }
}
