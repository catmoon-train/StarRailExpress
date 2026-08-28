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

package io.wifi.starrailexpress.game.forensic;

import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.resources.ResourceLocation;

/**
 * 通用物证（第1批）：把具体的死因（DeathReason）归并成"凶器大类"。
 * 验尸官依旧能看到精确型号，全员只读这一层粗粒度——线索而非答案。
 *
 * <p>映射对所有人可用（数据本就同步给客户端），不修改任何通用类。
 */
public enum ForensicCategory {
    BLADE("forensic.category.blade"),
    FIREARM("forensic.category.firearm"),
    BLUNT("forensic.category.blunt"),
    PROJECTILE("forensic.category.projectile"),
    POISON("forensic.category.poison"),
    EXPLOSIVE("forensic.category.explosive"),
    ENVIRONMENT("forensic.category.environment"),
    UNKNOWN("forensic.category.unknown");

    public final String langKey;

    ForensicCategory(String langKey) {
        this.langKey = langKey;
    }

    /**
     * 根据死因解析凶器大类。未知/已知精确常量都做兜底，
     * 兼容以物品ID作为死因的自定义武器（基于路径关键字启发式）。
     */
    public static ForensicCategory fromDeathReason(ResourceLocation reason) {
        if (reason == null) {
            return UNKNOWN;
        }

        // 已知精确死因常量
        if (reason.equals(GameConstants.DeathReasons.KNIFE)) {
            return BLADE;
        }
        if (reason.equals(GameConstants.DeathReasons.REVOLVER)
                || reason.equals(GameConstants.DeathReasons.DERRINGER)
                || reason.equals(GameConstants.DeathReasons.SNIPER_RIFLE)
                || reason.equals(GameConstants.DeathReasons.SNIPER_RIFLE_BACKFIRE)
                || reason.equals(GameConstants.DeathReasons.ZERO_ONE_FIVE)
                || reason.equals(GameConstants.DeathReasons.BACKFIRE)) {
            return FIREARM;
        }
        if (reason.equals(GameConstants.DeathReasons.BAT)
                || reason.equals(GameConstants.DeathReasons.NUNCHUCK)) {
            return BLUNT;
        }
        if (reason.equals(GameConstants.DeathReasons.ARROW)
                || reason.equals(GameConstants.DeathReasons.TRIDENT)) {
            return PROJECTILE;
        }
        if (reason.equals(GameConstants.DeathReasons.POISON)) {
            return POISON;
        }
        if (reason.equals(GameConstants.DeathReasons.GRENADE)
                || reason.equals(GameConstants.DeathReasons.SELF_EXPLOSION)) {
            return EXPLOSIVE;
        }
        if (reason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)
                || reason.equals(GameConstants.DeathReasons.FALL_DAMAGE)
                || reason.equals(GameConstants.DeathReasons.TOUCH_INCORRECT)
                || reason.equals(GameConstants.DeathReasons.DEATH_IN_DARKNESS)
                || reason.equals(GameConstants.DeathReasons.CANNOT_SWIM)
                || reason.equals(GameConstants.DeathReasons.LOG_CRUSH)
                || reason.equals(GameConstants.DeathReasons.LAVA)
                || reason.equals(GameConstants.DeathReasons.DROWNED)
                || reason.equals(GameConstants.DeathReasons.MANHOLE_SUFFOCATION)
                || reason.equals(GameConstants.DeathReasons.STALACTITE_IMPALE)
                || reason.equals(GameConstants.DeathReasons.FLAMETHROWER_BURNED)
                || reason.equals(GameConstants.DeathReasons.BOULDER_CRUSH)
                || reason.equals(GameConstants.DeathReasons.INCINERATOR_PUSHED)) {
            return ENVIRONMENT;
        }

        // 兜底：路径关键字启发式（自定义武器死因常以物品ID出现）
        String p = reason.getPath();
        if (p.contains("knife") || p.contains("blade") || p.contains("dagger") || p.contains("sword")) {
            return BLADE;
        }
        if (p.contains("revolver") || p.contains("derringer") || p.contains("rifle")
                || p.contains("gun") || p.contains("shot") || p.contains("pistol") || p.contains("sniper")) {
            return FIREARM;
        }
        if (p.contains("bat") || p.contains("nunchuck") || p.contains("baton")
                || p.contains("club") || p.contains("hammer")) {
            return BLUNT;
        }
        if (p.contains("arrow") || p.contains("bow") || p.contains("trident")
                || p.contains("kunai") || p.contains("shuriken")) {
            return PROJECTILE;
        }
        if (p.contains("poison") || p.contains("infect") || p.contains("venom")) {
            return POISON;
        }
        if (p.contains("grenade") || p.contains("explos") || p.contains("c4")
                || p.contains("bomb") || p.contains("tnt")) {
            return EXPLOSIVE;
        }
        if (p.contains("fell") || p.contains("fire") || p.contains("burn") || p.contains("crush")
                || p.contains("suffocat") || p.contains("incinerat") || p.contains("impale")
                || p.contains("boulder") || p.contains("manhole") || p.contains("stalactite")) {
            return ENVIRONMENT;
        }
        return UNKNOWN;
    }
}
