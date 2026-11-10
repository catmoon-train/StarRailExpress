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

package org.agmas.noellesroles.game.moment;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.NRSounds;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.StupidExpress;

/**
 * 项目内全部「时刻」的统一枚举。
 *
 * <p>
 * 每个时刻携带自己的主题音乐（{@link #music()}）、音量、播放声道与淡入淡出时长。
 * 音乐是否真正播放以及播放哪一个，由服务端 {@link MomentManager} 统一裁决，
 * 客户端 {@code MomentMusicClient} 依据裁决结果循环播放对应音乐——两种历史实现
 * （服务端一次性音效 + 停播包 / 客户端谓词环境音）都被收敛到这一处。
 * </p>
 *
 * <p>
 * {@link #DESPERADO}（亡命徒时刻，由难民修饰符触发）具有插队语义：它总是压在其
 * 它时刻之上，触发期间其它时刻暂停，结束后按回溯结果恢复。
 * </p>
 */
public enum MomentType {
    /** 阿蒙时刻（终幕）。 */
    AMON("amon", NRSounds.A_MENG, 1.0F, SoundSource.MASTER, 10, 20),
    /** 恶魔之妹时刻（芙兰朵露最后立会）。 */
    FURANDORU("furandoru", NRSounds.ROLES_FURANDORU_FINAL, 1.0F, SoundSource.MASTER, 10, 20),
    /** 黑警时刻。 */
    LICENSED_VILLAIN("licensed_villain", NRSounds.FATE, 0.75F, SoundSource.MASTER, 10, 20),
    /** 疫使时刻。 */
    INFECTED("infected", SoundEvents.WITCH_CELEBRATE, 1.0F, SoundSource.MASTER, 10, 20),
    /** 纵火犯时刻。 */
    ARSONIST("arsonist", NRSounds.ARSONIST, 0.57F, SoundSource.MASTER, 10, 20),
    /** 亡命徒时刻（难民修饰符触发，可随时插队）。 */
    DESPERADO("desperado", StupidExpress.SOUND_REGUGEE, 1.0F, SoundSource.AMBIENT, 10, 20),
    /** 逐日时刻（终末追逐）。 */
    DAYWALKER("daywalker", NRSounds.DAYWALKER, 1.0F, SoundSource.MASTER, 10, 20);

    private final String id;
    @Nullable
    private final SoundEvent music;
    private final float volume;
    private final SoundSource source;
    private final int fadeIn;
    private final int fadeOut;

    MomentType(String id, @Nullable SoundEvent music, float volume, SoundSource source, int fadeIn, int fadeOut) {
        this.id = id;
        this.music = music;
        this.volume = volume;
        this.source = source;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
    }

    /** 稳定字符串 ID，用于调试/命令/存档。 */
    public String id() {
        return id;
    }

    /** 该时刻的主题音乐，可能为 {@code null}（表示该时刻无音乐）。 */
    @Nullable
    public SoundEvent music() {
        return music;
    }

    public boolean hasMusic() {
        return music != null;
    }

    public float volume() {
        return volume;
    }

    public SoundSource source() {
        return source;
    }

    public int fadeIn() {
        return fadeIn;
    }

    public int fadeOut() {
        return fadeOut;
    }

    /** 按枚举序号取时刻，越界或负数返回 {@code null}。 */
    @Nullable
    public static MomentType byOrdinal(int ordinal) {
        MomentType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return null;
        }
        return values[ordinal];
    }

    /** 按字符串 ID 取时刻，未匹配返回 {@code null}。 */
    @Nullable
    public static MomentType byId(String id) {
        for (MomentType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
