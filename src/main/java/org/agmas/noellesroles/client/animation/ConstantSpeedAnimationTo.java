package org.agmas.noellesroles.client.animation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.phys.Vec2;

public class ConstantSpeedAnimationTo extends AbstractToAnimation {
    public ConstantSpeedAnimationTo(AbstractWidget widget, Vec2 start, Vec2 end, int duration, ConstantSpeedAnimation.Callback<Vec2> callback) {
        super(widget, start, end, duration, callback);
    }
    public ConstantSpeedAnimationTo(AbstractWidget widget, Vec2 start, Vec2 end, int duration) {
        super(widget, start, end, duration);
    }

    @Override
    protected Vec2 calculateAns(float t) {
        return new Vec2(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t
        );
    }
}
