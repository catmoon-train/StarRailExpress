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
 * <b>音乐与触发音效是两回事</b>：{@link #music()} 为空表示该时刻没有音乐；
 * {@link #cue()} 只是一次触发瞬间播放的小段音效（自带声道/音量/音调，不循环、
 * 不进音乐裁决），由 {@link MomentManager#trigger} 在真正触发时播放一次。
 * 例如 {@link #INFECTED} 没有音乐、只有触发时的女巫庆祝音效；{@link #ARSONIST}
 * 则在音乐之外另有触发时的凋灵生成音效。
 * </p>
 *
 * <p>
 * <b>一局一次</b>：除 {@link #DESPERADO} 这类由修饰符触发、允许反复插队的时刻
 * （{@link #repeatable()} 为 {@code true}）外，时刻默认一局只能触发一次，由
 * {@link MomentManager} 统一记录；触发后即便场上环境又达不到触发要求，也只是
 * 继续保持该时刻的状态与效果，不关闭、不再次触发。
 * </p>
 *
 * <p>
 * {@link #DESPERADO}（亡命徒时刻，由难民修饰符触发）具有插队语义：它总是压在其
 * 它时刻之上，触发期间其它时刻暂停，结束后按回溯结果恢复。
 * </p>
 */
public enum MomentType {
    /** 阿蒙时刻（终幕）。 */
    AMON("amon", NRSounds.A_MENG, null, 1.0F, SoundSource.MASTER, 10, 20),
    /** 恶魔之妹时刻（芙兰朵露最后立会）。 */
    FURANDORU("furandoru", NRSounds.ROLES_FURANDORU_FINAL, null, 1.0F, SoundSource.MASTER, 10, 20),
    /** 黑警时刻。 */
    LICENSED_VILLAIN("licensed_villain", NRSounds.FATE, null, 0.75F, SoundSource.MASTER, 10, 20),
    /** 疫使时刻：没有音乐，只有触发瞬间的一小段提示音效。 */
    INFECTED("infected", null, new Cue(SoundEvents.WITCH_CELEBRATE, SoundSource.MASTER, 1.0F, 1.0F),
            1.0F, SoundSource.MASTER, 10, 20),
    /** 纵火犯时刻：音乐之外另有触发瞬间的凋灵生成音效。 */
    ARSONIST("arsonist", NRSounds.ARSONIST,
            new Cue(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5F, 1.15F),
            0.57F, SoundSource.MASTER, 10, 20),
    /** 亡命徒时刻（难民修饰符触发，可随时插队、允许一局内反复触发）。 */
    DESPERADO("desperado", StupidExpress.SOUND_REGUGEE, null, 1.0F, SoundSource.AMBIENT, 10, 20, true),
    /** 逐日时刻（终末追逐）。 */
    DAYWALKER("daywalker", NRSounds.DAYWALKER, null, 1.0F, SoundSource.MASTER, 10, 20);

    /**
     * 触发瞬间的一次性音效及其播放参数（不是音乐、不循环、不参与音乐裁决）。
     *
     * @param sound  音效
     * @param source 声道
     * @param volume 音量
     * @param pitch  音调
     */
    public record Cue(SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    private final String id;
    @Nullable
    private final SoundEvent music;
    /** 触发瞬间的一次性音效及播放参数，不是音乐；可能为 {@code null}。 */
    @Nullable
    private final Cue cue;
    private final float volume;
    private final SoundSource source;
    private final int fadeIn;
    private final int fadeOut;
    /** 是否允许一局内重复触发（仅修饰符触发的时刻为 {@code true}）。 */
    private final boolean repeatable;

    MomentType(String id, @Nullable SoundEvent music, @Nullable Cue cue, float volume,
            SoundSource source, int fadeIn, int fadeOut) {
        this(id, music, cue, volume, source, fadeIn, fadeOut, false);
    }

    MomentType(String id, @Nullable SoundEvent music, @Nullable Cue cue, float volume,
            SoundSource source, int fadeIn, int fadeOut, boolean repeatable) {
        this.id = id;
        this.music = music;
        this.cue = cue;
        this.volume = volume;
        this.source = source;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
        this.repeatable = repeatable;
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

    /**
     * 该时刻触发瞬间播放的一次性音效及播放参数（不循环、不参与音乐裁决），可能为 {@code null}。
     * 由 {@link MomentManager#trigger} 在本局真正触发该时刻时播放一次。
     */
    @Nullable
    public Cue cue() {
        return cue;
    }

    public boolean hasMusic() {
        return music != null;
    }

    /** 是否允许一局内重复触发；默认 {@code false}，仅修饰符触发的时刻例外。 */
    public boolean repeatable() {
        return repeatable;
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
