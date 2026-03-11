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
