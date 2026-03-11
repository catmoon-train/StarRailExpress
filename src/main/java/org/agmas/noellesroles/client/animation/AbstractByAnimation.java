package org.agmas.noellesroles.client.animation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.phys.Vec2;

/**
 * 抽象 by动作（相对运动）动画
 * - 该动画起点为(0,0)可以实现起点到终点的相对运动：适用于多个动画叠加且不冲突
 */
abstract public class AbstractByAnimation extends AbstractAnimation{
    public interface Callback<T> {
        void onExecute(T param);
    }
    public AbstractByAnimation(AbstractWidget widget, Vec2 end, int duration, Callback<Vec2> callback) {
        super(widget, duration);
        this.end = end;
        this.durationTicks = duration;
        this.callback = callback;
    }
    public AbstractByAnimation(AbstractWidget widget, Vec2 end, int duration) {
        this(widget, end, duration,
                // 默认实现位置偏移动画
                (Vec2 pos) ->
                {
                    widget.setPosition((int)pos.x + widget.getX(), (int)pos.y + widget.getY());
                });
    }
    abstract protected Vec2 calculateAns(float t);
    protected Vec2 calculateDeltaAns(float t) {
        // start x = 0, start y = 0
        Vec2 curVec = calculateAns(t);
        float ansX = curVec.x - lastX;
        float ansY = curVec.y - lastY;
        lastX = curVec.x;
        lastY = curVec.y;

        // 整数误差修正
        if(isIntErrorFixOpen) {
            // 保存误差
            deltaErrorX += ansX - (int) ansX;
            deltaErrorY += ansY - (int) ansY;
            // 误差修正
            if (deltaErrorX > 1) {
                --deltaErrorX;
                ++ansX;
            } else if (deltaErrorX < -1) {
                ++deltaErrorX;
                --ansX;
            }
            if (deltaErrorY > 1) {
                --deltaErrorY;
                ++ansY;
            } else if (deltaErrorY < -1) {
                ++deltaErrorY;
                --ansY;
            }
        }
        return new Vec2(ansX, ansY);
    }
    @Override
    public void update() {
        callback.onExecute(calculateDeltaAns(progress));
        if(progress >= 1f)
        {
            // 将剩余的误差进行修正
            if(isIntErrorFixOpen)
                callback.onExecute(new Vec2( deltaErrorX > 0.5f ? 1 : deltaErrorX < -0.5f ? -1 : deltaErrorX,
                        deltaErrorY > 0.5f ? 1 : deltaErrorY < -0.5f ? -1 : deltaErrorY));
        }
    }
    public void setCallback(Callback<Vec2> callback) {
        this.callback = callback;
    }
    /** 开启整数误差修正：使用整数时应当开启，浮点数时应当关闭 */
    public void openIntErrorFix() {
        isIntErrorFixOpen = true;
    }
    /** 关闭整数误差修正：使用浮点数时需要关闭 */
    public void closeIntErrorFix() {
        isIntErrorFixOpen = false;
    }

    protected final Vec2 end;// 目标值
    protected Callback<Vec2> callback;
    // 是否打开整数误差修正：用于回调修改为整数时的误差修正
    protected boolean isIntErrorFixOpen = true;
    protected float lastX = 0;
    protected float lastY = 0;
    // 误差：每次移动控件时被舍去的小数
    protected float deltaErrorX = 0;
    protected float deltaErrorY = 0;
}
