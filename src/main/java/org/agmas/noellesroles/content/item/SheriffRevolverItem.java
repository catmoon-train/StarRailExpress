package org.agmas.noellesroles.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class SheriffRevolverItem extends SkinableItem {
    public SheriffRevolverItem(Item.Properties settings) {
        super(settings.durability(1));
    }

    public static ItemStack createUnloadedStack() {
        ItemStack stack = ModItems.SHERIFF_REVOLVER.getDefaultInstance();
        markEmpty(stack);
        return stack;
    }

    public static boolean isLoaded(ItemStack stack) {
        return stack.is(ModItems.SHERIFF_REVOLVER) && stack.isDamageableItem()
                && stack.getDamageValue() < stack.getMaxDamage();
    }

    public static void markEmpty(ItemStack stack) {
        if (stack.is(ModItems.SHERIFF_REVOLVER) && stack.isDamageableItem()) {
            stack.setDamageValue(stack.getMaxDamage());
        }
    }

    public static void reload(ItemStack stack) {
        if (stack.is(ModItems.SHERIFF_REVOLVER) && stack.isDamageableItem()) {
            stack.setDamageValue(0);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!isLoaded(stack)) {
            if (!world.isClientSide) {
                user.displayClientMessage(Component.translatable("message.noellesroles.sheriff_revolver.empty")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        SREGameWorldComponent gameComponent;
        SRERole role;
        if (world.isClientSide) {
            gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new GunShootPayload(target.getId()));
                CrosshairaddonsCompat.arrowHit();
            } else {
                ClientPlayNetworking.send(new GunShootPayload(-1));
            }

            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();
        } else {
            gameComponent = SREGameWorldComponent.KEY.get(world);
            role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.5F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 1.0F, 0.1F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            if (entity instanceof Player player) {
                return GameUtils.isPlayerAliveAndSurvival(player);
            }
            return false;
        }, 15.0);
    }

    @Override
    public String getItemSkinType() {
        return "revolver";
    }
}
