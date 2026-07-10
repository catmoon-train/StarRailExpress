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
    // 标记：该物品（赞助者 plush）替代了开局信封，右键应打开游戏介绍 GUI 而非放置方块
    DataComponentType<Boolean> SPONSOR_INTRO = register("sponsor_intro",
            stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

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
