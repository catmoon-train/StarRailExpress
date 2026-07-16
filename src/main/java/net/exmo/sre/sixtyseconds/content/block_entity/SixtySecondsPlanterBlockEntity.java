package net.exmo.sre.sixtyseconds.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 培育箱方块实体：只存当前种植的作物 id（{@link net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops}）。
 * 生长进度仍走方块状态 AGE（模型按阶段切换），此处仅补充"种的是什么"。
 */
public class SixtySecondsPlanterBlockEntity extends BlockEntity {

    /** 当前作物 id；空串 = 未播种。 */
    public String cropId = "";

    public SixtySecondsPlanterBlockEntity(BlockPos pos, BlockState state) {
        super(org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_PLANTER_ENTITY, pos, state);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cropId = tag.getString("Crop");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Crop", cropId);
    }
}
