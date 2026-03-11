package io.wifi.starrailexpress.index;

import com.mojang.serialization.Codec;

import io.wifi.starrailexpress.SRE;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public interface SREDataComponentTypes {
    DataComponentType<String> POISONER = register("poisoner", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<String> ARMORER = register("armorer", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<Boolean> USED = register("used", stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<String> OWNER = register("owner", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<String> SKIN = register("skin", stringBuilder -> stringBuilder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<Boolean> SCOPE_ATTACHED = register("scope_attached", stringBuilder -> stringBuilder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<Integer> AMMO_COUNT = register("ammo_count", stringBuilder -> stringBuilder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private static <T> DataComponentType<T> register(String name, @NotNull UnaryOperator<DataComponentType.Builder<T>> builderOperator) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SRE.id(name), builderOperator.apply(DataComponentType.builder()).build());
    }
}
