package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Random;


public class KnifeItem extends Item implements ItemWithSkin {
    public KnifeItem(Properties settings) {
        super(settings);
    }
    /**
     * (target, killer)
     */
    // public static BiConsumer<ServerPlayer, ServerPlayer> PlayerKilledPlayer;
    public static final ResourceLocation ITEM_ID = SRE.id("knife");
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        user.startUsingItem(hand);
        user.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0f, 1.0f);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        super.inventoryTick(itemStack, level, entity, i, bl);
        if (entity instanceof Player player) {
            if (itemStack.get(SREDataComponentTypes.SKIN) == null) {
                // 使用玩家的CCA组件来获取和设置皮肤
                itemStack.set(SREDataComponentTypes.SKIN, SREPlayerSkinsComponent.KEY.get( player).getEquippedSkinForItemType(BuiltInRegistries.ITEM.getKey(this).toString()));
            }
        }
    }




    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (remainingUseTicks >= this.getUseDuration(stack, user) - 8 || !(user instanceof Player attacker) || !world.isClientSide)
            return;
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        final var role = game.getRole(attacker);
        if (role != null){
            if (!role.onUseKnife(attacker)) {
                return;
            }
        }
        HitResult collision = getKnifeTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new KnifeStabPayload(target.getId()));
            CrosshairaddonsCompat.onAttack( target);
        }
    }

    public static HitResult getKnifeTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player), 4f);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }


    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 110;
    }
}