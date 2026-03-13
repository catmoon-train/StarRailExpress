package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.network.original.SniperShootPayload;
import io.wifi.starrailexpress.util.SniperProjectileUtil;
import io.wifi.StarRailExpressID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SniperRifleItem extends Item {
    public static final String SCOPE_ATTACHED_KEY = "ScopeAttached";
    public static final String AMMO_COUNT_KEY = "AmmoCount";
    public static final int MAX_AMMO = 2;

    public SniperRifleItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (world.isClientSide) {
            // 检查冷却
            if (user.getCooldowns().isOnCooldown(stack.getItem())) {
                return InteractionResultHolder.fail(stack);
            }

            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null) {
                    if (!role.onUseGun(user)) {
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }

            // 检查是否蹲下
            boolean isSneaking = user.isShiftKeyDown();

            if (isSneaking) {
                // 蹲下右键：同普通右键（射击）
                if (hasScopeAttached(stack)) {
                    // 已安装倍镜，右键先切换到瞄准模式，再次右键射击
                    // 第一次右键：进入瞄准模式
                    if (!ScopeOverlayRenderer.isInScopeView()) {
                        ScopeOverlayRenderer.setInScopeView(true);
                        return InteractionResultHolder.success(stack);
                    } else {
                        ScopeOverlayRenderer.setInScopeView(false);
                        return InteractionResultHolder.success(stack);
                    }
                    // // 已在瞄准模式，射击
                    // int currentAmmo = getAmmoCount(stack);
                    // if (currentAmmo <= 0) {
                    //     ScopeOverlayRenderer.setInScopeView(false);
                    //     return InteractionResultHolder.fail(stack); // 没有子弹
                    // }
                    // shoot(world, user, stack);
                    // ScopeOverlayRenderer.setInScopeView(false);
                } else {
                    // 未安装倍镜，直接射击
                    int currentAmmo = getAmmoCount(stack);
                    if (currentAmmo <= 0) {
                        return InteractionResultHolder.fail(stack); // 没有子弹
                    }
                    shoot(world, user, stack);
                }
            } else {
                // 不蹲下：右键射击
                if (hasScopeAttached(stack)) {
                    // // 已安装倍镜，右键先切换到瞄准模式，再次右键射击
                    // // 第一次右键：进入瞄准模式
                    // if (!ScopeOverlayRenderer.isInScopeView()) {
                    //     ScopeOverlayRenderer.setInScopeView(true);
                    //     return InteractionResultHolder.success(stack);
                    // }
                    // 已在瞄准模式，射击
                    int currentAmmo = getAmmoCount(stack);
                    if (currentAmmo <= 0) {
                        ScopeOverlayRenderer.setInScopeView(false);
                        return InteractionResultHolder.fail(stack); // 没有子弹
                    }
                    shoot(world, user, stack);
                    ScopeOverlayRenderer.setInScopeView(false);
                } else {
                    // 未安装倍镜，直接射击
                    int currentAmmo = getAmmoCount(stack);
                    if (currentAmmo <= 0) {
                        return InteractionResultHolder.fail(stack); // 没有子弹
                    }
                    shoot(world, user, stack);
                }
            }
        } else {
            // 服务端逻辑
            StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(world);
            final var role = gameWorldComponent.getRole(user);
            if (role != null) {
                if (!role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    private void shoot(Level world, Player user, ItemStack stack) {
        if (world.isClientSide) {
            // 客户端射击逻辑
            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new SniperShootPayload(SniperShootPayload.Action.SHOOT, target.getId()));
                CrosshairaddonsCompat.arrowHit();
                // 在路径上生成 SMOKE 粒子
                spawnSmokeTrail(world, user, collision.getLocation());
            } else {
                ClientPlayNetworking.send(new SniperShootPayload(SniperShootPayload.Action.SHOOT, -1));
                // 在路径上生成 SMOKE 粒子（到视线终点）
                spawnSmokeTrail(world, user, collision.getLocation());
            }
            user.setXRot(user.getXRot() - 4);
            spawnHandParticle();
            // 客户端设置冷却，防止重复射击
            user.getCooldowns().addCooldown(stack.getItem(), 80); // 4 秒冷却
        }
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

    /**
     * 在子弹路径上生成 SMOKE 粒子轨迹
     */
    private void spawnSmokeTrail(Level world, Player user, net.minecraft.world.phys.Vec3 hitPos) {
        // 获取玩家眼睛位置（子弹起点）
        net.minecraft.world.phys.Vec3 startPos = user.getEyePosition();

        // 计算从起点到终点的向量
        net.minecraft.world.phys.Vec3 direction = hitPos.subtract(startPos);
        double distance = direction.length();

        // 归一化方向向量
        direction = direction.normalize();

        // 每隔一定距离生成一个粒子
        double stepSize = 0.5; // 每 0.5 格生成一个粒子
        int particleCount = (int) (distance / stepSize);

        for (int i = 0; i < particleCount; i++) {
            double ratio = (double) i / particleCount;
            net.minecraft.world.phys.Vec3 particlePos = startPos.add(
                    direction.x * distance * ratio,
                    direction.y * distance * ratio,
                    direction.z * distance * ratio);

            // 添加随机偏移，使烟雾更自然
            double offsetX = (world.random.nextDouble() - 0.5) * 0.2;
            double offsetY = (world.random.nextDouble() - 0.5) * 0.2;
            double offsetZ = (world.random.nextDouble() - 0.5) * 0.2;

            world.addParticle(
                    net.minecraft.core.particles.ParticleTypes.SMOKE,
                    particlePos.x + offsetX,
                    particlePos.y + offsetY,
                    particlePos.z + offsetZ,
                    0, 0.02, 0 // 缓慢上升的速度
            );
        }
    }

    public static HitResult getGunTarget(Player user) {
        return SniperProjectileUtil.getSniperHitResult(user,
                entity -> entity instanceof Player player && GameFunctions.isPlayerAliveAndSurvival(player), 200F);
    }

    // 倍镜相关方法
    public static boolean hasScopeAttached(ItemStack stack) {
        return stack.getOrDefault(SREDataComponentTypes.SCOPE_ATTACHED, false);
    }

    public static void setScopeAttached(ItemStack stack, boolean attached) {
        stack.set(SREDataComponentTypes.SCOPE_ATTACHED, attached);
    }

    // 子弹相关方法
    public static int getAmmoCount(ItemStack stack) {
        return stack.getOrDefault(SREDataComponentTypes.AMMO_COUNT, 0);
    }

    public static void setAmmoCount(ItemStack stack, int count) {
        stack.set(SREDataComponentTypes.AMMO_COUNT, Math.min(count, MAX_AMMO));
    }

    public static void consumeAmmo(ItemStack stack) {
        int currentAmmo = getAmmoCount(stack);
        setAmmoCount(stack, Math.max(0, currentAmmo - 1));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        int ammo = getAmmoCount(stack);
        boolean hasScope = hasScopeAttached(stack);
        tooltip.add(Component.translatable("item.starrailexpress.sniper_rifle.ammo", ammo, MAX_AMMO));
        tooltip.add(Component.translatable("item.starrailexpress.sniper_rifle.scope",
                hasScope ? Component.translatable("item.starrailexpress.sniper_rifle.scope.installed")
                        : Component.translatable("item.starrailexpress.sniper_rifle.scope.not_installed")));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        int ammo = getAmmoCount(stack);
        return (int) ((double) ammo / MAX_AMMO * 13);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0xFF5500;
    }
}
