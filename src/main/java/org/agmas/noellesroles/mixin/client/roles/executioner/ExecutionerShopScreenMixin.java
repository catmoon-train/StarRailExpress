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

package org.agmas.noellesroles.mixin.client.roles.executioner;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

import org.agmas.noellesroles.client.widget.ExecutionerPlayerWidget;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LimitedInventoryScreen.class)
public abstract class ExecutionerShopScreenMixin extends LimitedHandledScreen<InventoryMenu> {
    @Shadow
    @Final
    public LocalPlayer player;

    public ExecutionerShopScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    void addExecutionerTargetSelection(CallbackInfo ci) {
        // 检查是否启用了手动选择目标功能
        if (!NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return; // 如果未启用，则不显示选择界面
        }

        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(player.level());

        // 检查是否是Executioner角色
        if (gameWorldComponent.isRole(player, ModRoles.EXECUTIONER)) {
            ExecutionerPlayerComponent executionerComponent = ExecutionerPlayerComponent.KEY.get(player);

            // 只有在未选择目标时才显示选择界面
            if (!executionerComponent.targetSelected) {
                List<AbstractClientPlayer> entries = Minecraft.getInstance().level.players();

                // 筛选出平民阵营且存活的玩家
                entries.removeIf((e) -> {
                    if (e.getUUID().equals(player.getUUID()))
                        return true;
                    if (!GameUtils.isPlayerAliveAndSurvival(e))
                        return true;
                    return ExecutionerPlayerComponent.judgeRole(player.level(), gameWorldComponent.getRole(e));
                });

                int apart = 36;
                int x = ((LimitedInventoryScreen) (Object) this).width / 2 - (entries.size()) * apart / 2 + 9;
                int shouldBeY = (((LimitedInventoryScreen) (Object) this).height - 32) / 2;
                int y = shouldBeY + 80;

                for (int i = 0; i < entries.size(); ++i) {
                    ExecutionerPlayerWidget child = new ExecutionerPlayerWidget(x + apart * i, y, entries.get(i), i);
                    addRenderableWidget(child);
                }
            }
        }
    }
}