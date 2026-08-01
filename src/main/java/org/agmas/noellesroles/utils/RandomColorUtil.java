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

package org.agmas.noellesroles.utils;

public class RandomColorUtil {
    public int cachedColor = 0;
    private int refreshTime;
    private long lastRefresh;
    private boolean gradient;

    // 用于渐变效果的目标颜色和起始颜色
    private int targetColor;
    private int startColor;
    private long gradientStartTime;
    private static final int GRADIENT_DURATION = 1000; // 渐变持续时间，单位毫秒

    public RandomColorUtil(int refreshTime, boolean gradient) {
        this.refreshTime = refreshTime;
        this.gradient = gradient;
        this.lastRefresh = System.currentTimeMillis();
        this.targetColor = (int) (Math.random() * 0x1000000);
        this.startColor = this.targetColor;
        this.cachedColor = this.targetColor;
    }

    public int getOrRandomColor() {
        if (gradient) {
            return getGradientColor();
        } else {
            if (System.currentTimeMillis() - lastRefresh > refreshTime) {
                cachedColor = (int) (Math.random() * 0x1000000);
                lastRefresh = System.currentTimeMillis();
            }
            return cachedColor != 0 ? cachedColor : 0xFFFFFF;
        }
    }

    /**
     * 计算当前的渐变颜色
     * @return 当前渐变过程中的颜色值
     */
    private int getGradientColor() {
        long now = System.currentTimeMillis();
        
        // 如果渐变尚未开始或已结束，重新初始化渐变
        if (now - gradientStartTime >= GRADIENT_DURATION || startColor == targetColor) {
            startColor = targetColor;
            targetColor = (int) (Math.random() * 0x1000000);
            gradientStartTime = now;
        }

        // 计算渐变进度 (0.0 到 1.0)
        float progress = (float) (now - gradientStartTime) / GRADIENT_DURATION;
        if (progress > 1.0f) {
            progress = 1.0f;
        }

        // 分别提取 RGB 分量并进行插值
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int targetR = (targetColor >> 16) & 0xFF;
        int targetG = (targetColor >> 8) & 0xFF;
        int targetB = targetColor & 0xFF;

        int currentR = (int) (startR + (targetR - startR) * progress);
        int currentG = (int) (startG + (targetG - startG) * progress);
        int currentB = (int) (startB + (targetB - startB) * progress);

        // 合并回整数颜色值
        return (currentR << 16) | (currentG << 8) | currentB;
    }
}
