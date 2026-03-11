package io.wifi.starrailexpress.mixin.block;

import io.wifi.starrailexpress.util.BlockSettingsAdditions;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.Properties.class)
public class AbstractBlockSettingsMixin implements BlockSettingsAdditions {
    @Shadow
    boolean hasCollision;

    @Unique
    private boolean tmm$collidable = true; // 默认值与原版一致

    @Override
    public BlockBehaviour.Properties tmm$setCollidable(boolean collidable) {
        this.hasCollision = collidable;
        return (BlockBehaviour.Properties) (Object) this;
    }
}