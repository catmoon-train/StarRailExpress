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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    // ==================== 疯狂模式：完全按伪装职业实现 ====================
    // 魔术师本人不改动任何职业的疯狂模式配置，这里只是把「带 player 的钩子」转发给当前伪装的职业，
    // 所以伪装 Dream 买巨幕面具时走的就是 Dream 的原实现（不发球棒 + 专属音效/粒子），
    // 伪装蕾米莉亚时疯魔皮肤也是蕾米莉亚的；没有伪装 / 解析失败时回落到自身默认（球棒）。

    @Override
    public List<Item> getPsychoSupportedWeapons(Player player) {
        SRERole disguise = disguiseOf(player);
        return disguise != null ? disguise.getPsychoSupportedWeapons(player)
                : super.getPsychoSupportedWeapons(player);
    }

    @Override
    public boolean isPsychoSupportedWeapon(Player player, ItemStack stack) {
        SRERole disguise = disguiseOf(player);
        return disguise != null ? disguise.isPsychoSupportedWeapon(player, stack)
                : super.isPsychoSupportedWeapon(player, stack);
    }

    @Override
    public boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent psychoComponent) {
        SRERole disguise = disguiseOf(player);
        return disguise != null ? disguise.onPsychoGiveItem(player, psychoComponent)
                : super.onPsychoGiveItem(player, psychoComponent);
    }

    @Override
    public boolean shouldClearGrantedPsychoWeapon(Player player, Item weapon) {
        SRERole disguise = disguiseOf(player);
        return disguise != null ? disguise.shouldClearGrantedPsychoWeapon(player, weapon)
                : super.shouldClearGrantedPsychoWeapon(player, weapon);
    }

    @Override
    public void onPsychoStart(Player player, SREPlayerPsychoComponent psychoComponent) {
        SRERole disguise = disguiseOf(player);
        if (disguise != null) {
            disguise.onPsychoStart(player, psychoComponent);
        } else {
            super.onPsychoStart(player, psychoComponent);
        }
    }

    @Override
    public void onPsychoOver(Player player, SREPlayerPsychoComponent psychoComponent) {
        SRERole disguise = disguiseOf(player);
        if (disguise != null) {
            disguise.onPsychoOver(player, psychoComponent);
        } else {
            super.onPsychoOver(player, psychoComponent);
        }
    }

    /** 疯魔皮肤：蕾米莉亚等职业有专属皮肤，伪装时一并沿用。 */
    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        SRERole disguise = disguiseOf(player);
        return disguise != null ? disguise.getPsychoSkin(player, isSlim) : super.getPsychoSkin(player, isSlim);
    }

    @Nullable
    private static SRERole disguiseOf(@Nullable Player player) {
        return MagicianDisguiseHelper.resolveDisguisedRole(player);
    }
}
