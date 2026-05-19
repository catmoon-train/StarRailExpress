package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.packet.MorticianCreateBodyC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.util.DeathReasonHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 葬仪选择死亡原因Widget
 * 显示可用的死亡原因供葬仪选择
 */
public class BodymakerDeathReasonWidget extends Button {

    public final LimitedInventoryScreen screen;
    public final ItemStack deathReasonStack;
    public final UUID targetPlayerUuid;
    public final MorticianScreenCallback callback;
    public final String deathReasonId;

    public BodymakerDeathReasonWidget(@NotNull LimitedInventoryScreen screen, int x, int y, 
                                     @NotNull ItemStack deathReasonStack, @NotNull String deathReasonId,
                                     @NotNull UUID targetPlayerUuid, @NotNull MorticianScreenCallback callback) {
        super(x, y, 16, 16, Component.empty(), (button) -> {
            if (Minecraft.getInstance().player == null) return;
            
            // 检查是否启用了FakeRole选择
            if (DeathReasonHelper.isFakeRoleEnabled()) {
                // 进入角色选择阶段
                callback.setSelectedDeathReason(deathReasonId);
            } else {
                // 直接发送创建尸体包（不指定角色）
                ClientPlayNetworking.send(new MorticianCreateBodyC2SPacket(targetPlayerUuid, deathReasonId, ""));
                screen.onClose();
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.deathReasonStack = deathReasonStack;
        this.targetPlayerUuid = targetPlayerUuid;
        this.callback = callback;
        this.deathReasonId = deathReasonId;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        context.renderItem(deathReasonStack, this.getX(), this.getY());
        context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        if (this.isHovered()) {
            this.drawShopSlotHighlight(context, this.getX(), this.getY());
            // 显示死亡原因提示
            String translationKey = "death_reason." + deathReasonId.replace(':', '.');
            context.renderTooltip(Minecraft.getInstance().font, Component.translatable(translationKey), mouseX, mouseY);
        }
    }

    private void drawShopSlotHighlight(@NotNull GuiGraphics context, int x, int y) {
        int color = 0x80000000;
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
