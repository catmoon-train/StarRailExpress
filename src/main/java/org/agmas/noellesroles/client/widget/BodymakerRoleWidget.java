package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.MorticianCreateBodyC2SPacket;
import org.agmas.noellesroles.util.RoleListProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 葬仪输入角色Widget
 * 输入要伪造的角色名
 */
public class BodymakerRoleWidget extends EditBox {

    public final LimitedInventoryScreen screen;
    public static boolean stopClosing = false;
    private final UUID targetPlayerUuid;
    private final String deathReason;
    private final List<String> availableRoles;

    public BodymakerRoleWidget(@NotNull LimitedInventoryScreen screen, @NotNull Font textRenderer, 
                               int x, int y, @NotNull UUID targetPlayerUuid, @NotNull String deathReason) {
        super(textRenderer, x, y, 200, 16, Component.empty());
        this.screen = screen;
        this.targetPlayerUuid = targetPlayerUuid;
        this.deathReason = deathReason;
        this.availableRoles = getAvailableRoles();
        
        this.setMaxLength(64);
        this.setValue("");
        this.setSuggestion(getFirstSuggestion());
    }

    private List<String> getAvailableRoles() {
        List<String> roles = new ArrayList<>();
        
        // 从工具类获取可用角色
        if (RoleListProvider.hasRoleListProvider()) {
            roles.addAll(RoleListProvider.getAvailableRoles());
        } else {
            // 备用列表
            roles.add("executioner");
            roles.add("swapper");
            roles.add("morphling");
            roles.add("voodoo");
            roles.add("manipulator");
            roles.add("ninja");
            roles.add("stalker");
            roles.add("creeper");
            roles.add("party_killer");
            roles.add("bandit");
            roles.add("watcher");
            roles.add("conspirator");
            roles.add("bomber");
            roles.add("delayer");
            roles.add("insane_killer");
            roles.add("imitator");
        }
        
        return roles;
    }
    
    private String getFirstSuggestion() {
        if (availableRoles.isEmpty()) {
            return "";
        }
        String firstRole = availableRoles.get(0);
        // 移除命名空间前缀
        if (firstRole.contains(":")) {
            firstRole = firstRole.substring(firstRole.indexOf(":") + 1);
        }
        return firstRole;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter键或Numpad Enter
            if (!getValue().isEmpty()) {
                String roleName = getValue();
                // 如果没有冒号，自动添加noellesroles命名空间
                if (!roleName.contains(":")) {
                    roleName = "noellesroles:" + roleName;
                }
                // 发送创建尸体包
                ClientPlayNetworking.send(new MorticianCreateBodyC2SPacket(targetPlayerUuid, deathReason, roleName));
                screen.onClose();
            }
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        boolean original = super.charTyped(chr, modifiers);
        updateSuggestion();
        return original;
    }

    private void updateSuggestion() {
        if (availableRoles.isEmpty()) {
            setSuggestion("");
            return;
        }
        
        String currentText = getValue().toLowerCase();
        if (currentText.isEmpty()) {
            setSuggestion(getFirstSuggestion());
            return;
        }
        
        // 找到匹配的建议
        String matchedRole = availableRoles.stream()
            .filter(role -> {
                String displayRole = role.contains(":") ? role.substring(role.indexOf(":") + 1) : role;
                return displayRole.toLowerCase().startsWith(currentText);
            })
            .findFirst()
            .orElse(null);
        
        if (matchedRole != null) {
            String displayRole = matchedRole;
            if (displayRole.contains(":")) {
                displayRole = displayRole.substring(displayRole.indexOf(":") + 1);
            }
            setSuggestion(displayRole.substring(currentText.length()));
        } else {
            setSuggestion("");
        }
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        stopClosing = isFocused();
        updateSuggestion();
        super.renderWidget(context, mouseX, mouseY, delta);
        
        // 绘制提示文本
        if (getValue().isEmpty() && !isFocused()) {
            context.drawString(this.font, 
                Component.translatable("hud.bodymaker.enter_role").getString(), 
                this.getX() + 4, this.getY() + 4, 0x808080, false);
        }
    }
}
