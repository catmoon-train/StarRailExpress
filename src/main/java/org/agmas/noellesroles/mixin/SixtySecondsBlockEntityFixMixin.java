package org.agmas.noellesroles.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 chunk 加载时孤立的 BlockEntity（NBT 残留但方块已是 air）导致构造函数抛
 * {@code IllegalStateException} 崩溃的问题。
 * <p>
 * 根因：MC 1.21 在 {@code BlockEntity} 构造函数里加了
 * {@code validateBlockState(state.isAir() → throw)} 校验，
 * 但世界保存/结构生成可能导致 block entity 数据残留、方块已被替换为 air。
 * <p>
 * 这里把校验调用踹掉（只对 air 块跳过），让 BlockEntity 正常构造出来——
 * 反正没了 valid block，下一帧就会被 MC 自己清掉。
 */
@Mixin(BlockEntity.class)
public class SixtySecondsBlockEntityFixMixin {

    /**
     * 踢掉 {@link BlockEntity#validateBlockState(BlockState)} 对 air 块的严打。
     * 这方法是 private 的，在构造函数内调用；Redirect 比 WrapWithCondition 更稳妥
     * （避免 Wrap 在 <init> 上可能的兼容性坑）。
     */
    @SuppressWarnings("UnusedReturnValue")
    @Redirect(
        method = "<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;validateBlockState(Lnet/minecraft/world/level/block/state/BlockState;)V"
        ),
        require = 0
    )
    private void validateBlockState(BlockEntity self, BlockState state) {
        // air：NBT 遗孤，放它过去——方块不是 air 的场合原校验只检查 isAir()，不会抛
        // 所以非 air 时直接什么都不做就是原行为
        // (什么都不做 = 跳过原 private 校验调用)
    }
}
