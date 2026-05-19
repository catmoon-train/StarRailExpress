package org.agmas.noellesroles.mixin.client.roles.bodymaker;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.*;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.mortician.MorticianPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.util.DeathReasonHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 葬仪物品栏屏幕Mixin
 * 在物品栏界面显示三阶段选择流程：
 * 1. 选择目标玩家
 * 2. 选择死亡原因
 * 3. 输入要伪造的角色（可选）
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class BodymakerScreenMixin extends LimitedHandledScreen<InventoryMenu> implements MorticianScreenCallback {

    @Shadow @Final
    public LocalPlayer player;
    
    @Unique
    private int selectedLevel = 0; // 0=选择玩家, 1=选择死亡原因, 2=输入角色名
    
    @Unique
    private UUID selectedPlayerUuid = null;
    
    @Unique
    private String selectedDeathReason = null;

    public BodymakerScreenMixin(@NotNull InventoryMenu handler, @NotNull Inventory inventory, @NotNull Component title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    void renderBodymakerWidgets(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.player.clientLevel);
        if (gameWorld == null) return;
        
        // 检查是否为葬仪角色
        if (!gameWorld.isRole(client.player, ModRoles.MORTICIAN_BODYMAKER)) {
            return;
        }
        
        MorticianPlayerComponent component = ModComponents.MORTICIAN_BODYMAKER.get(client.player);
        if (component == null) return;
        
        // 只有在冷却为0时才显示
        if (component.cooldown > 0) {
            return;
        }
        
        int apart = 36;
        int shouldBeY = (this.height - 32) / 2;
        int y = shouldBeY + 80;
        
        if (selectedLevel == 0) {
            // 阶段1：选择目标玩家
            List<PlayerInfo> playerInfoList = new ArrayList<>();
            if (client.getConnection() != null) {
                for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                    // 排除自己
                    if (!info.getProfile().getId().equals(player.getUUID())) {
                        playerInfoList.add(info);
                    }
                }
            }

            int x = this.width / 2 - (playerInfoList.size() * apart) / 2 + 9;
            for (int i = 0; i < playerInfoList.size(); ++i) {
                PlayerInfo info = playerInfoList.get(i);
                BodymakerPlayerWidget widget = new BodymakerPlayerWidget(
                        (LimitedInventoryScreen) (Object) this,
                        x + apart * i, y, info.getProfile().getId(), info, this
                );
                addRenderableWidget(widget);
            }
        }
        else if (selectedLevel == 1) {
            // 阶段2：选择死亡原因
            ItemStack[] deathReasons = DeathReasonHelper.getAvailableDeathReasons();
            
            int x = this.width / 2 - (deathReasons.length * apart) / 2 + 9;
            for (int i = 0; i < deathReasons.length; ++i) {
                String deathReasonId = DeathReasonHelper.getDeathReasonId(deathReasons[i]);
                BodymakerDeathReasonWidget widget = new BodymakerDeathReasonWidget(
                        (LimitedInventoryScreen) (Object) this,
                        x + apart * i, y, deathReasons[i], deathReasonId, selectedPlayerUuid, this
                );
                addRenderableWidget(widget);
            }
        }
        else if (selectedLevel == 2) {
            // 阶段3：输入角色名
            int x = this.width / 2 - 100;
            BodymakerRoleWidget widget = new BodymakerRoleWidget(
                    (LimitedInventoryScreen) (Object) this,
                    this.font, x, y, selectedPlayerUuid, selectedDeathReason
            );
            addRenderableWidget(widget);
            widget.setFocused(true);
        }
    }

    @Override
    @Unique
    public void setSelectedPlayer(@NotNull UUID uuid) {
        this.selectedPlayerUuid = uuid;
        this.selectedLevel = 1;
        clearWidgets();
        init();
    }

    @Unique
    @Override
    public void setSelectedDeathReason(@NotNull String deathReason) {
        this.selectedDeathReason = deathReason;
        this.selectedLevel = 2;
        clearWidgets();
        init();
    }
}
