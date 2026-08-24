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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.innocence.DivinerRoleData;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 晶球：占卜家专用道具。右键对准一具尸体可开始 10 秒占卜施法，期间需静止不动。
 * 完成后随机揭示一项凶手线索；50% 概率破碎；60 秒冷却。
 */
public class CrystalBallItem extends Item {

    public CrystalBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(level);
            if (!gw.isRole(sp, ModRoles.DIVINER) || !GameUtils.isPlayerAliveAndSurvival(sp)) {
                return InteractionResultHolder.fail(stack);
            }

            // 射线检测目标（尸体或玩家）
            NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
            HitResult hr = ProjectileUtil.getHitResultOnViewVector(sp,
                    e -> e instanceof PlayerBodyEntity || (e instanceof Player p && p != sp),
                    cfg.divinerRange);
            if (hr instanceof EntityHitResult ehr) {
                Entity target = ehr.getEntity();
                DivinerRoleData comp = RoleData.getNullable(DivinerRoleData.class, sp);
                if (comp != null) {
                    if (comp.startChannel(sp, target)) {
                        // 回放记录：占卜家成功占卜了一具尸体
                        io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                                net.minecraft.network.chat.Component.translatable("replay.event.diviner.divination",
                                        io.wifi.starrailexpress.api.replay.GameReplayUtils
                                                .getReplayPlayerDisplayText(sp, true)));
                    }
                }
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
