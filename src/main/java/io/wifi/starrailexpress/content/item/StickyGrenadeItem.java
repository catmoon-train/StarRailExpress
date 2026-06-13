package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.StickyGrenadeEntity;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 粘性雷 — 投掷后粘在墙面或玩家身上，短延时后爆炸。
 */
public class StickyGrenadeItem extends SkinableItem {
    public static final int MAX_CHARGE_TIME = 15; // 0.75 秒蓄力

    public StickyGrenadeItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (!world.isClientSide) {
            if (user instanceof Player player && player.getCooldowns().isOnCooldown(stack.getItem()))
                return;
            if (user instanceof Player player) {
                if (!player.isCreative()
                        && !SREGameWorldComponent.KEY.get(player.level()).isRole(player,
                                SpecialGameModeRoles.SUPER_LOOSE_END)) {
                    player.getCooldowns().addCooldown(stack.getItem(), SREConfig.instance().grenadeCooldown);
                }
            }

            int chargeTime = this.getUseDuration(stack, user) - remainingUseTicks;
            chargeTime = Math.max(0, Math.min(chargeTime, MAX_CHARGE_TIME));

            world.playSound(null, user.getX(), user.getY(), user.getZ(), TMMSounds.ITEM_GRENADE_THROW,
                    SoundSource.NEUTRAL, 0.5F, 0.8F + (world.random.nextFloat() - .5f) / 10f);

            StickyGrenadeEntity grenade = new StickyGrenadeEntity(TMMEntities.STICKY_GRENADE, world);
            grenade.setOwner(user);
            grenade.setPosRaw(user.getX(), user.getEyeY() - 0.1, user.getZ());

            float velocity = 0.35F + (0.65F * (float) chargeTime / MAX_CHARGE_TIME);
            grenade.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, velocity, 1.0F);
            world.addFreshEntity(grenade);

            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
        }
        stack.consume(1, user);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public String getItemSkinType() {
        return "sticky_grenade";
    }
}
