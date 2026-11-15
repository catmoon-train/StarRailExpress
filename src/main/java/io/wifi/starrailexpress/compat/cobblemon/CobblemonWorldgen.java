package io.wifi.starrailexpress.compat.cobblemon;

import io.wifi.starrailexpress.mixin.compat.cobblemon.WorldMechanicsMixins.FeatureAccess;
import io.wifi.starrailexpress.mixin.compat.cobblemon.WorldMechanicsMixins.ListAccess;
import io.wifi.starrailexpress.mixin.compat.cobblemon.WorldMechanicsMixins.SingleAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

import java.util.ArrayList;

/** 过滤生成池的展示副本，原池和复合片段中的原版子项不丢失。 */
public final class CobblemonWorldgen {
    public static StructurePoolElement vanillaElement(StructurePoolElement element) {
        if (element instanceof SinglePoolElement) {
            return ((SingleAccess) element).sre$template().left().filter(CobblemonMechanics::isCobblemon).isPresent()
                    ? EmptyPoolElement.INSTANCE : element;
        }
        if (element instanceof FeaturePoolElement) {
            return ((FeatureAccess) element).sre$feature().unwrapKey()
                    .filter(key -> CobblemonMechanics.isCobblemon(key.location())).isPresent() ? EmptyPoolElement.INSTANCE : element;
        }
        if (element instanceof ListPoolElement) {
            var original = ((ListAccess) element).sre$elements();
            var kept = new ArrayList<StructurePoolElement>();
            boolean changed = false;
            for (StructurePoolElement child : original) {
                var filtered = vanillaElement(child);
                changed |= filtered != child;
                if (filtered != EmptyPoolElement.INSTANCE) kept.add(filtered);
            }
            if (!changed) return element;
            return kept.isEmpty() ? EmptyPoolElement.INSTANCE : new ListPoolElement(kept, element.getProjection());
        }
        return CobblemonMechanics.isCobblemon(BuiltInRegistries.STRUCTURE_POOL_ELEMENT.getKey(element.getType()))
                ? EmptyPoolElement.INSTANCE : element;
    }
    private CobblemonWorldgen() { }
}
