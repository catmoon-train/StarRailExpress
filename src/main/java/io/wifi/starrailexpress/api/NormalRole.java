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

package io.wifi.starrailexpress.api;

import net.minecraft.resources.ResourceLocation;

public class NormalRole extends SRERole {
    public static enum RoleType {
        KILLER, CIVILIAN, VIGILANTE, NEUTRALS, NEUTRALS_FOR_KILLERS, NEUTRALS_FOR_INNOCENT
    }

    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public NormalRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.setPassiveIncome(canUseKiller);
        this.setNeutrals(isInnocent == false && canUseKiller == false);
    }

    /**
     * 另一种构造方法
     * 
     * @param identifier
     * @param color
     * @param roleType
     * @param moodType
     * @param maxSprintTime
     * @param canSeeTime
     */
    public NormalRole(ResourceLocation identifier, int color, RoleType roleType,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        // 必须第一行调用 this，参数直接用三元表达式计算
        this(identifier, color,
                roleType == RoleType.CIVILIAN || roleType == RoleType.VIGILANTE, // isInnocent
                roleType == RoleType.KILLER, // canUseKiller
                moodType, maxSprintTime, canSeeTime);

        // 根据 roleType 设置额外属性（这些 setter 方法来自父类 SRERole）
        if (roleType == RoleType.VIGILANTE) {
            setVigilanteTeam(true);
        }
        if (roleType == RoleType.NEUTRALS || roleType == RoleType.NEUTRALS_FOR_INNOCENT
                || roleType == RoleType.NEUTRALS_FOR_KILLERS) {
            setNeutrals(true);
        }
        if (roleType == RoleType.NEUTRALS_FOR_INNOCENT) {
            setNeutralForInnocent(true);
        }
        if (roleType == RoleType.NEUTRALS_FOR_KILLERS) {
            setNeutralForKiller(true);
        }
    }
}
