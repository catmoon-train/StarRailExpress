package io.wifi.starrailexpress.mixin.client;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.srepackcompat.startup.NeighbourTableFactory;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 只替换邻接表的分配方式，保留原版 ArrayTable 类型、键顺序和全部状态引用。 */
@Mixin(StateHolder.class)
public abstract class StateHolderTableMixin {
    @WrapOperation(method = "populateNeighbours", at = @At(value = "INVOKE", target =
            "Lcom/google/common/collect/ArrayTable;create(Lcom/google/common/collect/Table;)Lcom/google/common/collect/ArrayTable;",
            remap = false))
    private <R, C, V> ArrayTable<R, C, V> srepack$reuseNeighbourLayout(Table<R, C, V> source,
            Operation<ArrayTable<R, C, V>> original) {
        return NeighbourTableFactory.create(source);
    }
}
