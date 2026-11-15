package io.wifi.starrailexpress.mixin.compat.cobblemon;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import io.wifi.starrailexpress.compat.cobblemon.CobblemonWorldgen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 不修改世界注册表或保存内容，只在新生成与战利品展开时过滤宝可梦贡献。 */
public final class WorldMechanicsMixins {
    @Mixin(NestedLootTable.class)
    public static class Loot {
        @Shadow @Final private Either<ResourceKey<LootTable>, LootTable> contents;
        @Inject(method = "createItemStack", at = @At("HEAD"), cancellable = true)
        private void sre$injectedLoot(CallbackInfo ci) {
            if (!CobblemonMechanics.enabled() && contents.left()
                    .map(key -> CobblemonMechanics.injectedLoot(key.location())).orElse(false)) ci.cancel();
        }
    }

    @Mixin(PlacedFeature.class)
    public static class Features {
        @Inject(method = {"place", "placeWithBiomeCheck"}, at = @At("HEAD"), cancellable = true)
        private void sre$features(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos pos,
                                  CallbackInfoReturnable<Boolean> cir) {
            if (CobblemonMechanics.enabled()) return;
            PlacedFeature feature = (PlacedFeature) (Object) this;
            ResourceLocation id = world.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(feature);
            if (CobblemonMechanics.isCobblemon(id) || feature.feature().unwrapKey()
                    .map(key -> CobblemonMechanics.isCobblemon(key.location())).orElse(false)) cir.setReturnValue(false);
        }
    }

    @Mixin(Structure.class)
    public static class Structures {
        @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
        private void sre$structures(RegistryAccess registries, ChunkGenerator generator, BiomeSource biomeSource,
                                   RandomState randomState, StructureTemplateManager manager, long seed, ChunkPos chunk,
                                   int references, LevelHeightAccessor height, Predicate<Holder<Biome>> validBiome,
                                   CallbackInfoReturnable<StructureStart> cir) {
            if (!CobblemonMechanics.enabled() && CobblemonMechanics.isCobblemon(
                    registries.registryOrThrow(Registries.STRUCTURE).getKey((Structure) (Object) this))) {
                cir.setReturnValue(StructureStart.INVALID_START);
            }
        }
    }

    @Mixin(SinglePoolElement.class)
    public interface SingleAccess {
        @Accessor("template") Either<ResourceLocation, StructureTemplate> sre$template();
    }
    @Mixin(ListPoolElement.class)
    public interface ListAccess {
        @Accessor("elements") List<StructurePoolElement> sre$elements();
    }
    @Mixin(FeaturePoolElement.class)
    public interface FeatureAccess {
        @Accessor("feature") Holder<PlacedFeature> sre$feature();
    }

    @Mixin(StructureTemplatePool.class)
    public static class Pools {
        @Shadow @Final private ObjectArrayList<StructurePoolElement> templates;

        @Inject(method = "getRandomTemplate", at = @At("HEAD"), cancellable = true)
        private void sre$random(RandomSource random, CallbackInfoReturnable<StructurePoolElement> cir) {
            if (CobblemonMechanics.enabled()) return;
            var filtered = sre$vanillaTemplates();
            if (filtered != null) cir.setReturnValue(filtered.isEmpty() ? EmptyPoolElement.INSTANCE : filtered.get(random.nextInt(filtered.size())));
        }

        @Inject(method = "getShuffledTemplates", at = @At("HEAD"), cancellable = true)
        private void sre$shuffled(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> cir) {
            if (CobblemonMechanics.enabled()) return;
            var filtered = sre$vanillaTemplates();
            if (filtered == null) return;
            for (int i = filtered.size(); i > 1; --i) {
                int j = random.nextInt(i);
                var old = filtered.get(i - 1); filtered.set(i - 1, filtered.get(j)); filtered.set(j, old);
            }
            cir.setReturnValue(filtered);
        }

        @Inject(method = "getMaxSize", at = @At("HEAD"), cancellable = true)
        private void sre$size(StructureTemplateManager manager, CallbackInfoReturnable<Integer> cir) {
            if (CobblemonMechanics.enabled()) return;
            var filtered = sre$vanillaTemplates();
            if (filtered != null) cir.setReturnValue(filtered.stream().filter(element -> element != EmptyPoolElement.INSTANCE)
                    .mapToInt(element -> element.getBoundingBox(manager, BlockPos.ZERO, Rotation.NONE).getYSpan()).max().orElse(0));
        }

        @org.spongepowered.asm.mixin.Unique
        private List<StructurePoolElement> sre$vanillaTemplates() {
            var filtered = new ArrayList<StructurePoolElement>();
            boolean changed = false;
            for (StructurePoolElement element : templates) {
                var vanilla = CobblemonWorldgen.vanillaElement(element);
                changed |= vanilla != element;
                if (vanilla != EmptyPoolElement.INSTANCE || element == EmptyPoolElement.INSTANCE) filtered.add(vanilla);
            }
            return changed ? filtered : null;
        }
    }

    @Mixin(StructureTemplate.class)
    public static class Processors {
        @WrapOperation(method = "processBlockInfos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
        private static StructureTemplate.StructureBlockInfo sre$processor(StructureProcessor processor, LevelReader world,
                BlockPos pos, BlockPos pivot, StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo current,
                StructurePlaceSettings settings, Operation<StructureTemplate.StructureBlockInfo> call) {
            return !CobblemonMechanics.enabled() && processor.getClass().getName().startsWith("com.cobblemon.mod.")
                    ? current : call.call(processor, world, pos, pivot, original, current, settings);
        }

        @WrapOperation(method = "processBlockInfos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;finalizeProcessing(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Ljava/util/List;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Ljava/util/List;"))
        private static List<StructureTemplate.StructureBlockInfo> sre$finalize(StructureProcessor processor, ServerLevelAccessor world,
                BlockPos pos, BlockPos pivot, List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> current,
                StructurePlaceSettings settings, Operation<List<StructureTemplate.StructureBlockInfo>> call) {
            return !CobblemonMechanics.enabled() && processor.getClass().getName().startsWith("com.cobblemon.mod.")
                    ? current : call.call(processor, world, pos, pivot, original, current, settings);
        }
    }
}
