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

public class ProgressProvider {
    float max = 0;
    float now = 0;
    float min = 0;
    float percent = 0;

    public ProgressProvider(float now, float max) {
        this.max = max;
        this.now = now;
        this.min = 0;
        calcProgress();
    }

    public ProgressProvider(float min, float max, float now) {
        this.max = max;
        this.now = now;
        this.min = min;
        calcProgress();
    }

    public ProgressProvider setNow(float now) {
        this.now = now;
        calcProgress();
        return this;
    }

    public ProgressProvider setMin(float min) {
        this.min = min;
        calcProgress();
        return this;
    }

    public ProgressProvider setMax(float max) {
        this.max = max;
        calcProgress();
        return this;
    }

    public void calcProgress() {
        this.percent = (now - min) / (max - min);
    }

    public float getMin() {
        return this.min;
    }

    public float getMax() {
        return this.max;
    }

    public float getNow() {
        return this.now;
    }

    public float getPercent() {
        return this.percent;
    }

    public static ProgressProvider of(float min, float max, float now) {
        return new ProgressProvider(min, max, now);
    }

    public static ProgressProvider of(float now, float max) {
        return new ProgressProvider(now, max);
    }

    public static ProgressProvider of(float progress) {
        return new ProgressProvider(0, 1, progress);
    }
}
