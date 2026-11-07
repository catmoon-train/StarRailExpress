package io.wifi.starrailexpress.mixin.compat.dashloader;

import io.wifi.starrailexpress.client.resource.BlockStateJsonFallback;
import io.wifi.starrailexpress.client.resource.DashLoaderCompat;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 替代 DashLoader 按每个未缓存状态重复加载整块定义的路径，避免 O(状态数²) 与日志风暴。 */
@Mixin(BlockStateModelLoader.class)
public abstract class DashBlockStateFallbackMixin {
    @Unique private static final Logger sre$stateLogger = LoggerFactory.getLogger("SRE/DashLoader");
    @Mutable @Shadow @Final
    private Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources;

    @Shadow
    protected abstract void loadBlockStateDefinitions(ResourceLocation id, StateDefinition<Block, BlockState> definition);

    @Inject(method = "loadAllBlockStates", at = @At("HEAD"), cancellable = true)
    private void sre$loadUniqueFallbackBlocks(CallbackInfo ci) {
        var resources = DashLoaderCompat.fallbackResources();
        if (resources == null) {
            return;
        }
        var missingBlocks = DashLoaderCompat.missingBlockIds();
        // DashLoader 热缓存的 map 通常为空或不可变，只复制一次，不改共享 map。
        blockStateResources = new HashMap<>(blockStateResources);
        for (ResourceLocation id : missingBlocks) {
            ResourceLocation file = id.withPath(path -> "blockstates/" + path + ".json");
            if (!blockStateResources.containsKey(file)) {
                try {
                    blockStateResources.put(file, BlockStateJsonFallback.load(resources, file));
                } catch (IOException | RuntimeException error) {
                    // 真正坏的 JSON 仍报告一次，交原版缺失模型兜底，绝不伪造成功。
                    sre$stateLogger.warn("Failed to load fallback blockstate {}", file, error);
                    blockStateResources.put(file, List.of());
                }
            }
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                loadBlockStateDefinitions(id, BuiltInRegistries.BLOCK.get(id).getStateDefinition());
            } else {
                sre$stateLogger.warn("Unknown block in DashLoader fallback: {}", id);
            }
        }
        sre$stateLogger.info("Loaded fallback blockstate definitions for {} distinct blocks", missingBlocks.size());
        // 不进入 DashLoader 的 Iterator.hasNext 包装：它会为每个状态再次加载整个方块。
        ci.cancel();
    }
}
