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

package org.agmas.noellesroles.game.roles.innocence.great_detective;

import net.minecraft.nbt.CompoundTag;

/**
 * 大侦探"推理之书"中的一条线索。
 *
 * <p>线索只携带"类型 + 原始值"，文本本地化交给客户端
 * （{@code org.agmas.noellesroles.client.screen.DeductionClueText}）处理，
 * 避免在服务端按服务器语言硬编码字符串。
 */
public record DetectiveClue(ClueType type, String value) {

    /** 线索类型。 */
    public enum ClueType {
        /** 凶手携带的修饰符（value = 修饰符 ResourceLocation 字符串）。 */
        MODIFIER,
        /** 使用的凶器大类（value = {@code ForensicCategory} 枚举名）。 */
        WEAPON,
        /** 具体职业（value = 职业 ResourceLocation 字符串）。 */
        ROLE,
        /** 名字中带有的 1 个字（value = 名字片段字面量）。 */
        NAME,
        /** 凶手所在房间/车厢（value = 房间号）。 */
        ROOM,
        /** 阵营：killer / neutral / civilian。 */
        FACTION,
        /** 主手持物大类：empty / blade / firearm / blunt / other。 */
        HELD,
        /** 游戏内金币档：empty / low / mid / high。 */
        GOLD,
        /** 场上能对上的尸体数量（value = 数字）。 */
        KILLS,
        /** 周围 8 格内其他存活玩家数量（value = 数字）。 */
        NEARBY,
        /** 相对楼层：upper / lower。 */
        FLOOR,
        /** 背包里是否有枪：yes / no。 */
        GUN,
        /** 相对侦探的距离档：close / mid / far。 */
        RANGE;

        public static ClueType byName(String name) {
            for (ClueType t : values()) {
                if (t.name().equals(name)) {
                    return t;
                }
            }
            return WEAPON;
        }
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("value", value);
        return tag;
    }

    public static DetectiveClue fromNbt(CompoundTag tag) {
        return new DetectiveClue(ClueType.byName(tag.getString("type")), tag.getString("value"));
    }

    /** 同类型同值视为同一条线索（用于去重）。 */
    public boolean sameAs(DetectiveClue other) {
        return other != null && other.type == this.type && other.value.equals(this.value);
    }
}
