package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.command_block.SteveMaterial;
import org.agmas.noellesroles.game.command_block.SteveRoleData;
import org.agmas.noellesroles.role.ModRoles;

/** 右上角的 Steve 虚拟材料账本，图标不对应背包物品或掉落实体。 */
public final class SteveMaterialsHud {
    private SteveMaterialsHud() {
    }

    public static void register() {
        SteveMaterial[] materials = SteveMaterial.values();
        ItemStack[] icons = new ItemStack[materials.length];
        for (int i = 0; i < materials.length; i++) icons[i] = materials[i].item().getDefaultInstance();
        RoleHudRenderCallback.EVENT.register(ModRoles.STEVE_ID, (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || !SREClient.isPlayerAliveAndInSurvival()
                    || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) return;
            SteveRoleData data = RoleData.getNullable(SteveRoleData.class, client.player);
            if (data == null) return;
            Component title = Component.translatable("hud.noellesroles.steve.materials");
            Component[] rows = new Component[materials.length];
            int width = client.font.width(title) + 16;
            for (int i = 0; i < materials.length; i++) {
                rows[i] = Component.translatable("hud.noellesroles.steve.material_count",
                        icons[i].getHoverName(), data.count(materials[i]));
                width = Math.max(width, client.font.width(rows[i]) + 38);
            }
            int x = Math.max(8, graphics.guiWidth() - width - 8);
            int y = client.player.getActiveEffects().stream().anyMatch(MobEffectInstance::showIcon) ? 56 : 8;
            graphics.fill(x, y, x + width, y + 24 + materials.length * 18, 0xC81A1008);
            graphics.fill(x, y, x + width, y + 1, 0xFFD4AF37);
            graphics.drawString(client.font, title, x + 8, y + 7, 0xFFD4AF37);
            for (int i = 0; i < materials.length; i++) {
                int rowY = y + 22 + i * 18;
                graphics.renderItem(icons[i], x + 7, rowY);
                graphics.drawString(client.font, rows[i], x + 28, rowY + 4, 0xFFFFF4DC);
            }
        });
    }
}
