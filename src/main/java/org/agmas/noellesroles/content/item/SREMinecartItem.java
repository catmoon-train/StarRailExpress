package org.agmas.noellesroles.content.item;

import org.agmas.noellesroles.content.entity.SREMinecart;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.AbstractMinecart.Type;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;

public class SREMinecartItem extends MinecartItem{

    public SREMinecartItem(Type type, Properties properties) {
        super(type, properties);
    }
    
   public InteractionResult useOn(UseOnContext useOnContext) {
      Level level = useOnContext.getLevel();
      BlockPos blockPos = useOnContext.getClickedPos();
      BlockState blockState = level.getBlockState(blockPos);
      if (!blockState.is(BlockTags.RAILS)) {
         return InteractionResult.FAIL;
      } else {
         ItemStack itemStack = useOnContext.getItemInHand();
         if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            RailShape railShape = blockState.getBlock() instanceof BaseRailBlock ? (RailShape)blockState.getValue(((BaseRailBlock)blockState.getBlock()).getShapeProperty()) : RailShape.NORTH_SOUTH;
            double d = (double)0.0F;
            if (railShape.isAscending()) {
               d = (double)0.5F;
            }

            AbstractMinecart abstractMinecart = createMinecart(serverLevel, (double)blockPos.getX() + (double)0.5F, (double)blockPos.getY() + (double)0.0625F + d, (double)blockPos.getZ() + (double)0.5F, itemStack, useOnContext.getPlayer());
            serverLevel.addFreshEntity(abstractMinecart);
            serverLevel.gameEvent(GameEvent.ENTITY_PLACE, blockPos, GameEvent.Context.of(useOnContext.getPlayer(), serverLevel.getBlockState(blockPos.below())));
         }

         itemStack.shrink(1);
         return InteractionResult.sidedSuccess(level.isClientSide);
      }
   }

   
   public static AbstractMinecart createMinecart(ServerLevel serverLevel, double d, double e, double f, ItemStack itemStack, Player player) {
      AbstractMinecart abstractMinecart = new SREMinecart(serverLevel, d, e, f);
      EntityType.createDefaultStackConfig(serverLevel, itemStack, player).accept(abstractMinecart);
      return abstractMinecart;
   }
}
