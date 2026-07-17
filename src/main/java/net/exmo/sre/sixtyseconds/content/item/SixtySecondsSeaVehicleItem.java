package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.Predicate;

/**
 * 海上载具放置物品（木筏 / 汽艇 / 渔船）。
 * <p>
 * 与陆上的 {@link SixtySecondsVehicleItem} 不同，这里<b>不能</b>用 {@code useOn}——右键水面根本不产生
 * 方块命中（除非射线把流体也算上），所以照 {@code BoatItem} 的做法在 {@link #use} 里自己做一次
 * {@link ClipContext.Fluid#ANY} 射线，只允许放在水面上。
 */
public class SixtySecondsSeaVehicleItem extends Item {

    /** 与原版 BoatItem 一致的取放距离。 */
    private static final double REACH = 5.0D;
    private static final Predicate<net.minecraft.world.entity.Entity> ENTITY_FILTER =
            net.minecraft.world.entity.EntitySelector.NO_SPECTATORS
                    .and(net.minecraft.world.entity.Entity::isPickable);

    private final Supplier<EntityType<SixtySecondsSeaVehicleEntity>> typeSupplier;

    public SixtySecondsSeaVehicleItem(Properties properties,
            Supplier<EntityType<SixtySecondsSeaVehicleEntity>> typeSupplier) {
        super(properties);
        this.typeSupplier = typeSupplier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        // 视线上先撞到实体（比如另一条船）就不放，避免船摞船
        Vec3 view = player.getViewVector(1.0F);
        List<net.minecraft.world.entity.Entity> blockers = level.getEntities(player,
                player.getBoundingBox().expandTowards(view.scale(REACH)).inflate(1.0), ENTITY_FILTER);
        if (!blockers.isEmpty()) {
            Vec3 eye = player.getEyePosition();
            for (net.minecraft.world.entity.Entity blocker : blockers) {
                net.minecraft.world.phys.AABB box = blocker.getBoundingBox().inflate(blocker.getPickRadius());
                if (box.contains(eye)) {
                    return InteractionResultHolder.pass(stack);
                }
            }
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        // 必须放在水上：这是海上载具，摆到旱地上就成了摆设
        BlockPos pos = net.minecraft.core.BlockPos.containing(hit.getLocation());
        if (!level.getFluidState(pos).is(FluidTags.WATER)
                && !level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.noellesroles.sixty_seconds.boat_need_water")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        SixtySecondsSeaVehicleEntity boat = typeSupplier.get().create(serverLevel);
        if (boat == null) {
            return InteractionResultHolder.fail(stack);
        }
        boat.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        boat.setYRot(player.getYRot());
        if (!serverLevel.noCollision(boat, boat.getBoundingBox())) {
            return InteractionResultHolder.fail(stack);
        }
        serverLevel.addFreshEntity(boat);
        serverLevel.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getLocation());
        level.playSound(null, boat.getX(), boat.getY(), boat.getZ(), SoundEvents.BOAT_PADDLE_WATER,
                SoundSource.NEUTRAL, 0.7F, 1.1F);
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }
}
