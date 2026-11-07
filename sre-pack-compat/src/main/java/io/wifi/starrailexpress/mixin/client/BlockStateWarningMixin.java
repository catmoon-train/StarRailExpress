package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.resource.ResourceWarningBudget;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** 只合并逐 variant 的同类缺失警告；每资源保留三个样例和省略数量，不屏蔽其他错误。 */
@Mixin(BlockStateModelLoader.class)
public abstract class BlockStateWarningMixin {
    @Unique private static final Logger sre$warningLogger = LoggerFactory.getLogger("SRE/BlockStates");
    @Unique private final ResourceWarningBudget<Object> sre$warningBudget = new ResourceWarningBudget<>(3);

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private void sre$limitMissingVariantSamples(Logger logger, String format, Object resource, Object variant,
                                                Operation<Void> original) {
        if (!"Exception loading blockstate definition: '{}' missing model for variant: '{}'".equals(format)
                || sre$warningBudget.allow(resource)) {
            original.call(logger, format, resource, variant);
        }
    }

    @WrapMethod(method = "loadAllBlockStates")
    private void sre$summarizeMissingVariants(Operation<Void> original) {
        try {
            original.call();
        } finally {
            sre$warningBudget.drain((resource, omitted) -> sre$warningLogger.warn(
                    "Blockstate {}: omitted {} additional missing-variant warnings (first 3 shown)", resource, omitted));
        }
    }
}
