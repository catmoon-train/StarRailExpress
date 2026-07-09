package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.game.Rooms;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.funny.SREChameleonGameMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 变色龙自带的回合逻辑与 SRE 的游戏循环互斥，这里把两边划清界限：
 *
 * <ul>
 * <li>SRE 托管房间时：屏蔽变色龙的回合 tick、上下线 / 死亡 / 重生 / 换维度处理，
 * 并把它的“找到躲藏者”改判为 SRE 的击杀；同时让 {@code inActiveRound} 返回 false，
 * 免得它那套“回合内只能开门按按钮”的限制盖掉 SRE 自己的交互规则。</li>
 * <li>不在变色龙模式时：方块伪装与匍匐一律禁用，堵住 {@code enterBlockPose} 在无房间时
 * 回落到 {@code GlobalSettings.blockDisguise()} 的口子。</li>
 * </ul>
 */
@Mixin(value = Rooms.class, remap = false)
public abstract class RoomsGateMixin {

    /**
     * 霰弹枪命中（以及变色龙自己的近战 tag）都会走到这里。SRE 主导时改由 SRE 结算击杀，
     * 这样尸体、归属、回放、胜负判定才走同一条管线。
     */
    @Inject(method = "found", at = @At("HEAD"), cancellable = true)
    private static void sre$routeFoundToSreKill(ServerPlayer victim, ServerPlayer seeker, CallbackInfo ci) {
        if (!ChameleonCompat.isRoomActive()) {
            return;
        }
        ci.cancel();
        if (victim == null || seeker == null || !SREChameleonGameMode.isHiderTarget(victim)) {
            return;
        }
        GameUtils.killPlayer(victim, true, seeker, ChameleonCompat.SHOTGUN_DEATH_REASON);
    }

    /** 变色龙的阶段计时、记分板、竞技场边界、欢迎语——SRE 主导时全部不跑。 */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void sre$suppressRoundTick(MinecraftServer server, CallbackInfo ci) {
        if (ChameleonCompat.isRoomActive()) {
            ci.cancel();
        }
    }

    /** 这几个都会淘汰玩家或改写游戏模式 / 传送，交给 SRE 处理。 */
    @Inject(method = { "onDeath", "onRespawn", "onLogin", "onLogout",
            "onDimensionChange" }, at = @At("HEAD"), cancellable = true)
    private static void sre$suppressPlayerLifecycle(ServerPlayer player, CallbackInfo ci) {
        if (ChameleonCompat.isRoomActive()) {
            ci.cancel();
        }
    }

    /** 回合内变色龙只允许开门 / 按按钮，会挡住箱子和商店，SRE 有自己的一套限制。 */
    @Inject(method = "inActiveRound", at = @At("HEAD"), cancellable = true)
    private static void sre$dropVanillaRoundRestrictions(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (ChameleonCompat.isRoomActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "enterBlockPose", at = @At("HEAD"), cancellable = true)
    private static void sre$gateBlockPose(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (!ChameleonCompat.isRoomActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "toggleCrawl", at = @At("HEAD"), cancellable = true)
    private static void sre$gateCrawl(ServerPlayer player, CallbackInfo ci) {
        if (!ChameleonCompat.isRoomActive()) {
            ci.cancel();
        }
    }
}
