package org.agmas.noellesroles.content.entity;

import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SREMinecart extends Minecart {
    private int outRailTime = 0;
    private boolean onRails;

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    public SREMinecart(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    protected Item getDropItem() {
        return ModItems.SRE_MINECART;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return ModItems.SRE_MINECART.getDefaultInstance();
    }

    public SREMinecart(Level level, double d, double e, double f) {
        this(ModEntities.MINECART, level);
        this.setPos(d, e, f);
        this.xo = d;
        this.yo = e;
        this.zo = f;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {

            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY());
            int k = Mth.floor(this.getZ());
            if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
                --j;
            }
            BlockPos blockPos = new BlockPos(i, j, k);
            BlockState blockState = this.level().getBlockState(blockPos);
            onRails = BaseRailBlock.isRail(blockState);
            if (onRails) {
                outRailTime = 0;
            } else {
                if (this.getDeltaMovement().lengthSqr() < 0.01) {
                    outRailTime++;
                } else {
                    outRailTime = 0;
                }
            }
        }
        if (outRailTime > 5 * 20) {
            this.kill();
            return;
        }
        tickMinecart();
    }

    protected void tickMinecart() {
        super.tick();
    }

    @Override
    public void destroy(Item item) {
        this.kill();
    }
}
