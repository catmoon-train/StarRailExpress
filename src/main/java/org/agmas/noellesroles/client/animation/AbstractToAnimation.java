package org.agmas.noellesroles.client.animation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.phys.Vec2;

/**
 * 抽象 To动作动画：
 * - 实现直接从start移动到end，使用绝对坐标
 * - 动画间会互相冲突
 */
abstract public class AbstractToAnimation extends AbstractAnimation{
    public interface Callback<T> {
        void onExecute(T param);
    }
    public AbstractToAnimation(AbstractWidget widget, Vec2 start, Vec2 end, int duration, ConstantSpeedAnimation.Callback<Vec2> callback) {
        super(widget, duration);
        this.start = start;
        this.end = end;
        this.callback = callback;
    }
    public AbstractToAnimation(AbstractWidget widget, Vec2 start, Vec2 end, int duration) {
        this(widget, start, end, duration,
                // 默认实现位置偏移动画
                (Vec2 pos) ->
                {
                    widget.setPosition((int)pos.x, (int)pos.y);
                });
    }
    abstract protected Vec2 calculateAns(float t);
    @Override
    public void update() {
        callback.onExecute(calculateAns(progress));
    }
    public void setCallback(ConstantSpeedAnimation.Callback<Vec2> callback) {
        this.callback = callback;
    }
    protected final Vec2 start, end;
    protected ConstantSpeedAnimation.Callback<Vec2> callback;
}
