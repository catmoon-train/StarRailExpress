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

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 咸鱼「晒咸鱼」时的假尸体。
 *
 * <p>这个实体只作为客户端渲染用的壳：由 {@code PlayerBodyDisguiseRenderer} 创建、不入世界，
 * 位置每帧复制本体，再由 {@code SaltedFishBodyRenderMixin} 摆成躺姿与翻身动画。
 * 因此这里关闭重力、且不可被推动。
 */
public class SaltedFishBodyEntity extends PlayerBodyEntity {
    public SaltedFishBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(true);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // 不推挤其他实体，也不被推挤（配合 isPushable=false 保持与本体位置一致）
    }
}
