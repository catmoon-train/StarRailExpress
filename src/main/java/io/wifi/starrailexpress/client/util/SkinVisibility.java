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

package io.wifi.starrailexpress.client.util;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * 「隐藏指定皮肤」的客户端统一判定入口。
 * <p>
 * 语义：本地玩家在「饰品设置」里勾选了某个皮肤<b>类别</b>（{@code knife} / {@code backpack} / …）后，
 * <b>其他玩家</b>在该类别上的皮肤一律回退默认外观；<b>本地玩家自己的皮肤始终正常显示</b>。
 * <p>
 * 该过滤是纯客户端的观感设置，不改变任何服务端数据，也不影响物品本身的功能。
 * 音乐盒 / 称号 / MVP 结算动画不属于「他人身上可见」的类别，因此
 * {@link ItemSkinManager.SkinTypes#MUSIC_BOX} 等类型永远不参与过滤（{@link #isHideableType} 会拒绝）。
 */
public final class SkinVisibility {

    private SkinVisibility() {
    }

    /**
     * 该皮肤类别是否可以被「隐藏指定皮肤」开关控制。
     * <p>
     * 只排除不影响他人渲染的类别：音乐盒（纯音效）、称号（走名片系统）、MVP 结算动画（全屏演出）。
     */
    public static boolean isHideableType(String skinType) {
        if (skinType == null || skinType.isBlank()) {
            return false;
        }
        return !ItemSkinManager.SkinTypes.MUSIC_BOX.equals(skinType)
                && !ItemSkinManager.SkinTypes.MVP_ANIMATION.equals(skinType)
                && !"name_tag".equals(skinType);
    }

    /**
     * 某玩家身上的某类皮肤当前是否应该被本机渲染出来。
     * <p>
     * 本地玩家自己恒为 {@code true}（自己的皮肤不受隐藏开关影响）；
     * 他人则看该类别有没有被勾选隐藏。
     */
    public static boolean isVisible(Player player, String skinType) {
        if (!isHideableType(skinType)) {
            return true;
        }
        if (player == null) {
            return true;
        }
        Player local = Minecraft.getInstance().player;
        if (local != null && local.getUUID().equals(player.getUUID())) {
            return true;
        }
        return !SREClientConfig.instance().isSkinTypeHidden(skinType);
    }

    /**
     * 按「隐藏指定皮肤」过滤一个已解析出来的皮肤名。
     *
     * @param player   皮肤归属玩家（他人）
     * @param skinType 皮肤类别
     * @param skinName 已解析的皮肤名；{@code null} / {@code default} 原样返回
     * @return 被隐藏时返回 {@code "default"}
     */
    public static String filter(Player player, String skinType, String skinName) {
        if (skinName == null || skinName.isBlank() || "default".equals(skinName)) {
            return skinName;
        }
        return isVisible(player, skinType) ? skinName : "default";
    }
}
