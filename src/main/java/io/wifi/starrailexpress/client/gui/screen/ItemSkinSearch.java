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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.util.PinYinUtils;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 皮肤页搜索匹配器：让「中文名 / 全拼 / 拼音首字母 / 皮肤 ID」四种输入都能命中，
 * 并且把命中质量量化成分数，供列表按匹配度排序（而不是只做子串过滤）。
 * <p>
 * 现有实现（改造前）只看 {@code displayName().getString()} 的子串，
 * 于是出现两个用户可感知的问题：
 * <ul>
 *   <li>搜「星澜」时，如果当前语言文件缺失或键名回退到英文/ID，中文名根本不在候选串里；</li>
 *   <li>搜「xl」这种拼音首字母完全匹配不到，因为原串是中文。</li>
 * </ul>
 * 本类把「名字 + 拼音 + 首字母 + ID」拼成一组候选串统一打分，
 * 命中越靠前、越完整，分数越高，保证「搜什么先出什么」。
 * <p>
 * 打分区间（越大越优先）：
 * <pre>
 *   1000  候选串与输入完全相同
 *    800  候选串以输入开头
 *    600  候选串中某个「词首」以输入开头（如 "star dust" 里的 "dust"）
 *    400  候选串包含输入（越靠前越高，最高 +99）
 *    200  仅 ID / 原始名兜底命中
 *      0  不命中
 * </pre>
 */
public final class ItemSkinSearch {

    /** 完全相等 */
    public static final int SCORE_EXACT = 1000;
    /** 以输入开头 */
    public static final int SCORE_PREFIX = 800;
    /** 词首命中（人名/多段名里的一段开头） */
    public static final int SCORE_WORD_PREFIX = 600;
    /** 纯包含 */
    public static final int SCORE_CONTAINS = 400;
    /** 仅 ID 兜底命中 */
    public static final int SCORE_ID_ONLY = 200;
    /** 不命中 */
    public static final int SCORE_NONE = 0;

    private ItemSkinSearch() {
    }

    /** 输入归一化：小写、去首尾空白。 */
    public static String normalize(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 给一个皮肤算匹配分。
     *
     * @param query       用户输入（未归一化也可）
     * @param displayName 已解析的显示名（中文时是中文，英文时是英文；可为 null）
     * @param skinName    皮肤 ID（{@code xinglan_revolver} 这种；可为 null）
     * @return 匹配分，{@link #SCORE_NONE} 表示不命中
     */
    public static int score(String query, Component displayName, String skinName) {
        String resolved = displayName == null ? "" : displayName.getString();
        return score(query, resolved, skinName);
    }

    /**
     * 给一个皮肤算匹配分（显示名已转成字符串）。
     */
    public static int score(String query, String displayName, String skinName) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return SCORE_EXACT;
        }

        int best = SCORE_NONE;

        // 1. 显示名本身（含中文）——最强信号
        best = Math.max(best, scoreAgainst(q, displayName, SCORE_EXACT));

        // 2. 显示名的全拼 + 首字母（"星澜左轮" → "xinglanzuolun" / "xlzl"）
        String shown = displayName == null ? "" : displayName;
        if (!shown.isEmpty()) {
            best = Math.max(best, scoreAgainst(q, PinYinUtils.toSearchablePinyin(shown), SCORE_EXACT));
            best = Math.max(best, scoreAgainst(q, PinYinUtils.toPinyinInitials(shown), SCORE_PREFIX - 50));
        }

        // 3. 皮肤 ID（"xinglan_revolver" → 去掉下划线后再比一次）
        if (skinName != null && !skinName.isEmpty()) {
            best = Math.max(best, scoreAgainst(q, skinName, SCORE_ID_ONLY));
            String flatten = skinName.replace('_', ' ').replace('-', ' ');
            best = Math.max(best, scoreAgainst(q, flatten, SCORE_WORD_PREFIX));
            best = Math.max(best, scoreAgainst(q, flatten.replace(" ", ""), SCORE_PREFIX - 100));
        }

        return best;
    }

    /** 用一条候选串给输入打分（exactScore 指定「完全相等」时的分值）。 */
    private static int scoreAgainst(String q, String candidate, int exactScore) {
        if (candidate == null || candidate.isEmpty()) {
            return SCORE_NONE;
        }
        String c = candidate.toLowerCase(Locale.ROOT);
        if (c.equals(q)) {
            return exactScore;
        }
        if (c.startsWith(q)) {
            return Math.max(SCORE_NONE, exactScore - 200);
        }
        if (atWordStart(c, q)) {
            return Math.max(SCORE_NONE, exactScore - 400);
        }
        int idx = c.indexOf(q);
        if (idx >= 0) {
            // 越靠前越优先；最高 +99，最低 +0
            int bonus = Math.max(0, 99 - idx);
            int base = Math.max(SCORE_ID_ONLY, exactScore - 600);
            return base + bonus;
        }
        return SCORE_NONE;
    }

    /** 候选串里是否有某个词以输入开头（空格 / 下划线 / 连字符 / 驼峰分界）。 */
    private static boolean atWordStart(String candidate, String q) {
        int from = 0;
        while (true) {
            int idx = candidate.indexOf(q, from);
            if (idx < 0) {
                return false;
            }
            if (idx == 0) {
                return true;
            }
            char prev = candidate.charAt(idx - 1);
            if (prev == ' ' || prev == '_' || prev == '-' || prev == '.' || prev == ':') {
                return true;
            }
            from = idx + 1;
        }
    }

    /**
     * 是否命中（分数 &gt; 0 或输入为空）。
     */
    public static boolean matches(String query, Component displayName, String skinName) {
        return normalize(query).isEmpty() || score(query, displayName, skinName) > SCORE_NONE;
    }
}
