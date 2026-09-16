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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.innocence.DivinerRoleData;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 晶球：占卜家专用道具。
 *
 * <p>
 * 右键一具尸体开始 3 秒占卜，完成后传送到背包界面选中的玩家身边；
 * 传送后 10 秒内再次右键晶球可传送回原位置。未在背包中选中目标时右键会用 actionbar 提示。
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
            if (gw.isRole(sp, ModRoles.DIVINER) && GameUtils.isPlayerAliveAndSurvival(sp)) {
                DivinerRoleData comp = RoleData.getNullable(DivinerRoleData.class, sp);
                if (comp != null) {
                    if (comp.isAwaitingReturn()) {
                        // 回传窗口内：传送回原位置
                        comp.returnToOrigin(sp);
                    } else if (comp.getTarget() == null) {
                        // 未选中目标玩家：actionbar 提示
                        sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.no_target")
                                .withStyle(ChatFormatting.GRAY), true);
                    } else {
                        // 射线检测尸体
                        NoellesRolesConfig cfg = NoellesRolesConfig.HANDLER.instance();
                        HitResult hr = ProjectileUtil.getHitResultOnViewVector(sp,
                                e -> e instanceof PlayerBodyEntity,
                                cfg.divinerRange);
                        if (hr instanceof EntityHitResult ehr
                                && ehr.getEntity() instanceof PlayerBodyEntity body) {
                            if (comp.startDivination(sp, body)) {
                                // 回放记录：占卜家开始占卜
                                io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordCustomEvent(
                                        Component.translatable("replay.event.diviner.divination",
                                                io.wifi.starrailexpress.api.replay.GameReplayUtils
                                                        .getReplayPlayerDisplayText(sp, true)));
                            }
                        } else {
                            sp.displayClientMessage(Component.translatable("message.noellesroles.diviner.no_corpse")
                                    .withStyle(ChatFormatting.GRAY), true);
                        }
                    }
                }
            }
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.crystal_ball.tooltip"));
    }
}
