package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import org.agmas.noellesroles.packet.BanditRevolverShootPayload;
import org.jetbrains.annotations.NotNull;

import static io.wifi.starrailexpress.content.item.RevolverItem.spawnHandParticle;

public class BanditRevolverItem extends SkinableItem implements TrainWeapon {

    public BanditRevolverItem(Item.Properties settings) {
        super(settings);
    }

    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        if (world.isClientSide) {
            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null) {
                    if (!role.onUseGun(user)) {
                        return InteractionResultHolder.fail(user.getItemInHand(hand));
                    }
                }
            }
            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult) {
                EntityHitResult entityHitResult = (EntityHitResult) collision;
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new BanditRevolverShootPayload(target.getId()));
            } else {
                ClientPlayNetworking.send(new BanditRevolverShootPayload(-1));
            }
            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();
        } else {
            final var gameComponent = SREGameWorldComponent.KEY.get(world);
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null) {
                    if (!role.onUseGun(user)) {
                        return InteractionResultHolder.fail(user.getItemInHand(hand));
                    }
                }
            }
        }
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }

    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            boolean var10000;
            if (entity instanceof Player player) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    var10000 = true;
                    return var10000;
                }
            }

            var10000 = false;
            return var10000;
        }, (double) 20.0F);
    }

    @Override
    public String getItemSkinType() {
        return "revolver";
    }
}
