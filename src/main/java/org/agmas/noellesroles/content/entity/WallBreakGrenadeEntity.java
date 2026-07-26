package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.game.wallbreak.WallBreakManager;
import org.agmas.noellesroles.init.ModItems;

/**
 * 破墙弹实体：右键投掷，命中墙壁后拆除以命中点为球心、半径 2 的方块，
 * 5 秒后自动恢复（持久化，重启不丢失）。带 NBT 的方块不拆除。见 {@link WallBreakManager}。
 */
public class WallBreakGrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 拆墙半径。 */
    public static final int BREAK_RADIUS = 2;

    public WallBreakGrenadeEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.WALL_BREAK_GRENADE;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            // 命中方块时以命中方块为球心，否则以自身所在方块为球心
            BlockPos center = hitResult instanceof BlockHitResult blockHit
                    ? blockHit.getBlockPos()
                    : this.blockPosition();
            ServerPlayer thrower = this.getOwner() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            WallBreakManager.breakWalls(world, center, BREAK_RADIUS, thrower);
            this.discard();
        }
    }
}
