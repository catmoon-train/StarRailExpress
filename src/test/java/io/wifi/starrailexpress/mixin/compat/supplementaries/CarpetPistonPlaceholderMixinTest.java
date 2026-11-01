package io.wifi.starrailexpress.mixin.compat.supplementaries;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CarpetPistonPlaceholderMixinTest {
    private void invoke(BlockEntity entity, Operation<Void> original, boolean extending, CallbackInfo ci) throws Throwable {
        var method = CarpetPistonPlaceholderMixin.class.getDeclaredMethod("sre$forwardCarpetPlaceholder",
                Level.class, BlockEntity.class, Operation.class, Direction.class, boolean.class, CallbackInfo.class);
        method.setAccessible(true);
        try {
            method.invoke(new CarpetPistonPlaceholderMixin(), null, entity, original, Direction.EAST, extending, ci);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    @Test void forwardsPlaceholderOnceBeforeCancellingSupplementariesAssertion() throws Throwable {
        var calls = new AtomicInteger();
        var ci = new CallbackInfo("piston", true);
        invoke(null, args -> {
            calls.incrementAndGet();
            assertEquals(2, args.length);
            assertNull(args[0]);
            assertNull(args[1]);
            assertFalse(ci.isCancelled(), "必须先执行原操作，再结束 Supplementaries 的空值分支");
            return null;
        }, true, ci);
        assertEquals(1, calls.get());
        assertTrue(ci.isCancelled());
    }

    @Test void samePlaceholderHandlingForPulling() throws Throwable {
        var calls = new AtomicInteger();
        var ci = new CallbackInfo("piston", true);
        invoke(null, args -> { calls.incrementAndGet(); return null; }, false, ci);
        assertEquals(1, calls.get());
        assertTrue(ci.isCancelled());
    }

    @Test void leavesRealBlockEntityForOriginalSupplementariesCapture() throws Throwable {
        // 只测试实体身份分支，无世界与注册表引导；不伪造实际活塞移动验收。
        BlockEntity entity = new BlockEntity(null, BlockPos.ZERO, null) {
            @Override
            public boolean isValidBlockState(net.minecraft.world.level.block.state.BlockState state) {
                return true;
            }
        };
        var ci = new CallbackInfo("piston", true);
        invoke(entity, args -> { fail("非空实体必须继续正常 Supplementaries 处理，而不是提前转发"); return null; }, true, ci);
        assertFalse(ci.isCancelled());
    }

    @Test void neverSwallowsOtherOperationFailures() {
        var failure = new IllegalStateException("real operation failed");
        var ci = new CallbackInfo("piston", true);
        Throwable thrown = assertThrows(IllegalStateException.class,
                () -> invoke(null, args -> { throw failure; }, true, ci));
        assertSame(failure, thrown);
        assertFalse(ci.isCancelled());
    }
}
