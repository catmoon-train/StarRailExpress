package org.agmas.noellesroles.content.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.FlareEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

/**
 * 照明弹 - 右键投掷，落地后生成照明弹方块照亮区域，10秒后消失
 */
public class FlareItem extends Item {
    public FlareItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.EGG_THROW, SoundSource.NEUTRAL, 0.5F,
                0.4F / (world.random.nextFloat() * 0.4F + 0.8F));

        if (!world.isClientSide) {
            FlareEntity flare = new FlareEntity(ModEntities.FLARE, world);
            flare.setOwner(user);
            flare.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            flare.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.2F, 0.5F);
            world.addFreshEntity(flare);
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        if (!user.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
