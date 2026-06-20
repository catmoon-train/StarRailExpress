package io.wifi.starrailexpress.content.item;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.event.AllowShootRevolverDrop;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 制式左轮 - 继承左轮手枪材质和功能，但命中玩家时永不掉落。
 * 冷却和数值与左轮手枪相同。
 */
public class StandardRevolverItem extends SkinableItem {
    public static final ResourceLocation ITEM_ID = ResourceLocation.fromNamespaceAndPath("starrailexpress",
            "standard_revolver");

    public StandardRevolverItem(Properties settings) {
        super(settings.durability(4));
    }

    public static void registerEvents() {
        // 制式左轮永不掉落：无论谁持有，命中玩家后枪都不会掉落
        AllowShootRevolverDrop.EVENT.register((player, target) -> {
            if (player.getMainHandItem().is(TMMItems.STANDARD_REVOLVER)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (world.isClientSide) {
            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null) {
                    if (!role.onUseGun(user)) {
                        return InteractionResultHolder.fail(stack);
                    }
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
            user.setXRot(user.getXRot() - 4);
            spawnHandParticle();
        } else {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
            final var role = gameWorldComponent.getRole(user);
            if (role != null) {
                if (!role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    public static void spawnHandParticle() {
        HandParticle handParticle = new HandParticle()
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1f, 0.275f, -0.2f)
                .setMaxAge(3)
                .setSize(0.5f)
                .setVelocity(0f, 0f, 0f)
                .setLight(15, 15)
                .setAlpha(1f, 0.1f)
                .setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> {
                    return entity instanceof Player player && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)
                            || entity instanceof PuppeteerBodyEntity;
                }, 20f);
    }

    @Override
    public String getItemSkinType() {
        return "revolver"; // 继承左轮手枪的皮肤
    }
}
