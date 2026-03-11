package org.agmas.noellesroles.item;

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
import java.util.Objects;

import org.agmas.noellesroles.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

public class WheelchairItem extends Item {
   public WheelchairItem() {
      super(new Item.Properties().stacksTo(1).durability(90));
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
      if (!itemStack.is(ModItems.WHEELCHAIR))
         return InteractionResultHolder.pass(itemStack);
      if (level.isClientSide)
         return InteractionResultHolder.success(itemStack);
      EntityType<WheelchairEntity> entityType = ModEntities.WHEELCHAIR;
      BlockPos bc = player.getOnPos();
      player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
      WheelchairEntity entity = entityType.spawn((ServerLevel) level, itemStack, player, bc.above(),
            MobSpawnType.SPAWN_EGG, false, false);
      if (entity == null) {
         return InteractionResultHolder.pass(itemStack);
      } else {
         entity.durability = itemStack.getMaxDamage() - itemStack.getDamageValue();

         itemStack.consume(1, player);
         player.awardStat(Stats.ITEM_USED.get(this));
         level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
         player.stopRiding();
         player.startRiding(entity);
         return InteractionResultHolder.consume(itemStack);
      }
   }

   public boolean spawnsEntity(ItemStack itemStack, EntityType<?> entityType) {
      return Objects.equals(ModEntities.WHEELCHAIR, entityType);
   }
}