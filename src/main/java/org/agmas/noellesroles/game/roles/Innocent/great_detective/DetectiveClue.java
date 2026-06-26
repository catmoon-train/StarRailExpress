package org.agmas.noellesroles.game.roles.innocent.great_detective;

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
        /** 名字中带有的 2-3 个字（value = 名字片段字面量）。 */
        NAME,
        /** 凶手所在房间/车厢（value = 房间号）。 */
        ROOM;

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
