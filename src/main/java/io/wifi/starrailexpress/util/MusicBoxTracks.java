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

package io.wifi.starrailexpress.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * 音乐盒曲目登记表。
 * <p>
 * 皮肤类型 {@link ItemSkinManager.SkinTypes#MUSIC_BOX} 的每个皮肤对应一首音乐盒曲目，
 * 曲目由三部分组成（后两者可选）：
 * <ul>
 * <li>{@code ambient} 对局音效：装配后对局进行中低音量循环播放，必填；</li>
 * <li>{@code win} 结算成功音效：对局胜利时替换默认钢琴音，可缺省；</li>
 * <li>{@code lose} 结算失败音效：对局失败时替换默认钢琴音，可缺省。</li>
 * </ul>
 * 缺省的结算音效（以及没装配音乐盒的情况）由 {@code RoundTextRenderer} 回落到默认结算音。
 *
 * <p>皮肤由内容模组（如 sre-skin）通过 {@link ItemSkinManager#registerACustomSkin} 注册，
 * 曲目在同一个初始化流程里调用 {@link #register} 登记。音频文件（{@code sounds/*.ogg}）与
 * {@code sounds.json} 里的音效条目同样由内容模组提供，本类只记住「皮肤 ID → 音效 ID」的对应关系。</p>
 */
public final class MusicBoxTracks {
    /**
     * 一首音乐盒曲目。
     *
     * @param ambient 对局音效（循环播放），必填
     * @param win     结算成功音效，可为 {@code null}
     * @param lose    结算失败音效，可为 {@code null}
     */
    public record Track(ResourceLocation ambient, ResourceLocation win, ResourceLocation lose) {
        public boolean hasWin() {
            return win != null;
        }

        public boolean hasLose() {
            return lose != null;
        }
    }

    /** 皮肤 ID → 曲目（保持登记顺序，方便展示） */
    private static final Map<String, Track> TRACKS = new LinkedHashMap<>();

    private MusicBoxTracks() {
    }

    /** 只登记对局音效（没有结算音效的音乐盒）。 */
    public static void register(String skinId, ResourceLocation ambient) {
        register(skinId, ambient, null, null);
    }

    /**
     * 登记一首完整曲目。
     *
     * @param skinId  音乐盒皮肤 ID（不区分大小写）
     * @param ambient 对局音效 ID，必填，必须与内容模组 {@code sounds.json} 中的键一致
     * @param win     结算成功音效 ID，可为 {@code null}
     * @param lose    结算失败音效 ID，可为 {@code null}
     */
    public static void register(String skinId, ResourceLocation ambient, ResourceLocation win, ResourceLocation lose) {
        if (skinId == null || skinId.isBlank() || ambient == null) {
            return;
        }
        TRACKS.put(normalize(skinId), new Track(ambient, win, lose));
    }

    /** 取某个音乐盒皮肤登记的曲目；没有登记过返回 {@code null}。 */
    public static Track get(String skinId) {
        if (skinId == null) {
            return null;
        }
        return TRACKS.get(normalize(skinId));
    }

    /** 对局音效 ID；没有登记过返回 {@code null}。 */
    public static ResourceLocation getSoundId(String skinId) {
        Track track = get(skinId);
        return track == null ? null : track.ambient();
    }

    /** 结算成功音效 ID；没有登记过或该音乐盒不带结算成功音效时返回 {@code null}。 */
    public static ResourceLocation getWinSound(String skinId) {
        Track track = get(skinId);
        return track == null ? null : track.win();
    }

    /** 结算失败音效 ID；没有登记过或该音乐盒不带结算失败音效时返回 {@code null}。 */
    public static ResourceLocation getLoseSound(String skinId) {
        Track track = get(skinId);
        return track == null ? null : track.lose();
    }

    /** 该音乐盒皮肤是否登记过对局音效。 */
    public static boolean hasTrack(String skinId) {
        return getSoundId(skinId) != null;
    }

    /** 所有已登记曲目的音乐盒皮肤 ID（只读）。 */
    public static Set<String> skinIds() {
        return Collections.unmodifiableSet(TRACKS.keySet());
    }

    private static String normalize(String skinId) {
        return skinId.toLowerCase(Locale.ROOT);
    }
}
