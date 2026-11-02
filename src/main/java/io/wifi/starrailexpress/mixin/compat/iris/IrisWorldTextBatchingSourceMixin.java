package io.wifi.starrailexpress.mixin.compat.iris;

import io.wifi.starrailexpress.compat.WorldTextBatchingSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** 复用 Iris 的世界文字合批及着色器状态管理；不接管其排序或提交时机。 */
@Pseudo
@Mixin(targets = {
        "net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource",
        "net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource$UnflushableWrapper"
}, remap = false)
public abstract class IrisWorldTextBatchingSourceMixin implements WorldTextBatchingSource {
}
