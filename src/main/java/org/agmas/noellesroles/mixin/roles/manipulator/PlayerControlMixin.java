package org.agmas.noellesroles.mixin.roles.manipulator;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.roles.manipulator.InControlCCA;
import org.agmas.noellesroles.roles.manipulator.RandomMoveData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.phys.Vec3;

@Mixin(Player.class)
public class PlayerControlMixin {

    // 随机移动数据类

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    public void noe$aiStep(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (player instanceof ServerPlayer serverPlayer) {
            final var gameWorldComponent = GameWorldComponent.KEY.get(serverPlayer.serverLevel());
            if (gameWorldComponent.isRunning() && GameFunctions.isPlayerAliveAndSurvival(serverPlayer)) {
                final var inControlCCA = InControlCCA.KEY.get(serverPlayer);
                if (inControlCCA.isControlling) {
                    // 只有生存模式的玩家才会随机移动
                    if (!GameFunctions.isPlayerAliveAndSurvival(serverPlayer)) {
                        RandomMoveData.randomMoveData.remove(serverPlayer);
                        return;
                    }

                    // 如果玩家正在控制（有输入），则停止随机移动

                    // 获取或创建随机移动数据
                    RandomMoveData data = RandomMoveData.randomMoveData.computeIfAbsent(serverPlayer,
                            k -> new RandomMoveData());

                    // 更新移动数据
                    data.tick();

                    // 小概率随机转向
                    if (data.shouldTurn()) {
                        float angle = RandomMoveData.random.nextFloat() * (float) Math.PI * 2;
                        data.moveDirection = new Vec3(
                                Math.sin(angle) * RandomMoveData.MOVE_SPEED,
                                0,
                                Math.cos(angle) * RandomMoveData.MOVE_SPEED);
                    }

                    // 应用移动
                    applyRandomMovement(serverPlayer, data);

                    // 随机转头
                    applyRandomLooking(serverPlayer);
                }
            }
        }
    }

    private void applyRandomMovement(ServerPlayer player, RandomMoveData data) {
        // 检查玩家是否在地面或可以站立的位置
        if (!player.onGround() && player.getDeltaMovement().y < 0) {
            // 如果玩家正在下落，不应用水平移动
            return;
        }

        // 获取当前位置
        Vec3 currentPos = player.position();

        // 计算新位置
        Vec3 newPos = currentPos.add(data.moveDirection);

        // 检查前方是否有障碍物（简化碰撞检测）
        if (!isPathBlocked(player, currentPos, newPos)) {
            // 设置速度
            player.setDeltaMovement(data.moveDirection.x, player.getDeltaMovement().y, data.moveDirection.z);

            // 保持在地面上
            if (player.onGround()) {
                player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
            }
        } else {
            // 如果路径被阻挡，转向
            data.moveDirection = data.moveDirection.yRot((float) Math.PI / 2);
        }
    }

    private boolean isPathBlocked(ServerPlayer player, Vec3 from, Vec3 to) {
        // 简化的碰撞检测
        // 在实际游戏中，您可能需要更复杂的碰撞检测逻辑

        // 检查玩家前方1格内是否有固体方块
        Vec3 lookVec = player.getLookAngle().normalize();
        Vec3 checkPos = from.add(lookVec.scale(1.0));

        // 检查玩家脚下的方块是否可行走
        Vec3 footPos = from.add(0, -0.5, 0);

        // 简单实现：总是返回false（允许移动）
        // 在实际游戏中，您应该实现真实的碰撞检测
        return false;
    }

    private void applyRandomLooking(ServerPlayer player) {
        // 每10tick有10%的概率随机转头
        if (player.tickCount % 10 == 0 && RandomMoveData.random.nextFloat() < 0.1f) {
            float yaw = player.getYRot() + (RandomMoveData.random.nextFloat() - 0.5f) * 90;
            float pitch = player.getXRot() + (RandomMoveData.random.nextFloat() - 0.5f) * 30;

            // 限制角度范围
            yaw = yaw % 360;
            pitch = Math.max(-90, Math.min(90, pitch));

            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
        }
    }

}