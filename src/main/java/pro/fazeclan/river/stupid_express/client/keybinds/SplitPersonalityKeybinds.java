package pro.fazeclan.river.stupid_express.client.keybinds;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.lwjgl.glfw.GLFW;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnOpenInventory;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.client.network.SplitPersonalityClientPackets;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

public class SplitPersonalityKeybinds {

    public static final KeyMapping SWITCH_PERSONALITY_KEY = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.stupid_express.switch_personality",
                    GLFW.GLFW_KEY_Y,
                    "category.stupid_express"));

    public static void registerKeyPressCallbacks() {
        // 这个方法将在客户端初始化时被调用
        OnOpenInventory.EVENT.register((player, screen) -> {
            // false 默认 true limited screen
            if (player.isCreative())
                return false;
            if (screen instanceof InventoryScreen) {
                var component = SplitPersonalityComponent.KEY.get(player);
                if (component != null) {
                    if (component != null && component.getMainPersonality() != null
                            && component.getSecondPersonality() != null
                            && !component.isDeath()) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    public static void handleSwitchPersonalityKey(LocalPlayer player) {
        if (player == null)
            return;
        if (!WorldModifierComponent.KEY.get(player.level()).isModifier(player, SEModifiers.SPLIT_PERSONALITY))
            return;
        if(!SREGameWorldComponent.KEY.get(player.level()).isSkillAvailable){
            player.displayClientMessage(Component.translatable("hud.stupid_express.split_personality.not_available")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        var component = SplitPersonalityComponent.KEY.get(player);
        if (component == null) {
            player.displayClientMessage(Component.translatable("hud.stupid_express.split_personality.notinit")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        if (component.getMainPersonality() == null) {
            player.displayClientMessage(Component.translatable("hud.stupid_express.split_personality.notinit")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        // 已经移除了死亡倒计时机制

        // 已死亡无法切换
        if (component.isDeath()) {
            player.displayClientMessage(
                    Component.translatable("hud.stupid_express.split_personality.dead").withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        // 发送切换请求到服务器
        // 服务器会验证冷却时间和其他条件
        SplitPersonalityClientPackets.sendSwitchPacket();
        player.displayClientMessage(Component.translatable("hud.stupid_express.split_personality.changing")
                .withStyle(ChatFormatting.YELLOW), true);
    }
}
