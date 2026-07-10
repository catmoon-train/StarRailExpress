package io.wifi.starrailexpress.mixin.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.wifi.starrailexpress.SRE;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link ByteBufferBuilder#reserve(int)} 的偏移量算术是 32 位的：
 *
 * <pre>{@code
 * int i = this.writeOffset;
 * int j = i + size;        // 越过 2 GiB 就溢出成负数
 * this.ensureCapacity(j);  // j 为负 => 判定"够用"，不扩容
 * this.writeOffset = j;
 * return this.pointer + i; // 下一次 reserve 就返回 base - 2 GiB
 * }</pre>
 *
 * <p>于是一旦某个 batch 往同一个 ByteBufferBuilder 里灌满 2 GiB 顶点，之后的顶点就写到分配块之外，
 * 进程被 EXCEPTION_ACCESS_VIOLATION 当场杀掉。hs_err 里只剩
 * {@code StubRoutines::jlong_disjoint_arraycopy}，一行 Java 栈都没有，等于没有线索。
 *
 * <p>这里在溢出前抛异常，把一次不可诊断的 JVM 崩溃换成一份带完整 Java 调用栈的崩溃报告——
 * 栈里就是那个无限往缓冲里灌顶点的渲染器。到 1 GiB 时先打一条带栈告警，通常不必等到真崩。
 *
 * <p>reserve() 每个顶点调用一次，是渲染最热的路径之一：用 @ModifyVariable 而非 @Inject，
 * 避免每顶点构造一个 CallbackInfo。
 */
@Mixin(ByteBufferBuilder.class)
public abstract class ByteBufferBuilderOverflowMixin {

    @Unique
    private static final long SRE$WARN_AT = Integer.MAX_VALUE / 2L;

    @Shadow
    private int writeOffset;

    @Unique
    private boolean sre$warned;

    @ModifyVariable(method = "reserve", at = @At("HEAD"), argsOnly = true)
    private int sre$failBeforeOffsetOverflow(int size) {
        long next = (long) this.writeOffset + size;
        if (next > Integer.MAX_VALUE) {
            throw new IllegalStateException("ByteBufferBuilder overflowed 2 GiB (writeOffset=" + this.writeOffset
                    + ", reserve=" + size + "). A renderer is filling one batch without bound.");
        }
        if (next > SRE$WARN_AT && !this.sre$warned) {
            this.sre$warned = true;
            SRE.LOGGER.warn("[SRE] ByteBufferBuilder passed 1 GiB in a single batch (writeOffset={}); "
                    + "this batch is on course to kill the JVM.", this.writeOffset, new Throwable("emitter"));
        }
        return size;
    }

    @Inject(method = { "clear", "discard" }, at = @At("TAIL"))
    private void sre$rearmWarning(CallbackInfo ci) {
        this.sre$warned = false;
    }
}
