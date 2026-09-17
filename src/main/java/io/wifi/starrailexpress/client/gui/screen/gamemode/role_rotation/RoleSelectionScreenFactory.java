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

package io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation;

import io.wifi.starrailexpress.content.vote.client.VolunteerOpenCache;
import net.minecraft.client.gui.screens.Screen;

/**
 * 开局选职业界面的统一入口：轮选模式与志愿海选模式共用同一套「轮选进行中」的判定，
 * 具体用哪个界面由当前模式决定。
 */
public final class RoleSelectionScreenFactory {

    private RoleSelectionScreenFactory() {
    }

    public static Screen create() {
        if (VolunteerOpenCache.isVolunteerOpenMode()) {
            return new VolunteerOpenSelectScreen();
        }
        return new RoleRotationScreen();
    }

    /**
     * 现在能不能把选职业界面顶出来。
     *
     * <p>
     * 志愿海选模式要求「开局运镜（地图的开场动画，含其收尾渐隐）播完」之后再显示界面——
     * 海选需要读职业、搜索、点选，不能盖在动画上面；运镜期间先让黑幕收掉、把画面露出来，
     * 动画结束后由 {@code SREClient} / 接收器自动打开界面。
     *
     * <p>
     * 职业轮选保持原有行为（本来就是运镜期间直接进界面）。
     */
    public static boolean canShowNow() {
        if (VolunteerOpenCache.isVolunteerOpenMode()) {
            return !net.exmo.sre.camera.client.AdvancedCameraDirector.isPresentationActive();
        }
        return true;
    }
}
