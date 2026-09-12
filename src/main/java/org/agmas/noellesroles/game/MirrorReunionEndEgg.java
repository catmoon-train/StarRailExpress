/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 破镜重圆结局彩蛋：开启后，游戏进入 STOPPING 时全员进入破镜崩裂；
 * 沉底后才黑屏，药水结束后才真正结束。
 * 实际持续时间 = 配置时长 + {@link #EXTRA_END_TICKS}（1.5 秒）。
 *
 * <p>
 * 启用状态保存在 {@link SREConfig#enableMirrorReunionEndEgg}，
 * 可由 {@code /sre:mirror_end_egg on|off} 命令修改并写入配置。
 */
public final class MirrorReunionEndEgg {
    /** 结局整体额外停留，给沉底黑屏留出时间。 */
    public static final int EXTRA_END_TICKS = 30;

    public static int BLACK_DURATION_TICKS = 7 * 20;

    private static boolean delayingThisRound;
    private static int holdTicks;
    private static boolean serverSnap;
    private static boolean clientSnap;
    private static boolean clientWasHolding;

    private MirrorReunionEndEgg() {
    }

    public static void register() {
        OnGameEnd.EVENT.register((world, component) -> {
            delayingThisRound = false;
            holdTicks = 0;
            serverSnap = false;
            clientSnap = false;
            clientWasHolding = false;
        });
    }

    public static boolean isEnabled() {
        SREConfig config = SREConfig.instance();
        return config != null && config.enableMirrorReunionEndEgg;
    }

    public static void setEnabled(boolean value) {
        SREConfig config = SREConfig.instance();
        if (config == null) {
            return;
        }
        config.enableMirrorReunionEndEgg = value;
        SREConfig.HANDLER.save();
    }

    public static void onGameStopping(ServerLevel world, SREGameWorldComponent component) {
        if (!isEnabled()) {
            return;
        }
        int configured = SREConfig.instance().mirrorReunionEndEggTime;
        BLACK_DURATION_TICKS = Math.max(1, configured) + EXTRA_END_TICKS;
        for (ServerPlayer player : world.players()) {
            player.addEffect(
                    new MobEffectInstance(ModEffects.MIRROR_REUNION, BLACK_DURATION_TICKS, 0, false, false, false));
            player.addEffect(
                    new MobEffectInstance(ModEffects.PLAYER_ISOLATION, BLACK_DURATION_TICKS, 0, false, false, false));
            player.addEffect(
                    new MobEffectInstance(ModEffects.INVINCIBLE, BLACK_DURATION_TICKS, 0, false, false, false));
            player.addEffect(
                    new MobEffectInstance(ModEffects.USED_BANED, BLACK_DURATION_TICKS, 0, false, false, false));
            player.addEffect(
                    new MobEffectInstance(ModEffects.SKILL_BANED, BLACK_DURATION_TICKS, 0, false, false, false));
        }
        if (!delayingThisRound) {
            delayingThisRound = true;
            holdTicks = BLACK_DURATION_TICKS;
        }
    }

    /**
     * @return true 时跳过本 tick 的淡出推进，黑屏结束前不要 finalize。
     */
    public static boolean shouldHoldFade(Level world) {
        if (!world.isClientSide()) {
            if (!delayingThisRound) {
                return false;
            }
            if (holdTicks > 0) {
                holdTicks--;
                return true;
            }
            delayingThisRound = false;
            serverSnap = true;
            return false;
        }
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        var effect = player.getEffect(ModEffects.MIRROR_REUNION);
        boolean hold = effect != null && effect.getDuration() > 1;
        if (clientWasHolding && !hold) {
            clientSnap = true;
        }
        clientWasHolding = hold;
        return hold;
    }

    public static boolean consumeSnapFinalize(Level world) {
        if (world.isClientSide()) {
            boolean snap = clientSnap;
            clientSnap = false;
            return snap;
        }
        boolean snap = serverSnap;
        serverSnap = false;
        return snap;
    }
}
