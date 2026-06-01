package org.agmas.noellesroles.mixin.client.roles.bodymaker;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedHandledScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.PlayerPaginationHelper;
import org.agmas.noellesroles.client.RoleScreenHelper;
import org.agmas.noellesroles.client.widget.*;
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
 * 在物品栏界面显示两阶段选择流程：
 * 1. 选择目标玩家（支持分页翻页）
 * 2. 选择死亡原因
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class BodymakerScreenMixin extends LimitedHandledScreen<InventoryMenu>
        implements MorticianScreenCallback, PlayerPaginationHelper.ScreenWithChildren {

    @Unique
    private static final PlayerPaginationHelper.PaginationTextProvider TEXT_PROVIDER = new PlayerPaginationHelper.PaginationTextProvider() {
        @Override
        public String getPageTranslationKey() {
            return "hud.pagination.page";
        }

        @Override
        public String getPrevTranslationKey() {
            return "hud.pagination.prev";
        }

        @Override
        public String getNextTranslationKey() {
            return "hud.pagination.next";
        }
    };

    @Shadow @Final
    public LocalPlayer player;
    
    @Unique
    private int selectedLevel = 0; // 0=选择玩家, 1=选择死亡原因

    @Unique
    private UUID selectedPlayerUuid = null;

    @Unique
    private RoleScreenHelper<PlayerInfo> roleScreenHelper;

    public BodymakerScreenMixin(@NotNull InventoryMenu handler, @NotNull Inventory inventory, @NotNull Component title) {
        super(handler, inventory, title);
    }

    @Unique
    private RoleScreenHelper<PlayerInfo> getRoleScreenHelper() {
        if (roleScreenHelper == null) {
            roleScreenHelper = new RoleScreenHelper<>(
                    player,
                    ModRoles.MORTICIAN_BODYMAKER,
                    this::createBodymakerWidget,
                    TEXT_PROVIDER,
                    this::drawBodymakerSelectionHint,
                    this::getEligiblePlayers
            );
        }
        return roleScreenHelper;
    }

    @Unique
    private Button createBodymakerWidget(int x, int y, PlayerInfo playerInfo, int index) {
        BodymakerPlayerWidget widget = new BodymakerPlayerWidget(
                (LimitedInventoryScreen) (Object) this,
                x, y, playerInfo.getProfile().getId(), playerInfo, this
        );
        addDrawableChild(widget);
        return widget;
    }

    @Unique
    private void drawBodymakerSelectionHint(GuiGraphics context, java.awt.Point point) {
        // 预留：可在玩家头像上方绘制提示文字
    }

    @Unique
    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }
        List<PlayerInfo> list = new ArrayList<>();
        if (client.getConnection() != null) {
            for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
                if (!info.getProfile().getId().equals(player.getUUID())) {
                    list.add(info);
                }
            }
        }
        return list;
    }

    @Inject(method = "init", at = @At("HEAD"))
    void onInit(CallbackInfo ci) {
        if (selectedLevel == 0) {
            // 阶段1：选择目标玩家（使用分页翻页）
            if (roleScreenHelper != null) {
                roleScreenHelper.getPaginationHelper().clearManagedWidgets(this);
            }
            getRoleScreenHelper().onInit(this);
        }
        else if (selectedLevel == 1) {
            // 阶段2：选择死亡原因（无需分页）
            if (!getRoleScreenHelper().isRoleActive()) return;
            
            int apart = 36;
            int y = (this.height - 32) / 2 + 80;
            ItemStack[] deathReasons = DeathReasonHelper.getAvailableDeathReasons();
            
            int x = this.width / 2 - (deathReasons.length * apart) / 2 + 9;
            for (int i = 0; i < deathReasons.length; ++i) {
                String deathReasonId = DeathReasonHelper.getDeathReasonId(deathReasons[i]);
                BodymakerDeathReasonWidget widget = new BodymakerDeathReasonWidget(
                        (LimitedInventoryScreen) (Object) this,
                        x + apart * i, y, deathReasons[i], deathReasonId, selectedPlayerUuid
                );
                addRenderableWidget(widget);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (selectedLevel == 0) {
            getRoleScreenHelper().onRender(context, this);
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
        // 已不再需要，删除阶段3后此回调可移除
    }

    // ========== PlayerPaginationHelper.ScreenWithChildren 接口实现 ==========

    @Override
    public void addDrawableChild(Button button) {
        super.addRenderableWidget(button);
    }

    @Override
    public void removeDrawableChild(Button button) {
        super.removeWidget(button);
    }

    @Override
    public void clearChildren() {
        super.clearWidgets();
    }
}
