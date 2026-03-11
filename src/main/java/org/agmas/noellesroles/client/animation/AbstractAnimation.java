package org.agmas.noellesroles.client.animation;

import net.minecraft.client.gui.components.AbstractWidget;

public abstract class AbstractAnimation {
    protected AbstractAnimation(AbstractWidget widget, int durationTicks)
    {
        this.widget = widget;
        this.durationTicks = durationTicks;
    }
    public void renderUpdate(float deltaTime) {
        if(isFinished)
            return;
        // deltaTime的单位为0.10秒
        delayTime += deltaTime / 10f;
        while (delayTime >= secondPerTick && !isFinished)
        {
            progress += 1f / (float) durationTicks;
            progress = Math.min(progress, 1f);
            update();
            if(progress >= 1f)
                isFinished = true;
            delayTime -= secondPerTick;
        }
    }
    public void reStrt()
    {
        isFinished = false;
        reSet();
    }
    public void reSet()
    {
        delayTime = 0f;
        progress = 0f;
    }
    public boolean isFinished() {
        return isFinished;
    }
    public abstract void update();
    public void setDurationTicks(int durationTicks)
    {
        this.durationTicks = durationTicks;
    }
    public int getDurationTicks() {
        return durationTicks;
    }
    public AbstractWidget getWidget() {
        return widget;
    }

    public static final float secondPerTick = (float)1 / 20;
    protected final AbstractWidget widget;
    protected float delayTime = 0f;
    protected float progress = 0f;
    protected boolean isFinished = false;
    protected int durationTicks; // 动画持续时间（刻）
}
