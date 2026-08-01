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
            isShoot = true;
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
