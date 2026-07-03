package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.content.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

import java.util.Objects;

public class WheelchairItem extends Item {
   public WheelchairItem() {
      super(new Item.Properties().stacksTo(1).durability(90));
   }

   public static boolean canPlaceWheelchairAtPlayer(Player player) {
      Level level = player.level();
      // 轮椅碰撞箱尺寸（与玩家站立尺寸一致）
      double width = 1;
      double height = 1.6;
      double depth = 1;

      // 计算轮椅底部中心点：玩家脚部位置（player.getY() 即为脚部Y）
      double centerX = player.getX();
      double footY = player.getY();
      double centerZ = player.getZ();

      // 构建轮椅碰撞箱
      AABB wheelchairBox = new AABB(
            centerX - width / 2.0,
            footY,
            centerZ - depth / 2.0,
            centerX + width / 2.0,
            footY + height,
            centerZ + depth / 2.0);

      // 检测是否与任何方块碰撞（忽略玩家自身）
      return level.noCollision(null, wheelchairBox);
   }

   public InteractionResult useOn(UseOnContext useOnContext) {
      Level level = useOnContext.getLevel();
      if (useOnContext.getPlayer().getCooldowns().isOnCooldown(ModItems.WHEELCHAIR)) {
         return InteractionResult.PASS;
      }
      if (!(level instanceof ServerLevel)) {
         return InteractionResult.SUCCESS;
      } else {
         ItemStack itemStack = useOnContext.getItemInHand();

         BlockPos blockPos = useOnContext.getClickedPos();
         Direction direction = useOnContext.getClickedFace();
         BlockState blockState = level.getBlockState(blockPos);

         BlockPos blockPos2;
         if (blockState.getCollisionShape(level, blockPos).isEmpty()) {
            blockPos2 = blockPos;
         } else {
            blockPos2 = blockPos.relative(direction);
         }
         var we = ModEntities.WHEELCHAIR.spawn((ServerLevel) level, itemStack, useOnContext.getPlayer(), blockPos2,
               MobSpawnType.SPAWN_EGG, true,
               !Objects.equals(blockPos, blockPos2) && direction == Direction.UP);
         useOnContext.getPlayer().getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
         if (we != null) {
            we.setYRot(useOnContext.getPlayer().getYRot());

            we.durability = itemStack.getMaxDamage() - itemStack.getDamageValue();
            itemStack.consume(1, useOnContext.getPlayer());
            level.gameEvent(useOnContext.getPlayer(), GameEvent.ENTITY_PLACE, blockPos);
         }

         return InteractionResult.CONSUME;

      }
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {

      ItemStack itemStack = player.getItemInHand(interactionHand);
      if (player.getCooldowns().isOnCooldown(ModItems.WHEELCHAIR)) {
         return InteractionResultHolder.pass(itemStack);
      }
      if (player.isShiftKeyDown()) { // 不准你潜行用
         return InteractionResultHolder.pass(itemStack);
      }
      if (player.getCooldowns().isOnCooldown(TMMBlocks.ACACIA_BRANCH.asItem())) {
         return InteractionResultHolder.pass(itemStack);
      }
      if (!canPlaceWheelchairAtPlayer(player)) {
         return InteractionResultHolder.fail(itemStack);
      }
      if (!itemStack.is(ModItems.WHEELCHAIR))
         return InteractionResultHolder.pass(itemStack);
      if (level.isClientSide)
         return InteractionResultHolder.success(itemStack);
      EntityType<WheelchairEntity> entityType = ModEntities.WHEELCHAIR;
      player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
      WheelchairEntity entity = entityType.create((ServerLevel) level);

      if (entity == null) {
         return InteractionResultHolder.pass(itemStack);
      } else {
         entity.setPos(player.getX(), player.getY(), player.getZ());
         entity.setYRot(player.getYRot());
         entity.durability = itemStack.getMaxDamage() - itemStack.getDamageValue();
         level.addFreshEntity(entity);

         itemStack.consume(1, player);
         player.awardStat(Stats.ITEM_USED.get(this));
         level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
         player.stopRiding();
         player.startRiding(entity);
         player.getCooldowns().addCooldown(TMMBlocks.ACACIA_BRANCH.asItem(), 10);

         return InteractionResultHolder.consume(itemStack);
      }
   }

   public static WheelchairEntity createEntity(EntityType<WheelchairEntity> entityType, ServerLevel level) {
      WheelchairEntity entity = entityType.create(level);
      if (entity == null) {
         return null;
      }
      return entity;
   }
}