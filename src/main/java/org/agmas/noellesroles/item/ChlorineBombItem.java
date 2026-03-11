package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.entity.ChlorineBombEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.NotNull;

/**
 * 氯气弹物品
 * - 右键投掷，落地时对半径3格内的玩家造成中毒效果
 * - 落地时播放FIRE_EXTINGUISH（火熄灭）声音
 */
public class ChlorineBombItem extends Item {

    public ChlorineBombItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        // 播放投掷音效
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                0.5F, 1F + (world.random.nextFloat() - .5f) / 10f);

        if (!world.isClientSide) {
            // 创建氯气弹实体
            ChlorineBombEntity chlorineBomb = new ChlorineBombEntity(ModEntities.CHLORINE_BOMB, world);
            chlorineBomb.setOwner(user);
            chlorineBomb.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());
            chlorineBomb.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 0.5F, 1.0F);
            world.addFreshEntity(chlorineBomb);
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);

        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }
}
