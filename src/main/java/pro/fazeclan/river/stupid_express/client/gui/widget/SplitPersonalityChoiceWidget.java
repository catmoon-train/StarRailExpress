package pro.fazeclan.river.stupid_express.client.gui.widget;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent.ChoiceType;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;

/**
 * 双重人格选择按钮工厂类
 * 用于创建独立的选择按钮
 */
public class SplitPersonalityChoiceWidget {

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private Button betrayButton;
    private Button sacrificeButton;
    public final Player player;
    private final SplitPersonalityComponent component;

    public SplitPersonalityChoiceWidget(Player player) {
        this.player = player;
        this.component = SplitPersonalityComponent.KEY.get(player);
    }

    /**
     * 创建奉献按钮
     */
    public Button createSacrificeButton(int x, int y) {
        var btn = Button.builder(Component.translatable("hud.stupid_express.split_personality.sacrifice"), button -> {
            if (sacrificeButton != null)
                sacrificeButton.active = false;
            if (betrayButton != null)
                betrayButton.active = true;
            submitChoice(SplitPersonalityComponent.ChoiceType.SACRIFICE);
        })
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        if (component.isMainPersonality()) {
            if (component.getMainPersonalityChoice().equals(ChoiceType.SACRIFICE)) {
                btn.active = false;
            } else {
                btn.active = true;
            }
        } else {
            if (component.getSecondPersonalityChoice().equals(ChoiceType.SACRIFICE)) {
                btn.active = false;
            } else {
                btn.active = true;
            }
        }
        this.sacrificeButton = btn;
        return btn;
    }

    /**
     * 创建欺骗按钮
     */
    public Button createBetrayButton(int x, int y) {
        var btn = Button.builder(Component.translatable("hud.stupid_express.split_personality.betray"), button -> {
            if (sacrificeButton != null)
                sacrificeButton.active = true;
            if (betrayButton != null)
                betrayButton.active = false;
            submitChoice(SplitPersonalityComponent.ChoiceType.BETRAY);
        })
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        if (component.isMainPersonality()) {
            if (component.getMainPersonalityChoice().equals(ChoiceType.BETRAY)) {
                btn.active = false;
            } else {
                btn.active = true;
            }
        } else {
            if (component.getSecondPersonalityChoice().equals(ChoiceType.BETRAY)) {
                btn.active = false;
            } else {
                btn.active = true;
            }
        }
        this.betrayButton = btn;
        return btn;
    }

    /**
     * 提交选择到服务器
     */
    private void submitChoice(SplitPersonalityComponent.ChoiceType choice) {
        if (component != null) {
            if (component.isMainPersonality()) {
                component.setMainPersonalityChoice(choice);
            } else {
                component.setSecondPersonalityChoice(choice);
            }
        }
        // 发送网络包到服务器
        int choiceValue = (choice == SplitPersonalityComponent.ChoiceType.SACRIFICE ? 0 : 1);
        ClientPlayNetworking.send(new SplitPersonalityPackets.SplitPersonalityChoicePayload(choiceValue));
    }

    /**
     * 获取组件引用
     */
    public SplitPersonalityComponent getComponent() {
        return component;
    }
}