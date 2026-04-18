package org.agmas.noellesroles.game.roles.Innocent.fool;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 处刑者手枪
 *
 * 愚者开局自带，初始子弹数1。
 * 只能对拥有"异端"效果的玩家造成伤害（一击必杀，无视护甲）。
 * 对其他玩家开枪无效，不消耗子弹，播放空枪音效。
 * 子弹无法购买，只能通过塔罗会补充。
 */
public class ExecutionerGunItem extends Item {

    public ExecutionerGunItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (world.isClientSide) {
            SREGameWorldComponent gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                SRERole role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new FoolExecutionerGunShootC2SPacket(target.getId()));
            } else {
                ClientPlayNetworking.send(new FoolExecutionerGunShootC2SPacket(-1));
            }

            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();
            user.getCooldowns().addCooldown(this, 5 * 20);
        } else {
            SREGameWorldComponent gameComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(world);
            SRERole role = gameComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
            user.getCooldowns().addCooldown(this, 5 * 20);
        }

        return InteractionResultHolder.consume(stack);
    }

    /**
     * 处理服务端射击逻辑
     * 由 GunShootPayload 处理器调用
     */
    public static boolean handleServerShoot(ServerPlayer shooter, Player target) {
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(shooter.level());
        if (!gameComponent.isRole(shooter, ModRoles.THE_FOOL)) {
            return false;
        }

        FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(shooter);

        // 检查目标是否为当前异端
        long currentTick = shooter.level().getGameTime();
        if (comp.hasActiveHeretic(currentTick) && comp.hereticTarget != null
                && target.getUUID().equals(comp.hereticTarget)) {
            return true;
        }

        // 目标不是异端或没有子弹——空枪
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0F, 1.0F);
        shooter.displayClientMessage(
                Component.translatable("message.noellesroles.fool.empty_shot").withStyle(ChatFormatting.GRAY),
                true);
        return false;
    }

    public static void spawnHandParticle() {
        HandParticle handParticle = (new HandParticle())
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.5F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[] { 1.0F, 0.1F }).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    public static HitResult getGunTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> {
            if (entity instanceof Player player) {
                return GameUtils.isPlayerAliveAndSurvival(player);
            }
            return false;
        }, 15.0);
    }

    public static boolean hasExecutionerGun(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.EXECUTIONER_GUN)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.EXECUTIONER_GUN)) {
                return true;
            }
        }
        return false;
    }

    public static void ensureExecutionerGun(ServerPlayer player) {
        if (!hasExecutionerGun(player)) {
            player.getInventory().add(new ItemStack(ModItems.EXECUTIONER_GUN));
        }
    }
}
