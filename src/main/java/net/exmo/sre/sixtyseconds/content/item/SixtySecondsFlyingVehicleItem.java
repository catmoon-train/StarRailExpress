package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsFlyingVehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.function.Supplier;

/**
 * 飞行载具物品（飞行器/直升机/飞机）：对地面右键放置对应载具实体。
 */
public class SixtySecondsFlyingVehicleItem extends Item {

    private final Supplier<EntityType<? extends Mob>> typeSupplier;

    public SixtySecondsFlyingVehicleItem(Properties properties,
            Supplier<EntityType<? extends Mob>> typeSupplier) {
        super(properties);
        this.typeSupplier = typeSupplier;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos spawnPos = state.getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(face);
        Mob entity = typeSupplier.get().create(serverLevel);
        if (entity == null) {
            return InteractionResult.PASS;
        }
        entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 0.1, spawnPos.getZ() + 0.5);
        if (ctx.getPlayer() != null) {
            entity.setYRot(ctx.getPlayer().getYRot());
        }
        serverLevel.addFreshEntity(entity);
        stack.consume(1, ctx.getPlayer());
        level.gameEvent(ctx.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
        return InteractionResult.CONSUME;
    }
}
