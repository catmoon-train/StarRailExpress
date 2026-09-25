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

package org.agmas.noellesroles.content.block.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 弹跳菇：玩家从高处落在上面时，把下落速度反转为向上，弹回接近原高度（同原版粘液块）。
 *
 * <p>
 * 与原版粘液块的区别：
 * <ul>
 * <li>反弹力度带轻微衰减（{@link #BOUNCE_FACTOR}），连续弹跳会慢慢变低，不会一直弹下去；</li>
 * <li><b>不做水平减速</b>：没有覆写 {@code stepOn}，走在上面与普通方块手感一致；</li>
 * <li>潜行时（{@code isSuppressingBounce}）不反弹，可以正常站在上面。</li>
 * </ul>
 */
public class BounceMushroomBlock extends Block {

    /**
     * 反弹力度系数：1.0 = 完全弹回原高度；略小于 1 表示力度稍有衰减，
     * 让「弹回接近原高度」的同时不会无限弹跳。
     */
    private static final double BOUNCE_FACTOR = 0.95D;

    public BounceMushroomBlock(Properties settings) {
        super(settings);
    }

    /**
     * 落到弹跳菇上不结算摔落伤害（同原版粘液块）：
     * 否则每一次弹跳落地都会吃一次摔落伤害。
     */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (entity.isSuppressingBounce()) {
            super.fallOn(level, state, pos, entity, fallDistance);
        } else {
            entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
        }
    }

    /** 落地瞬间把向下的速度反转为向上，实现弹跳。 */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
        } else {
            bounceUp(entity);
        }
    }

    private void bounceUp(Entity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.y < 0.0D) {
            // 生物用配置的衰减力度，其它实体沿用原版粘液块的 0.8
            double factor = entity instanceof LivingEntity ? BOUNCE_FACTOR : 0.8D;
            entity.setDeltaMovement(velocity.x, -velocity.y * factor, velocity.z);
        }
    }
}
