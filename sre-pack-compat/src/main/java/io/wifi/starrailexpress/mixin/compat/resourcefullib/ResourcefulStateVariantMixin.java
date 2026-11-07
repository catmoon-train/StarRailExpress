package io.wifi.starrailexpress.mixin.compat.resourcefullib;

import com.mojang.serialization.DataResult;
import io.wifi.srepackcompat.startup.ResourcefulStateLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

/** 保留 ResourcefulLib 原始解析校验，仅加速高状态数方块的重复 variant 查询。 */
@Mixin(targets = "com.teamresourceful.resourcefullib.client.highlights.state.StateVariant", remap = false)
public abstract class ResourcefulStateVariantMixin {
    @Shadow
    private static Predicate<BlockState> predicate(StateDefinition<Block, BlockState> definition, String variant) {
        throw new AssertionError();
    }

    @Inject(method = "getStates", at = @At("HEAD"), cancellable = true)
    private static void srepack$indexedStates(StateDefinition<Block, BlockState> definition, String variant,
            CallbackInfoReturnable<DataResult<List<BlockState>>> cir) {
        if (!ResourcefulStateLookup.eligible(definition)) {
            return;
        }
        var result = ResourcefulStateLookup.select(definition, variant, predicate(definition, variant));
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
