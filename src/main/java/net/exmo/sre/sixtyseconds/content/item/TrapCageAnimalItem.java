package net.exmo.sre.sixtyseconds.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 捕获动物物品：右键在玩家位置生成对应的生物/动物。
 */
public class TrapCageAnimalItem extends Item {

    private final EntityType<?> entityType;

    public TrapCageAnimalItem(EntityType<?> entityType, Properties properties) {
        super(properties);
        this.entityType = entityType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            BlockPos pos = player.blockPosition();
            entityType.spawn(serverLevel, pos, MobSpawnType.SPAWN_EGG);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
