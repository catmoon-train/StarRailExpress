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

package net.exmo.sre.loading;

import java.util.List;

/**
 * 开局免责声明的翻译键与章节元数据（不含 Minecraft 客户端类型，便于单测）。
 */
public final class GameDisclaimerContent {

    public static final String HEADER = "gui.sre.disclaimer.header";
    public static final String SUBTITLE = "gui.sre.disclaimer.subtitle";
    public static final String HINT = "gui.sre.disclaimer.hint";
    public static final String CONFIRM = "gui.sre.disclaimer.confirm";

    public static final String HEALTH_TITLE = "gui.sre.disclaimer.health.title";
    public static final String HEALTH_BODY = "gui.sre.disclaimer.health.body";
    public static final String EPILEPSY_TITLE = "gui.sre.disclaimer.epilepsy.title";
    public static final String EPILEPSY_BODY = "gui.sre.disclaimer.epilepsy.body";
    public static final String OPENSOURCE_TITLE = "gui.sre.disclaimer.opensource.title";
    public static final String OPENSOURCE_BODY = "gui.sre.disclaimer.opensource.body";
    public static final String FANWORK_TITLE = "gui.sre.disclaimer.fanwork.title";
    public static final String FANWORK_BODY = "gui.sre.disclaimer.fanwork.body";

    /** 健康忠告：金色 */
    public static final int HEALTH_COLOR = 0xFFD4AF37;
    /** 光敏性癫痫：警示红 */
    public static final int EPILEPSY_COLOR = 0xFFE06B65;
    /** 开源免费：确认绿 */
    public static final int OPENSOURCE_COLOR = 0xFF72C17B;
    /** 二创声明：功能蓝 */
    public static final int FANWORK_COLOR = 0xFF5EB7D8;

    public record Section(String titleKey, String bodyKey, int titleColor) {}

    private GameDisclaimerContent() {}

    public static List<Section> sections() {
        return List.of(
                new Section(HEALTH_TITLE, HEALTH_BODY, HEALTH_COLOR),
                new Section(EPILEPSY_TITLE, EPILEPSY_BODY, EPILEPSY_COLOR),
                new Section(OPENSOURCE_TITLE, OPENSOURCE_BODY, OPENSOURCE_COLOR),
                new Section(FANWORK_TITLE, FANWORK_BODY, FANWORK_COLOR));
    }

    public static List<String> allKeys() {
        return List.of(
                HEADER, SUBTITLE, HINT, CONFIRM,
                HEALTH_TITLE, HEALTH_BODY,
                EPILEPSY_TITLE, EPILEPSY_BODY,
                OPENSOURCE_TITLE, OPENSOURCE_BODY,
                FANWORK_TITLE, FANWORK_BODY);
    }

    /** 文本相对中轴线的绘制 X，保证换行后仍居中。 */
    public static int centerX(int axisX, int textWidth) {
        return axisX - textWidth / 2;
    }
}
