package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.ChargeableItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.vigilante.MagicApprenticeRoleData;

/** 见习法杖：蓄力 0.3 秒后释放当前选中的法术。 */
public class ApprenticeWandItem extends Item implements ChargeableItem {
    public static final int CHARGE_TICKS = 6;
    public static final int COOLDOWN_TICKS = 12 * 20;

    public ApprenticeWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(ModItems.APPRENTICE_WAND)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        int charged = getUseDuration(stack, entity) - remainingUseTicks;
        if (charged < CHARGE_TICKS) return;
        MagicApprenticeRoleData data = RoleData.getNullable(MagicApprenticeRoleData.class, player);
        if (data != null && data.castSelectedSpell(player)) {
            data.wandCooldownTicks = COOLDOWN_TICKS;
            data.sync();
            player.getCooldowns().addCooldown(ModItems.APPRENTICE_WAND, COOLDOWN_TICKS);
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return;
        int charged = getUseDuration(stack, entity) - remainingUseTicks;
        if (charged < CHARGE_TICKS) return;
        Vec3 look = player.getViewVector(1.0f);
        Vec3 tip = player.getEyePosition().add(look.scale(0.75));
        server.sendParticles(ParticleTypes.ENCHANT, tip.x, tip.y, tip.z, 1, .04, .04, .04, .02);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) { return CHARGE_TICKS; }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem) {
        return Math.max(0f, Math.min(1f, ticksUsingItem / (float) CHARGE_TICKS));
    }

    @Override
    public boolean hasSpecialVisualEffects(ItemStack stack, Player player) { return true; }
}
