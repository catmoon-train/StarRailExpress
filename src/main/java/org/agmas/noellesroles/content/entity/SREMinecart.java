package org.agmas.noellesroles.content.entity;

import org.agmas.noellesroles.init.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SREMinecart extends Minecart {
    private int outRailTime = 0;
    private boolean flipped;
    private boolean onRails;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    public SREMinecart(EntityType<?> entityType, Level level) {
        super(entityType, level);
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
        boolean onRails = false;
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
                }
                outRailTime = 0;
            }
        }
        if (outRailTime > 5 * 20) {
            this.kill();
            return;
        }
        tickMinecart();
    }

    protected void tickMinecart() {

        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        this.handlePortal();
        if (this.level().isClientSide) {
            if (this.lerpSteps > 0) {
                this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot,
                        this.lerpXRot);
                --this.lerpSteps;
            } else {
                this.reapplyPosition();
                this.setRot(this.getYRot(), this.getXRot());
            }

        } else {
            this.applyGravity();
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY());
            int k = Mth.floor(this.getZ());
            if (this.level().getBlockState(new BlockPos(i, j - 1, k)).is(BlockTags.RAILS)) {
                --j;
            }

            BlockPos blockPos = new BlockPos(i, j, k);
            BlockState blockState = this.level().getBlockState(blockPos);
            this.onRails = BaseRailBlock.isRail(blockState);
            if (this.onRails) {
                this.moveAlongTrack(blockPos, blockState);
                if (blockState.is(Blocks.ACTIVATOR_RAIL)) {
                    this.activateMinecart(i, j, k, (Boolean) blockState.getValue(PoweredRailBlock.POWERED));
                }
            } else {
                this.comeOffTrack();
            }

            this.checkInsideBlocks();
            this.setXRot(0.0F);
            double d = this.xo - this.getX();
            double e = this.zo - this.getZ();
            if (d * d + e * e > 0.001) {
                this.setYRot((float) (Mth.atan2(e, d) * (double) 180.0F / Math.PI));
                if (this.flipped) {
                    this.setYRot(this.getYRot() + 180.0F);
                }
            }

            double f = (double) Mth.wrapDegrees(this.getYRot() - this.yRotO);
            if (f < (double) -170.0F || f >= (double) 170.0F) {
                this.setYRot(this.getYRot() + 180.0F);
                this.flipped = !this.flipped;
            }

            this.setRot(this.getYRot(), this.getXRot());

            this.updateInWaterStateAndDoFluidPushing();
            if (this.isInLava()) {
                this.lavaHurt();
                this.fallDistance *= 0.5F;
            }

            this.firstTick = false;
        }
    }

    @Override
    public void destroy(Item item) {
        this.kill();
    }
}
