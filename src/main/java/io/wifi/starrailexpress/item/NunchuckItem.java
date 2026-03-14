package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.original.NunchuckHitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class NunchuckItem extends Item {
    public NunchuckItem(Properties settings) {
        super(settings.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 处理右键和Shift+右键
        // 左键和Shift+左键在 PlayerEntityMixin.attack 中处理

        if (world.isClientSide) {
            final var gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                final var role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            // 获取目标
            Player target = getTargetPlayer(user);
            if (target != null) {
                // 右键：向左击退 (direction = 0)
                // Shift+右键：向前击退 (direction = 3)
                int direction = user.isShiftKeyDown() ? 3 : 0;
                ClientPlayNetworking.send(new NunchuckHitPayload(target.getId(), direction));
            }
        } else {
            // 服务端检查角色权限
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(world);
            final var role = gameWorldComponent.getRole(user);
            if (role != null && !role.onUseGun(user)) {
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.consume(stack);
    }

    public static Player getTargetPlayer(Player user) {
        // 在前方4格内找到最近的玩家
        Vec3 start = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        Vec3 end = start.add(look.scale(4.0));

        HitResult hitResult = ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof Player player &&
                        GameUtils.isPlayerAliveAndSurvival(player) &&
                        player != user,
                4.0F);

        if (hitResult instanceof EntityHitResult entityHitResult &&
                entityHitResult.getEntity() instanceof Player target) {
            return target;
        }

        return null;
    }

    /**
     * 计算击退方向
     * @param direction 0: 向左, 1: 向右, 2: 向后, 3: 向前
     * @return 击退方向的Vec3向量
     */
    public static Vec3 getKnockbackDirection(Player attacker, int direction) {
        Vec3 look = attacker.getLookAngle();
        Vec3 up = new Vec3(0, 1, 0);

        Vec3 left = look.cross(up).normalize();
        Vec3 right = left.scale(-1);
        Vec3 backward = look.scale(-1);
        Vec3 forward = look;

        return switch (direction) {
            case 0 -> left;  // 向左
            case 1 -> right; // 向右
            case 2 -> backward; // 向后
            case 3 -> forward; // 向前
            default -> backward;
        };
    }

    /**
     * 检查玩家是否贴在方块侧面
     */
    public static boolean isPlayerNearBlock(Player player) {
        var pos = player.blockPosition();

        // 检查玩家周围四个水平方向的方块
        int[][] offsets = {
            {1, 0, 0},   // +X
            {-1, 0, 0},  // -X
            {0, 0, 1},   // +Z
            {0, 0, -1}   // -Z
        };

        for (int[] offset : offsets) {
            var checkPos = pos.offset(offset[0], offset[1], offset[2]);
            if (!player.level().getBlockState(checkPos).isAir()) {
                return true;
            }
        }
        return false;
    }
}
