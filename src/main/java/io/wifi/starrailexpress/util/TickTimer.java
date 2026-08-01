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
    public TickTimer(int endTime, boolean isOneShoot, Consumer<TickTimer> onCompleteCallback) {
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
                onCompleteCallback.accept(this);
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
    public void setOnCompleteCallback(Consumer<TickTimer> onCompleteCallback) {
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
    protected Consumer<TickTimer> onCompleteCallback;
}
