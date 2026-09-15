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

package io.wifi.starrailexpress.index;

import com.mojang.serialization.Codec;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.component.SREWritableBookContent;
import io.wifi.starrailexpress.content.item.component.SREWrittenBookContent;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface SREDataComponentTypes {
    DataComponentType<String> POISONER = register("poisoner",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<Boolean> TRAY_ITEM = register("from_tray_item",
            stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<Boolean> STATUS = register("status",
            stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<Boolean> FAKE_POISON = register("fake_poison",
            stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<String> ARMORER = register("armorer",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<String> WEAK_ARMORER = register("weak_armorer",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<Boolean> USED = register("used",
            stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<String> OWNER = register("owner",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** 自定义手铐的「施加者」UUID：被铐住玩家的定时指令里 {@code <attacker>} 靠它解析。 */
    DataComponentType<String> CUFF_APPLIER = register("cuff_applier",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<String> SKIN = register("skin",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<ResourceLocation> TEXTURE = register("texture",
            (builder) -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    DataComponentType<Boolean> SCOPE_ATTACHED = register("scope_attached",
            stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<Integer> AMMO_COUNT = register("ammo_count",
            stringBuilder -> stringBuilder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    DataComponentType<Integer> WEAPON_USED_TIME = register("weapon_used_time",
            stringBuilder -> stringBuilder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** 自定义列车物品的编号（同一个 custom_item 物品靠它区分不同自定义物品）。 */
    DataComponentType<String> CUSTOM_ITEM_ID = register("custom_item_id",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /**
     * 自定义列车物品的「已用次数」（耐久消耗）。
     *
     * <p>
     * 刻意<b>不占用</b>原版 {@code MAX_DAMAGE} 组件：所有自定义列车物品共用同一个注册物品，
     * 耐久上限只能实时读配置（改完配置重载立即生效），所以物品堆上只存「已经用了几次」，
     * 耐久条由 {@code CustomItem} 的 {@code isBarVisible/getBarWidth/getBarColor} 自己画。
     */
    DataComponentType<Integer> CUSTOM_ITEM_DAMAGE = register("custom_item_damage",
            stringBuilder -> stringBuilder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** 自定义方块的编号（同一个 custom_block 方块 / 物品靠它区分不同自定义方块）。 */
    DataComponentType<String> CUSTOM_BLOCK_ID = register("custom_block_id",
            stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private static <T> DataComponentType<T> register(String name,
            @NotNull UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SRE.id(name),
                builderOperator.apply(DataComponentType.builder()).build());
    }

    // 更多字数限制的书编辑component
    public static DataComponentType<SREWritableBookContent> WRITABLE_BOOK_CONTENT = register("writable_book_content",
            (builder) -> builder.persistent(SREWritableBookContent.CODEC)
                    .networkSynchronized(SREWritableBookContent.STREAM_CODEC).cacheEncoding());
    public static final DataComponentType<SREWrittenBookContent> WRITTEN_BOOK_CONTENT = register("written_book_content",
            (builder) -> builder.persistent(SREWrittenBookContent.CODEC)
                    .networkSynchronized(SREWrittenBookContent.STREAM_CODEC).cacheEncoding());
}
