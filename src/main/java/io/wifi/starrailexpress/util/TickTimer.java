package io.wifi.starrailexpress.util;

import java.util.function.Consumer;

/**
 * 按tick执行的计时器
 */
public class TickTimer {
    /**
     * @param endTime 倒计时时长
     * @param isOneShoot 是否单次
     * @param onCompleteCallback 倒计时完成回调R
     */
    public TickTimer(int endTime, boolean isOneShoot, Runnable onCompleteCallback) {
        this.delayTime = 0;
        this.endTime = endTime;
        this.isOneShot = isOneShoot;
        this.isShot = false;
        this.isRunning = true;
        this.onCompleteCallback = onCompleteCallback;
    }
    /** 每tick运行定时器 */
    public void tick() {
        if(!isRunning || (isShot && isOneShot))
            return;
        ++delayTime;
        if (delayTime >= endTime) {
            if(onCompleteCallback != null)
                onCompleteCallback.run();
            if (!isShot)
                isShot = true;
            delayTime -= endTime;
        }
    }
    /** 重置计时器 */
    public void reSet(){
        delayTime = 0;
        isShot = false;
    }
    public void setOnCompleteCallback(Runnable onCompleteCallback) {
        this.onCompleteCallback = onCompleteCallback;
    }
    public boolean isShot() {
        return isShot;
    }
    public void setOneShot(boolean isOneShoot) {
        this.isOneShot = isOneShoot;
    }
    public void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
    }
    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }
    public boolean isFinished() {
        return isOneShot && isShot;
    }
    /** 当前执行时长 */
    protected int delayTime;
    /** 倒计时长 */
    protected int endTime;
    /** 是否单次 */
    protected boolean isOneShot;
    /** 是否已触发过 */
    protected boolean isShot;
    /** 是否运行中 */
    protected boolean isRunning;
    /** 倒计时完成回调 */
    protected Runnable onCompleteCallback;
}
