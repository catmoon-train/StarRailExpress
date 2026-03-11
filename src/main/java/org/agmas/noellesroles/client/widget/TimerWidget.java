package org.agmas.noellesroles.client.widget;

import java.util.function.Consumer;

public class TimerWidget {
    public TimerWidget(float endTime, boolean isOneShoot, Consumer<TimerWidget> onCompleteCallback) {
        this.delayTime = 0;
        this.endTime = endTime;
        this.isOneShoot = isOneShoot;
        this.isShoot = false;
        this.isRunning = true;
        this.onCompleteCallback = onCompleteCallback;
    }
    public void onRenderUpdate(float deltaTime) {
        if(!isRunning || (isShoot && isOneShoot))
            return;
        delayTime += deltaTime / 10f;
        if (delayTime >= endTime) {
            if(onCompleteCallback != null)
                onCompleteCallback.accept(this);
            if (isOneShoot) {
                isShoot = true;
            }
            delayTime -= endTime;
        }
    }
    public void reSet(){
        delayTime = 0;
        isShoot = false;
    }
    public void setOnCompleteCallback(Consumer<TimerWidget> onCompleteCallback) {
        this.onCompleteCallback = onCompleteCallback;
    }
    public void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
    }
    public void setEndTime(float endTime) {
        this.endTime = endTime;
    }
    public boolean isFinished() {
        return isOneShoot && isShoot;
    }
    protected float delayTime;
    protected float endTime;// seconds
    protected boolean isOneShoot;
    protected boolean isShoot;
    protected boolean isRunning;
    protected Consumer<TimerWidget> onCompleteCallback;
}
