package io.wifi.starrailexpress.mixin.client;

import com.google.common.collect.ImmutableSortedMap;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wifi.srepackcompat.startup.StateCombinationMap;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.Map;

/** 仅加速原版邻接表的构建期查找；不接管状态创建、ID、运行时表或 setValue。 */
@Mixin(value = StateDefinition.class, priority = 900)
public abstract class StateDefinitionLookupMixin<O, S extends StateHolder<O, S>> {
    @Shadow @Final private ImmutableSortedMap<String, Property<?>> propertiesByName;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/StateHolder;populateNeighbours(Ljava/util/Map;)V"), index = 0)
    private Map<Map<Property<?>, Comparable<?>>, S> srepack$indexNeighbourLookup(
            Map<Map<Property<?>, Comparable<?>>, S> original,
            @Share("srepack$neighbourLookup") LocalRef<Map<Map<Property<?>, Comparable<?>>, S>> shared) {
        var indexed = shared.get();
        if (indexed == null) {
            indexed = StateCombinationMap.create(original, new ArrayList<>(propertiesByName.values()),
                    Property::getPossibleValues, StateHolder::getValues);
            shared.set(indexed);
        }
        return indexed;
    }
}
