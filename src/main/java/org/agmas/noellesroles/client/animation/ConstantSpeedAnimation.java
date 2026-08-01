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

package org.agmas.noellesroles.client.animation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.phys.Vec2;

/**
 * 匀速动画
 * - 只需要输入目标值和持续时间即可
 */
public class ConstantSpeedAnimation extends AbstractByAnimation{
    public ConstantSpeedAnimation(AbstractWidget widget, Vec2 end, int duration, Callback<Vec2> callback) {
        super(widget, end, duration, callback);
    }
    public ConstantSpeedAnimation(AbstractWidget widget, Vec2 end, int duration) {
        super(widget, end, duration);
    }
    @Override
    protected Vec2 calculateAns(float t) {
        return new Vec2(end.x * t, end.y * t);
    }
}
