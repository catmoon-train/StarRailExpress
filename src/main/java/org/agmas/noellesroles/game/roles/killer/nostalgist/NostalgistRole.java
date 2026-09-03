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

package org.agmas.noellesroles.game.roles.killer.nostalgist;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.NostalgistRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 怀旧者（里世界·杀手阵营）。
 *
 * <p>当场上存在一名以上杀手时，怀旧者处于「里世界」：视角灰白，对所有阵营隐身、
 * 奔跑无声无粒子、无法被看见/听见/攻击；身处里世界时无法击杀任何人，且无法说话（文字/语音）、
 * 无法使用物品、手持物品不显示，只能潜行与侦察。主动按技能键退出里世界需经过约 1.5 秒前摇
 * （伴随音效与粒子），前摇结束才现身。当场上仅剩怀旧者一名杀手时，里世界自动崩塌，怀旧者现身为
 * 普通杀手（见 {@link org.agmas.noellesroles.role_data.killer.NostalgistRoleData}）。
 *
 * <p>商店为通用杀手商店（{@link ShopContent#defaultKnifeEntries}）。
 */
public class NostalgistRole extends ExtraEffectRole {

    public NostalgistRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {

        List<ShopEntry> defaultKnifeEntries = ShopContent.getDefaultKnifeEntries();
        defaultKnifeEntries.add(new KillerKnifeShopEntry(SREConfig.instance().knifePrice){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });
        defaultKnifeEntries.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                SREConfig.instance().revolverPrice, ShopEntry.Type.WEAPON){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });
        defaultKnifeEntries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(),
                SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));

        defaultKnifeEntries.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(), SREConfig.instance().shortShotgunPrice, ShopEntry.Type.WEAPON){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });
        defaultKnifeEntries.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
                SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)){
                    return false;
                }
                return super.canBuy(player);
            }
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)){
                    return false;
                }
                player.level().players().forEach(
                        player1 -> {
                            if (!player1.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)){
                                player1.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                                        SREConfig.instance().psychoGlobalCooldown);
                            }
                        }
                );
                return SREPlayerShopComponent.usePsychoMode(player);
            }
        });

        defaultKnifeEntries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
                SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });
        defaultKnifeEntries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });
        defaultKnifeEntries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });
        defaultKnifeEntries.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(),
                SREConfig.instance().bodyBagPrice, ShopEntry.Type.TOOL){
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

        });


        defaultKnifeEntries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean canDisplay(@NotNull Player player) {
                return !RoleData.getOptional(NostalgistRoleData.class, player).map(d -> d.inBackWorld).orElse(false);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        defaultKnifeEntries.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice,
                ShopEntry.Type.TOOL));
        return defaultKnifeEntries;
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        // 处于里世界时无法捡起任何物品
        var comp = RoleData.getNullable(NostalgistRoleData.class, player);
        if (comp != null && comp.isActiveBackWorld()) {
            return TrueFalseResult.FALSE;
        }
        return super.onPickUpItem(player, item);
    }
}
