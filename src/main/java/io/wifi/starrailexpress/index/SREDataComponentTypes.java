package io.wifi.starrailexpress.index;

import com.mojang.serialization.Codec;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface SREDataComponentTypes {
    DataComponentType<String> POISONER = register("poisoner", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<String> ARMORER = register("armorer", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<Boolean> USED = register("used", stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<String> OWNER = register("owner", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<String> SKIN = register("skin", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<Boolean> SCOPE_ATTACHED = register("scope_attached", stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<Integer> AMMO_COUNT = register("ammo_count", stringBuilder -> stringBuilder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    DataComponentType<Integer> WEAPON_USED_TIME = register("weapon_used_time", stringBuilder -> stringBuilder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    // 标记：该物品（赞助者 plush）替代了开局信封，右键应打开游戏介绍 GUI 而非放置方块
    DataComponentType<Boolean> SPONSOR_INTRO = register("sponsor_intro", stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    // 自定义玩家 plush 绑定的玩家名（用于按名字解析皮肤并渲染），随物品携带、放置时写入方块实体
    DataComponentType<String> PLUSH_PLAYER = register("plush_player", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private static <T> DataComponentType<T> register(String name, @NotNull UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SRE.id(name), builderOperator.apply(DataComponentType.builder()).build());
    }
}
