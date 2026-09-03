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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 无我（Anatman）职业数据。
 * <p>
 * 与无妄（Asatya）绑定生成。技能体系：
 * <ul>
 * <li>为他：将无妄传送回他自己的房间（冷却80秒）</li>
 * <li>舍己：使自己死亡（死因：被列车碾压）并进入死亡惩罚（冷却150秒，开局自带180秒冷却）</li>
 * <li>还魂：消耗100金币使无妄复活（冷却120秒）</li>
 * </ul>
 * 当无我被其它玩家击杀时，拒绝死亡并进入30秒疯狂模式（附带1层护盾）。
 * 当无我与无妄在乘客/时间/杀手胜利结算时均存活，则取得联合独立胜利。
 */
public class AnatmanRoleData extends SimpleRoleData {

    /** 还魂消耗的金币数量。 */
    public static final int SOUL_RETURN_COST = 100;
    /** 舍己开局自带的冷却（tick）。 */
    public static final int SELF_SACRIFICE_INITIAL_COOLDOWN_TICKS = 180 * 20;

    public static final ResourceLocation FOR_HIM_SKILL_ID = SRE.id("anatman_for_him");
    public static final ResourceLocation SELF_SACRIFICE_SKILL_ID = SRE.id("anatman_self_sacrifice");
    public static final ResourceLocation SOUL_RETURN_SKILL_ID = SRE.id("anatman_soul_return");

    public AnatmanRoleData(RoleDataContext context) {
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

    /**
     * 查找职业为 partnerRole 的搭档玩家（排除自己）。
     */
    public static ServerPlayer findRolePartner(ServerPlayer self, SRERole partnerRole) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(self.level());
        if (gameWorld == null) {
            return null;
        }
        for (ServerPlayer p : self.serverLevel().players()) {
            if (p == self) {
                continue;
            }
            if (gameWorld.isRole(p, partnerRole)) {
                return p;
            }
        }
        return null;
    }

    /** 查找搭档（无妄）。 */
    public static ServerPlayer findPartner(ServerPlayer self) {
        return findRolePartner(self, ModRoles.ASATYA);
    }

    /**
     * 为他：将无妄传送回他自己的房间。
     *
     * @return true = 技能释放成功（进入冷却）
     */
    public static boolean useForHim(ServerPlayer player) {
        ServerPlayer partner = findPartner(player);
        if (partner == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.anatman.no_partner").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(partner)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.anatman.partner_dead").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        GameUtils.teleportBackToRoom(partner);
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
     * 还魂：消耗100金币使无妄复活。
     *
     * @return true = 技能释放成功（进入冷却）
     */
    public static boolean useSoulReturn(ServerPlayer player) {
        ServerPlayer partner = findPartner(player);
        if (partner == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.anatman.no_partner").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (GameUtils.isPlayerAliveAndSurvival(partner)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.anatman.partner_alive").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < SOUL_RETURN_COST) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.anatman.no_coins", SOUL_RETURN_COST)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-SOUL_RETURN_COST);
        GameUtils.revivePlayerToItsRoom(partner);
        return true;
    }

    /**
     * 无妄死亡时，所有存活的无我进入30秒疯狂模式（附带1层护盾）。
     *
     * @param asatya 死亡的无妄玩家
     */
    public static void onAsatyaDeath(ServerPlayer asatya) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(asatya.level());
        if (gameWorld == null) {
            return;
        }
        for (ServerPlayer p : asatya.serverLevel().players()) {
            if (!gameWorld.isRole(p, ModRoles.ANATMAN)) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            // 30秒疯狂模式，带1层护盾
            SREPlayerShopComponent.usePsychoMode_time(p, 30 * 20, 1);
            p.displayClientMessage(
                    Component.translatable("message.noellesroles.anatman.crazy").withStyle(ChatFormatting.RED),
                    false);
        }
    }
}
