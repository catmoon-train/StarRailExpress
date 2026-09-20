package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.api.ClimbState;
import org.agmas.noellesroles.api.PlayerClimbState;
import org.agmas.noellesroles.packet.ScoutClimbC2SPacket;
import org.agmas.noellesroles.role.bouns.roles.ScoutRole;

/**
 * 攀爬客户端：起始判定 + 本地预判 + 体力镜像。
 *
 * <p>真正让玩家「贴着墙移动」的是 {@code ScoutClimbTravelMixin}；
 * 这里只负责决定「什么时候开始 / 结束攀爬」，以及把开始 / 结束通知服务端。
 *
 * <p>攀爬状态挂在 Player 上（{@link ClimbState}，和体力条同一套路子），
 * 两端各自维护、不额外同步：本类只写 {@code predicting}，
 * 服务端只写 {@code climbing}。体力同样是两端各自模拟，所以这里也镜像扣一次。
 */
public final class ScoutClimbClient {

    private static boolean prevJumpDown = false;
    private static boolean prevShiftDown = false;

    private ScoutClimbClient() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(ScoutClimbClient::onUseBlock);
        ClientTickEvents.END_CLIENT_TICK.register(ScoutClimbClient::tick);
    }

    // ==================== 起始判定：右键墙壁 ====================

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof LocalPlayer local) || !level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!canClimb(local)) {
            return InteractionResult.PASS;
        }
        // 空手才算「抓墙」：否则右键是给手上物品用的（开枪 / 用道具），不能被我们吞掉
        if (!local.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        Direction face = hit.getDirection();
        BlockPos pos = hit.getBlockPos();
        if (!ScoutRole.isClimbableWallBlock(level, pos, level.getBlockState(pos), face)) {
            // 必须是一面竖直的、有碰撞箱的墙，且不是门 / 按钮这类交互方块
            return InteractionResult.PASS;
        }
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        if (!ScoutRole.isHuggingWall(local, normal)) {
            // 必须紧贴墙面：和墙之间还有缝就抓不住
            return InteractionResult.PASS;
        }
        if (!tryStart(local, normal)) {
            return InteractionResult.PASS;
        }
        // 右键墙壁 = 抓墙：吞掉这次方块交互（不会顺手开门 / 按按钮）
        return InteractionResult.FAIL;
    }

    // ==================== 每 tick ====================

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            prevJumpDown = false;
            prevShiftDown = false;
            return;
        }
        boolean jumpDown = client.options.keyJump.isDown();
        boolean shiftDown = player.isShiftKeyDown();
        ClimbState state = PlayerClimbState.of(player);
        if (state == null) {
            prevJumpDown = jumpDown;
            prevShiftDown = shiftDown;
            return;
        }

        if (!state.predicting) {
            // 起手只有一条路——空手右键紧贴的墙面；空格不再是触发方式（避免误触发）
            state.reset();
            prevJumpDown = jumpDown;
            prevShiftDown = shiftDown;
            return;
        }

        // ── 正在预判攀爬 ──
        boolean emptyHand = player.getMainHandItem().isEmpty();
        if (!canClimb(player) || !emptyHand || !ScoutRole.roleAllowsClimb(player)) {
            // 通用条件不满足 / 手上有物品 / 职业（含地图）不再允许攀爬 → 松手
            stopPredict(player, state, null);
            prevJumpDown = jumpDown;
            prevShiftDown = shiftDown;
            return;
        }

        ScoutRole.applyClimbPhysics(player);

        // 客户端镜像扣体力：两端各自模拟，只有都扣 HUD 体力条才会跟着掉
        double[] move = state.pollMovement(player.getX(), player.getY(), player.getZ());
        if (!ScoutRole.consumeClimbStamina(player, ScoutRole.climbDrain(move[0], move[1], move[2]))) {
            stopPredict(player, state, null);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.climb.stamina_out").withStyle(ChatFormatting.RED),
                    true);
            prevJumpDown = jumpDown;
            prevShiftDown = shiftDown;
            return;
        }

        // ── 脱手判定 ──
        Vec3 normal = state.normal;
        boolean jumpPressed = jumpDown && !prevJumpDown;
        // 只有「攀爬过程中新按下」潜行才算松手：起手前就按着潜行不该立刻掉下去
        boolean shiftPressed = shiftDown && !prevShiftDown;
        if (shiftPressed) {
            stopPredict(player, state, Vec3.ZERO);
        } else if (jumpPressed) {
            // 空格只是取消攀爬：清掉当前攀爬速度，不给任何向外 / 向上的推力
            stopPredict(player, state, Vec3.ZERO);
        } else if (!ScoutRole.isHuggingWall(player, normal)) {
            // 墙到头了：向上爬时先尝试翻上墙顶（爬上/上岸），翻不上去才按原来的小助力脱手
            boolean movingUp = move[1] > ScoutRole.CLIMB_MOVE_EPSILON;
            if (movingUp && ScoutRole.tryClimbOntoBlock(player, normal)) {
                stopPredict(player, state, null);
            } else {
                stopPredict(player, state, movingUp
                        ? new Vec3(-normal.x * 0.22D, 0.34D, -normal.z * 0.22D)
                        : null);
            }
        }
        prevJumpDown = jumpDown;
        prevShiftDown = shiftDown;
    }

    // ==================== 工具 ====================

    /** 与职业无关的通用前置条件；「这个职业此刻能不能爬」由 {@link ScoutRole#roleAllowsClimb} 决定 */
    private static boolean canClimb(LocalPlayer player) {
        if (player.isSpectator() || player.isPassenger()) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        return game != null && game.isRunning();
    }

    private static boolean tryStart(LocalPlayer player, Vec3 normal) {
        ClimbState state = PlayerClimbState.of(player);
        if (state == null || state.predicting) {
            return false;
        }
        if (!player.getMainHandItem().isEmpty()) {
            // 抓墙必须空手
            return false;
        }
        if (!ScoutRole.roleAllowsClimb(player) || !ScoutRole.hasClimbStamina(player)) {
            return false;
        }
        state.predicting = true;
        state.normal = normal;
        state.hasLastPos = false;
        ClientPlayNetworking.send(ScoutClimbC2SPacket.start(normal));
        return true;
    }

    private static void stopPredict(LocalPlayer player, ClimbState state, Vec3 detachVelocity) {
        boolean wasClimbing = state.predicting;
        state.predicting = false;
        state.hasLastPos = false;
        player.setNoGravity(false);
        if (!wasClimbing) {
            return;
        }
        if (detachVelocity != null) {
            player.setDeltaMovement(detachVelocity);
        }
        ClientPlayNetworking.send(ScoutClimbC2SPacket.stop());
    }
}
