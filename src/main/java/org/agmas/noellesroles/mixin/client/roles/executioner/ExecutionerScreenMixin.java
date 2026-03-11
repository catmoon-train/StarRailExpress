package org.agmas.noellesroles.mixin.client.roles.executioner;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.game.GameFunctions;
import org.agmas.noellesroles.client.widget.ExecutionerPlayerWidget;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Executioner角色选择目标的UI界面Mixin
 * 当打开背包界面时，如果是Executioner且未选择目标，显示可选择的平民玩家
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class ExecutionerScreenMixin extends LimitedHandledScreen<InventoryMenu> {
    @Shadow @Final public LocalPlayer player;

    public ExecutionerScreenMixin(InventoryMenu handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    void addExecutionerTargetSelection(CallbackInfo ci) {
        // 检查是否启用了手动选择目标功能
        if (!NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
            return; // 如果未启用，则不显示选择界面
        }
        
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.level());
        
        // 检查是否是Executioner角色
        if (gameWorldComponent.isRole(player, ModRoles.EXECUTIONER)) {
            ExecutionerPlayerComponent executionerComponent = ExecutionerPlayerComponent.KEY.get(player);
            
            // 只有在未选择目标时才显示选择界面
            if (!executionerComponent.targetSelected) {
                List<AbstractClientPlayer> entries = Minecraft.getInstance().level.players();
                
                // 筛选出平民阵营且存活的玩家
                entries.removeIf((e) -> {
                    if (e.getUUID().equals(player.getUUID())) return true;
                    if (!GameFunctions.isPlayerAliveAndSurvival(e)) return true;
                    return !gameWorldComponent.getRole(e).isInnocent();
                });
                
                int apart = 36;
                int x = ((LimitedInventoryScreen)(Object)this).width / 2 - (entries.size()) * apart / 2 + 9;
                int shouldBeY = (((ExecutionerScreenMixin)(Object)this).height - 32) / 2;
                int y = shouldBeY + 80;

                for (int i = 0; i < entries.size(); ++i) {
                    ExecutionerPlayerWidget child = new ExecutionerPlayerWidget(x + apart * i, y, entries.get(i), i);
                    addRenderableWidget(child);
                }
            }
        }
    }
}