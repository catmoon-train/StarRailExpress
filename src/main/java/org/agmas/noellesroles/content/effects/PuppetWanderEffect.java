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

package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * 傀儡游走（操控者·失控之躯）。
 *
 * <p>拥有者的身体每 tick 自动朝一个随机方向缓慢走动，方向每约 0.75s 变化一次，
 * 拥有者自身无法控制（配合 {@code MOVE_BANED/TURN_BANED/USED_BANED/SKILL_BANED/BLACK_MONITOR}
 * 一并施加），看起来就像被无形之手牵着四处乱走。为避免"被走进虚空/水/岩浆自杀"，
 * 移动带有一个悬崖/危险探测：只有当前进方向落脚点下方是实心方块时才迈步，
 * 否则本 tick 原地不动，等待下一个随机方向。
 *
 * <p>该效果替代操控者早期"远程直接驾驶"（{@code InControlCCA}）的玩法——
 * 那套代码已弃用但保留（见 {@code ManipulatorPlayerComponent}）。
 *
 * <p>纯服务端驱动：与被操控者侧一样在移动后用 {@code connection.teleport} 把权威坐标推回客户端，
 * 拥有者处于 {@code MOVE_BANED}，不会与之争抢输入。
 */
public class PuppetWanderEffect extends MobEffect {

    /** 每 tick 水平移动速度（格）。 */
    private static final double SPEED = 0.12D;
    /** 随机方向的刷新周期（tick）。 */
    private static final long HEADING_BUCKET_TICKS = 15L;

    public PuppetWanderEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A5ACD);
    }

    @Override
    public boolean isEnabled(FeatureFlagSet featureFlagSet) {
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (!(livingEntity instanceof ServerPlayer sp)) {
            return true;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            return true;
        }
        Level level = sp.level();

        // 随机朝向：按时间桶取种子，方向每 HEADING_BUCKET_TICKS 变化一次（不需要 per-entity 状态）
        long bucket = level.getGameTime() / HEADING_BUCKET_TICKS;
        long seed = sp.getUUID().getMostSignificantBits() ^ (bucket * 0x9E3779B97F4A7C15L);
        float yaw = new Random(seed).nextFloat() * 360.0F - 180.0F;
        double rad = Math.toRadians(yaw);
        double dirX = -Math.sin(rad);
        double dirZ = Math.cos(rad);

        // 面向行走方向（拥有者处于 TURN_BANED，不会自己转视角）
        sp.setYRot(yaw);
        sp.setYHeadRot(yaw);

        // 悬崖 / 危险探测：只有前方落脚点下方是实心方块才迈步
        boolean stepAllowed = true;
        if (sp.onGround()) {
            double nx = sp.getX() + dirX * 0.6D;
            double nz = sp.getZ() + dirZ * 0.6D;
            BlockPos below = BlockPos.containing(nx, sp.getY() - 0.2D, nz);
            stepAllowed = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
        }

        double horizontalX = stepAllowed ? dirX * SPEED : 0.0D;
        double horizontalZ = stepAllowed ? dirZ * SPEED : 0.0D;
        double vertical = sp.onGround() ? 0.0D : -0.12D;

        Vec3 delta = new Vec3(horizontalX, vertical, horizontalZ);
        sp.move(MoverType.SELF, delta);
        sp.setDeltaMovement(horizontalX, vertical, horizontalZ);
        sp.hasImpulse = true;

        if (horizontalX != 0.0D || horizontalZ != 0.0D) {
            // 推送权威坐标到拥有者客户端（其处于 MOVE_BANED，不会与之争抢）
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
        }
        return true;
    }
}
