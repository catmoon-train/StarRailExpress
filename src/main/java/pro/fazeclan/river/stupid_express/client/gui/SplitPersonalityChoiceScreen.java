package pro.fazeclan.river.stupid_express.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;

public class SplitPersonalityChoiceScreen extends Screen {

    private SplitPersonalityComponent.ChoiceType currentPlayerChoice;
    private Player otherPlayer;
    private SplitPersonalityComponent component;

    public SplitPersonalityChoiceScreen(Player otherPlayer) {
        super(Component.translatable("screen.stupid_express.split_personality.choice.title"));
        this.otherPlayer = otherPlayer;
        this.component = SplitPersonalityComponent.KEY.get(Minecraft.getInstance().player);
        // 初始化当前选择为默认值或现有选择
        this.currentPlayerChoice = component != null
                ? (component.isMainPersonality() ? component.getMainPersonalityChoice()
                        : component.getSecondPersonalityChoice())
                : SplitPersonalityComponent.ChoiceType.SACRIFICE;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 根据CCA组件状态判断是否解锁选择
        boolean canChoose = component != null && component.getMainPersonality() != null && !component.isDeath();

        // 欺骗按钮
        Button betrayButton = Button
                .builder(Component.translatable("hud.stupid_express.split_personality.betray"), button -> {
                    if (canChoose) {
                        currentPlayerChoice = SplitPersonalityComponent.ChoiceType.BETRAY;
                        sendChoiceToServer(SplitPersonalityComponent.ChoiceType.BETRAY);
                        this.onClose();
                    }
                })
                .bounds(centerX - 105, centerY - 20, 100, 40)
                .build();
        betrayButton.active = canChoose;
        this.addRenderableWidget(betrayButton);

        // 奉献按钮 (默认选中)
        Button sacrificeButton = Button
                .builder(Component.translatable("hud.stupid_express.split_personality.sacrifice"), button -> {
                    if (canChoose) {
                        currentPlayerChoice = SplitPersonalityComponent.ChoiceType.SACRIFICE;
                        sendChoiceToServer(SplitPersonalityComponent.ChoiceType.SACRIFICE);
                        this.onClose();
                    }
                })
                .bounds(centerX + 5, centerY - 20, 100, 40)
                .build();
        sacrificeButton.active = canChoose;
        this.addRenderableWidget(sacrificeButton);
    }

    private void sendChoiceToServer(SplitPersonalityComponent.ChoiceType choice) {
        int choiceValue = choice == SplitPersonalityComponent.ChoiceType.SACRIFICE ? 0 : 1;
        ClientPlayNetworking.send(new SplitPersonalityPackets.SplitPersonalityChoicePayload(choiceValue));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);

        // 显示配对玩家信息
        if (otherPlayer != null) {
            MutableComponent partnerInfo = Component.translatable("screen.stupid_express.split_personality.partner",
                    otherPlayer.getDisplayName());
            guiGraphics.drawCenteredString(this.font, (partnerInfo),
                    this.width / 2, this.height / 2 - 35, 0xAAAAAA);
        }

        // 显示当前选择
        MutableComponent currentChoiceText = (currentPlayerChoice == SplitPersonalityComponent.ChoiceType.SACRIFICE
                ? Component.translatable("hud.stupid_express.split_personality.choice_now",
                        Component
                                .translatable("hud.stupid_express.split_personality.sacrifice")
                                .withStyle(ChatFormatting.DARK_GREEN))
                        .withStyle(ChatFormatting.WHITE)
                : Component.translatable("hud.stupid_express.split_personality.choice_now",
                        Component.translatable("hud.stupid_express.split_personality.betray")
                                .withStyle(ChatFormatting.DARK_RED))
                        .withStyle(ChatFormatting.WHITE));
        guiGraphics.drawCenteredString(this.font, (currentChoiceText),
                this.width / 2, this.height / 2 + 30,
                currentPlayerChoice == SplitPersonalityComponent.ChoiceType.SACRIFICE ? 0x00FF00 : 0xFF0000);

        // 显示状态信息
        MutableComponent statusText;
        int statusColor;
        if (component == null) {
            statusText = Component.translatable("hud.stupid_express.split_personality.notinit");
            statusColor = 0xFF0000;
        } else if (component.getMainPersonality() == null) {
            statusText = Component.translatable("hud.stupid_express.split_personality.notpartner");
            statusColor = 0xFF0000;
        } else if (component.isDeath()) {
            statusText = Component.translatable("screen.stupid_express.split_personality.choice.dead");
            statusColor = 0xFF0000;
        } else {
            statusText = Component.translatable("screen.stupid_express.split_personality.choice.available");
            statusColor = 0x00FF00;
        }

        guiGraphics.drawCenteredString(this.font, (statusText),
                this.width / 2, this.height / 2 + 50, statusColor);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true; // 允许按ESC关闭
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public SplitPersonalityComponent.ChoiceType getCurrentPlayerChoice() {
        return currentPlayerChoice;
    }

    public void setCurrentPlayerChoice(SplitPersonalityComponent.ChoiceType choice) {
        this.currentPlayerChoice = choice;
    }
}