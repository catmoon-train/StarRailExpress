package org.agmas.noellesroles.client.animation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.phys.Vec2;

/**
 * 三阶贝塞尔曲线动画
 * start为(0,0)作相对运动：以控件自身为原点
 */
public class BezierAnimation extends AbstractByAnimation {
    /**
     * 三阶贝塞尔动画建造者
     * <p>
     *     使用建造者进行构建可以更灵活地配置动画
     * </p>
     */
    public static class Builder {
        public Builder(AbstractWidget widget, Vec2 end, int durationTicks) {
            this.widget = widget;
            // 默认控制点0.1 0.9
            control1 = new Vec2(end.x * 0.1f,end.y * 0.1f);
            control2 = new Vec2(end.x * 0.9f, end.y * 0.9f);
            this.end = end;
            this.durationTicks = durationTicks;
            callback =
                    (Vec2 pos) ->
                    {
                        widget.setPosition((int)pos.x + widget.getX(), (int)pos.y + widget.getY());
                    };
        }
        public Builder setControl1(Vec2 control1) {
            this.control1 = control1;
            return this;
        }
        public Builder setControl2(Vec2 control2) {
            this.control2 = control2;
            return this;
        }
        public Builder setControl(Vec2 control1, Vec2 control2) {
            this.control1 = control1;
            this.control2 = control2;
            return this;
        }
        public Builder setCallback(Callback<Vec2> callback) {
            this.callback = callback;
            return this;
        }
        /** callBack 使用浮点数计算时请关闭整数误差修正 */
        public Builder setIntErrorFix(boolean isIntErrorFixOpen) {
            this.isIntErrorFixOpen = isIntErrorFixOpen;
            return this;
        }
        public BezierAnimation build() {
            BezierAnimation animation = new BezierAnimation(widget, control1, control2, end, durationTicks, callback);
            if (isIntErrorFixOpen) {
                animation.openIntErrorFix();
            }
            else
                animation.closeIntErrorFix();
            return animation;
        }
        protected AbstractWidget widget;
        protected Vec2 control1, control2;
        protected Vec2 end;
        protected int durationTicks;
        protected Callback<Vec2> callback;
        protected boolean isIntErrorFixOpen = true;
    }
    public static Builder builder(AbstractWidget widget, Vec2 end, int durationTicks) {
        return new Builder(widget, end, durationTicks);
    }
    public BezierAnimation(AbstractWidget widget, Vec2 control1, Vec2 control2, Vec2 end, int duration, Callback<Vec2> callback) {
        super(widget, end, duration, callback);
        this.control1 = control1;
        this.control2 = control2;
    }
    public BezierAnimation(AbstractWidget widget, Vec2 control1, Vec2 control2, Vec2 end, int duration) {
        super(widget, end, duration);
        this.control1 = control1;
        this.control2 = control2;
    }
    // 使用默认控制点
    public BezierAnimation(AbstractWidget widget, Vec2 end, int duration) {
        // 控制点靠近起点/终点，产生平滑开始/结束 10%/90%
        this(widget,
                new Vec2(end.x * 0.1f,end.y * 0.1f),new Vec2(end.x * 0.9f, end.y * 0.9f),
                end, duration);
    }
    public BezierAnimation(AbstractWidget widget, Vec2 end, int duration, Callback<Vec2> callback) {
        this(widget,
                new Vec2(end.x * 0.1f,end.y * 0.1f),new Vec2(end.x * 0.9f, end.y * 0.9f),
                end, duration, callback);
    }

    /** 三阶贝塞尔曲线计算*/
    @Override
    protected Vec2 calculateAns(float t) {
        float u = 1 - t;
        float tt = t * t;
        float uu = u * u;
        float uuu = uu * u;
        float ttt = tt * t;

        float x = uuu * 0/*start x*/ +
                3 * uu * t * control1.x +
                3 * u * tt * control2.x +
                ttt * end.x;
        float y = uuu * 0/*start y*/ +
                3 * uu * t * control1.y +
                3 * u * tt * control2.y +
                ttt * end.y;
        return new Vec2(x, y);
    }

    private final Vec2 control1, control2;
}
