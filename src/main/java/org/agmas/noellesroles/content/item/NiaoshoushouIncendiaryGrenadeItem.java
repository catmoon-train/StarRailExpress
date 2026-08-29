package org.agmas.noellesroles.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.IncendiaryGrenadeEntity;
import org.agmas.noellesroles.content.entity.NiaoshoushouIncendiaryGrenadeEntity;
import org.agmas.noellesroles.init.ModEntities;

/** 鸟兽兽专属燃烧弹：复用现有燃烧弹，增加 45 秒使用冷却。 */
public class NiaoshoushouIncendiaryGrenadeItem extends IncendiaryGrenadeItem {
    public NiaoshoushouIncendiaryGrenadeItem(Properties properties) {
        super(properties);
    }

    @Override
    protected IncendiaryGrenadeEntity createGrenadeEntity(Level level) {
        return new NiaoshoushouIncendiaryGrenadeEntity(ModEntities.NIAOSHOU_SHOU_INCENDIARY_GRENADE,
                level);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
        super.releaseUsing(stack, level, user, remainingUseTicks);
        if (!level.isClientSide && user instanceof Player player && !player.isSpectator()
                && !player.isCreative()) {
            player.getCooldowns().addCooldown(this, 45 * 20);
        }
    }
}
