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

package org.agmas.noellesroles.client;

import dev.doctor4t.ratatouille.client.util.ambience.AmbienceUtil;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbience;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.agmas.noellesroles.game.moment.MomentType;
import org.agmas.noellesroles.packet.MomentMusicS2CPacket;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端统一时刻音乐播放器。
 *
 * <p>
 * 服务端 {@code MomentManager} 裁决「当前应播放哪一条时刻音乐」并通过
 * {@link MomentMusicS2CPacket} 下发；本类保存该结果，并为每个带音乐的时刻注册一条
 * 循环环境音（{@link MyBackgroundAmbience}，自带循环与淡入淡出）。每条环境音的谓词
 * 仅为「当前时刻 == 自己」，因此同一时刻最多只有一条音乐在响——旧时刻音乐自动淡出、
 * 新时刻音乐自动淡入，时刻结束时下发 {@code -1}，全部谓词归假，音乐立即停止。
 * </p>
 */
public final class MomentMusicClient {

    @Nullable
    private static MomentType current;

    private MomentMusicClient() {
    }

    /** 当前正在播放的时刻音乐，可能为 {@code null}。 */
    @Nullable
    public static MomentType current() {
        return current;
    }

    public static void register() {
        // 每个带音乐的时刻各注册一条环境音，仅当它被服务端选为「当前音乐」时播放。
        for (MomentType type : MomentType.values()) {
            if (!type.hasMusic()) {
                continue;
            }
            final MomentType moment = type;
            AmbienceUtil.registerBackgroundAmbience(new MyBackgroundAmbience(
                    type.music(), type.source(),
                    player -> current == moment,
                    type.volume(), type.fadeIn(), type.fadeOut()));
        }
        ClientPlayNetworking.registerGlobalReceiver(MomentMusicS2CPacket.ID, (payload, ctx) -> ctx.client().execute(
                () -> current = MomentType.byOrdinal(payload.ordinal())));
    }
}
