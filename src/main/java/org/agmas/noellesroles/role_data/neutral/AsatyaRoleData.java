/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
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

package org.agmas.noellesroles.role_data.neutral;

import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * 无妄（Asatya）职业数据。
 * <p>
 * 与无我（Anatman）绑定生成。技能体系与无我同构：
 * <ul>
 * <li>为他：将无我传送至自己身边（冷却80秒）</li>
 * <li>舍己：使自己死亡（死因：被列车碾压）并进入死亡惩罚（冷却150秒，开局自带180秒冷却）</li>
 * <li>还魂：消耗100金币使无我复活（冷却120秒）</li>
 * </ul>
 * 当无我死亡时，自身叠加一层护盾（最多5层），护盾可抵挡致命伤害（每层消耗1层）。
 * 当无我与无妄在乘客/时间/杀手胜利结算时均存活，则取得联合独立胜利。
 */
public class AsatyaRoleData extends SimpleRoleData {

    /** 还魂消耗的金币数量。 */
    public static final int SOUL_RETURN_COST = 100;
    /** 舍己开局自带的冷却（tick）。 */
    public static final int SELF_SACRIFICE_INITIAL_COOLDOWN_TICKS = 180 * 20;
    /** 护盾层数上限。 */
    public static final int MAX_SHIELD_LAYERS = 5;

    public static final ResourceLocation FOR_HIM_SKILL_ID = SRE.id("asatya_for_him");
    public static final ResourceLocation SELF_SACRIFICE_SKILL_ID = SRE.id("asatya_self_sacrifice");
    public static final ResourceLocation SOUL_RETURN_SKILL_ID = SRE.id("asatya_soul_return");

    public AsatyaRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        if (player.level().isClientSide()) {
            return;
        }
        // 舍己开局自带180秒冷却
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (ability != null) {
            ability.setSkillCooldown(SELF_SACRIFICE_SKILL_ID, SELF_SACRIFICE_INITIAL_COOLDOWN_TICKS);
        }
    }

    /** 查找搭档（无我）。 */
    public static ServerPlayer findPartner(ServerPlayer self) {
        return AnatmanRoleData.findRolePartner(self, ModRoles.ANATMAN);
    }

    /**
     * 为他：将无我传送至自己身边。
     *
     * @return true = 技能释放成功（进入冷却）
     */
    public static boolean useForHim(ServerPlayer player) {
        ServerPlayer partner = findPartner(player);
        if (partner == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.asatya.no_partner").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(partner)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.asatya.partner_dead").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        partner.teleportTo(player.getX(), player.getY(), player.getZ());
        return true;
    }

    /**
     * 舍己：使自己死亡（死因：被列车碾压）并进入死亡惩罚。
     *
     * @return true = 技能释放成功（进入冷却）
     */
    public static boolean useSelfSacrifice(ServerPlayer player) {
        GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
        return true;
    }

    /**
     * 还魂：消耗100金币使无我复活。
     *
     * @return true = 技能释放成功（进入冷却）
     */
    public static boolean useSoulReturn(ServerPlayer player) {
        ServerPlayer partner = findPartner(player);
        if (partner == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.asatya.no_partner").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (GameUtils.isPlayerAliveAndSurvival(partner)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.asatya.partner_alive").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < SOUL_RETURN_COST) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.asatya.no_coins", SOUL_RETURN_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-SOUL_RETURN_COST);
        GameUtils.revivePlayerToItsRoom(partner);
        return true;
    }

    /**
     * 无我死亡时，所有存活的无妄叠加一层护盾（最多 {@link #MAX_SHIELD_LAYERS} 层）。
     *
     * @param anatman 死亡的无我玩家
     */
    public static void onAnatmanDeath(ServerPlayer anatman) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(anatman.level());
        if (gameWorld == null) {
            return;
        }
        for (ServerPlayer p : anatman.serverLevel().players()) {
            if (!gameWorld.isRole(p, ModRoles.ASATYA)) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(p);
            if (armor.getArmor() >= MAX_SHIELD_LAYERS) {
                continue;
            }
            armor.addArmor(1);
            p.displayClientMessage(
                    Component.translatable("message.noellesroles.asatya.shield_gained", armor.getArmor())
                            .withStyle(ChatFormatting.AQUA),
                    false);
        }
    }

    // 无自有的需同步状态（护盾由 SREArmorPlayerComponent 自带同步），
    // RoleData 接口要求显式实现空方法。
    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
    }
}
