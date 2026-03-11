package org.agmas.noellesroles.client.widget;

import java.util.function.Consumer;

public class TickTimerWidget {
    public TickTimerWidget(int endTime, boolean isOneShoot, Consumer<TickTimerWidget> onCompleteCallback) {
        this.delayTime = 0;
        this.endTime = endTime;
        this.isOneShoot = isOneShoot;
        this.isShoot = false;
        this.isRunning = true;
        this.onCompleteCallback = onCompleteCallback;
    }
    public void tick() {
        if(!isRunning || (isShoot && isOneShoot))
            return;
        ++delayTime;
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
    public void setOnCompleteCallback(Consumer<TickTimerWidget> onCompleteCallback) {
        this.onCompleteCallback = onCompleteCallback;
    }
    public boolean isShoot() {
        return isShoot;
    }
    public void setOneShoot(boolean isOneShoot) {
        this.isOneShoot = isOneShoot;
    }
    public void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
    }
    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }
    public boolean isFinished() {
        return isOneShoot && isShoot;
    }
    protected int delayTime;
    protected int endTime;// ticks
    protected boolean isOneShoot;
    protected boolean isShoot;
    protected boolean isRunning;
    protected Consumer<TickTimerWidget> onCompleteCallback;
}
