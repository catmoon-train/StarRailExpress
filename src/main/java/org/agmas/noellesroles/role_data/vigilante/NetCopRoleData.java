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

package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 网警（Net Cop）职业数据（警长阵营特殊巡警）。
 * <p>
 * 核心机制：
 * <ul>
 * <li>完成小游戏任务额外恢复 30% 理智（理智上限为 1，即 {@code addMood(0.3f)}）；</li>
 * <li>小游戏任务刷新不受轮换模式「2~3 个普通任务」限制（独立计时派发）；</li>
 * <li>商店使用小游戏代币按顺序购买 Dream 铁斧/钻石剑/重锤（见 {@code RoleShopHandler}）；</li>
 * <li>以 Dream 铁斧/钻石剑/重锤 主手击杀玩家后，对应武器进入 20 秒原版物品冷却
 * （原版冷却按 {@link Item} 维度，三武器天然独立）；</li>
 * <li>死亡时身上的 Dream 武器掉落为左轮手枪（见 {@code NRDeathEvents#dropRoleSpecificItems}）。</li>
 * </ul>
 */
public class NetCopRoleData extends SimpleRoleData {

    /** Dream 武器击杀后的物品冷却时长（tick）：20 秒。 */
    public static final int KILL_COOLDOWN_TICKS = 20 * 20;

    static {
        OnPlayerDeathWithKiller.EVENT.register(NetCopRoleData::onKillWithDreamWeapon);
    }

    public NetCopRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 网警以 Dream 铁斧/钻石剑/重锤 主手成功击杀玩家后，使对应武器进入 20 秒原版物品冷却。
     */
    private static void onKillWithDreamWeapon(Player victim, Player killer,
            net.minecraft.resources.ResourceLocation deathReason) {
        if (!(killer instanceof net.minecraft.server.level.ServerPlayer sk) || victim == killer) {
            return;
        }
        if (!isNetCop(sk)) {
            return;
        }
        ItemStack mainHand = sk.getMainHandItem();
        Item weapon = mainHand.getItem();
        if (isNetCopDreamWeapon(weapon)) {
            sk.getCooldowns().addCooldown(weapon, KILL_COOLDOWN_TICKS);
        }
    }

    /** 判断物品是否为网警商店出售的 Dream 三武器之一。 */
    public static boolean isNetCopDreamWeapon(Item item) {
        return item == ModItems.DREAM_AXE
                || item == ModItems.DREAM_DIAMOND_SWORD
                || item == ModItems.DREAM_MACE;
    }

    /** 玩家当前职业是否为网警。 */
    public static boolean isNetCop(Player player) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld != null && gameWorld.isRole(player, ModRoles.NET_COP);
    }

    /**
     * 网警完成小游戏任务后额外恢复 30% 理智（理智上限 1.0）。
     *
     * @return 是否实际恢复（仅网警生效）
     */
    public static boolean restoreSanityAfterMinigame(net.minecraft.server.level.ServerPlayer sp) {
        if (!isNetCop(sp)) {
            return false;
        }
        SREPlayerMoodComponent.KEY.get(sp).addMood(0.3f);
        return true;
    }

    // 无自有需同步状态，RoleData 接口要求显式实现空方法。
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
    }
}
