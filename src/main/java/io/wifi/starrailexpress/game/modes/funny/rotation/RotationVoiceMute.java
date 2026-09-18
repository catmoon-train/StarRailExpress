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

package io.wifi.starrailexpress.game.modes.funny.rotation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 职业轮选（轮换模式）开局期间的「全员禁言」。
 *
 * <p>
 * 轮换模式开局会先把所有人丢进轮选界面挑职业，此时语音毫无成本地泄露/串通选择结果，
 * 所以进入轮选阶段时给<b>全部在线玩家</b>挂上 {@link ModEffects#VOICE_SILENCE}：
 * svc（Simple Voice Chat）的麦克风包会在服务端被拦下
 * （见 {@code NoellesrolesVoiceChatPlugin#paranoidEvent}），谁都无法用语音说话。
 *
 * <p>
 * 用法（轮选模式与单选轮选模式一致）：
 * <ul>
 * <li>进入轮选阶段时 {@link #apply} 一次；</li>
 * <li>轮选阶段每 tick {@link #ensure} 补漏（掉线重连会被清效果）；</li>
 * <li>轮选结束 / 游戏强制结算时 {@link #clear}。</li>
 * </ul>
 *
 * 效果时长由调用方给足整个轮选总超时（{@code ROTATION_SAFE_TIME + 40}，补挂时按剩余轮选时间给），
 * 因此即使某条结束路径漏了清理，禁言也会随轮选总超时自然过期，不会永久卡住玩家。
 */
public final class RotationVoiceMute {

    private RotationVoiceMute() {
    }

    /**
     * 给场内所有人上禁言效果，并在动作栏提示一次原因（轮选界面会把这行动作栏消息画在界面上，
     * 见 {@code RoleRotationScreen#renderOverlayMessageOnScreen}）。
     *
     * @param durationTicks 禁言持续时长，建议直接传轮选总超时
     */
    public static void apply(ServerLevel world, int durationTicks) {
        if (world == null)
            return;
        for (ServerPlayer player : world.players()) {
            applyTo(player, durationTicks);
        }
    }

    /**
     * 轮选阶段进行中每 tick 调用的补漏：掉线重连的玩家会被
     * {@code DecServerJoinPlayer} 清掉全部药水效果，中途加入的玩家也没经历过 {@link #apply}，
     * 两者都会在轮选界面开着的时候重新能说话——这里只对「缺失该效果」的玩家补挂一次
     * （含动作栏提示），已经挂上的不动，所以不会每 tick 重复发效果。
     *
     * @param durationTicks 本次补挂的时长，调用方按剩余轮选时间给
     */
    public static void ensure(ServerLevel world, int durationTicks) {
        if (world == null)
            return;
        for (ServerPlayer player : world.players()) {
            if (player.hasEffect(ModEffects.VOICE_SILENCE))
                continue;
            applyTo(player, durationTicks);
        }
    }

    /**
     * 摘掉禁言。轮选阶段结束（正常收尾 / 总超时强制收尾）与游戏强制结算时都要调用，
     * 避免轮选界面关掉之后还有人被闷着。
     */
    public static void clear(ServerLevel world) {
        if (world == null)
            return;
        for (ServerPlayer player : world.players()) {
            player.removeEffect(ModEffects.VOICE_SILENCE);
        }
    }

    private static void applyTo(ServerPlayer player, int durationTicks) {
        player.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, durationTicks, 0, true, false, false));
        player.displayClientMessage(Component.translatable("gui.sre.role_rotation.voice_muted")
                .withStyle(ChatFormatting.GRAY), true);
    }
}
