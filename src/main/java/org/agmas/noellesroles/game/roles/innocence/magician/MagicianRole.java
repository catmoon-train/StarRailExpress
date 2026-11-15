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

package org.agmas.noellesroles.game.roles.innocence.magician;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 魔术师（平民阵营，隐藏身份为杀手团队）。
 *
 * <p>与普通 {@link NormalRole} 的唯一差别是商店：魔术师<b>没有自己的固定商店</b>，
 * 商店完全跟随开局伪装的杀手职业，并统一加价 20%，见 {@link MagicianShop}。</p>
 *
 * <p>其它被动（伪装身份去重、伪装职业的初始物品 / 初始药水效果、非左轮手枪不致死）
 * 分别落在 {@link MagicianDisguiseHelper}、
 * {@code MagicianRoleData#startDisguiseRandomRole()} 与
 * {@code GameUtilityRoleHooks#magicianKillRestriction}。</p>
 */
public class MagicianRole extends NormalRole {

    public MagicianRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 被动——伪装大师：拥有伪装职业的商店（价格 +20%）。 */
    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        return MagicianShop.buildEntries(player);
    }
}
