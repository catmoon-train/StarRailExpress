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

package pro.fazeclan.river.stupid_express.client.modifier.split_personality;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.client.gui.widget.SplitPersonalityChoiceWidget;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

/**
 * 双重人格（modifier）背包界面扩展：在背包界面（{@link LimitedInventoryScreen}）显示
 * "献祭/背叛"选择按钮与提示文本。
 *
 * <p>非职业扩展，通过 {@code LimitedInventoryScreenEvents} 注册
 * （init 开头 + render 末尾），见 {@code StupidExpressClient.registerInventoryEvents()}。
 */
public final class SplitPersonalityInventoryScreenExtension {

    public static final SplitPersonalityInventoryScreenExtension INSTANCE = new SplitPersonalityInventoryScreenExtension();

    private Button sacrificeButton;
    private Button betrayButton;
    private Button hiddenButton;

    private SplitPersonalityComponent component;
    private boolean hiddenText = false;

    private SplitPersonalityInventoryScreenExtension() {
    }

    /** {@code init()} 开头：创建双重人格选择按钮。 */
    public void onInit(LimitedInventoryScreen screen) {
        Player player = screen.player;
        this.component = SplitPersonalityComponent.KEY.get(player);

        // 只有在是双重人格且未死亡时才添加选择功能
        if (component != null && component.getMainPersonality() != null && component.getSecondPersonality() != null
                && !component.isDeath()) {
            SplitPersonalityChoiceWidget widgetFactory = new SplitPersonalityChoiceWidget(player);

            int buttonX = screen.width / 2 - 110;
            int buttonY = screen.height / 2 + 60;

            // 创建并添加两个独立的按钮
            this.sacrificeButton = widgetFactory.createSacrificeButton(buttonX, buttonY);
            this.betrayButton = widgetFactory.createBetrayButton(buttonX + 110, buttonY);
            this.hiddenButton = Button
                    .builder(Component.translatable("hud.stupid_express.split_personality.hidden"), (b) -> {
                        this.hiddenText = true;
                        this.betrayButton.visible = false;
                        this.sacrificeButton.visible = false;
                        this.hiddenButton.visible = false;
                    }).bounds(screen.width / 2 - 50, buttonY + 44, 100, 20).build();

            screen.addRoleWidget(this.sacrificeButton);
            screen.addRoleWidget(this.betrayButton);
            screen.addRoleWidget(this.hiddenButton);
        }
    }

    /** {@code render()} 末尾：显示选择提示文本与当前选择状态。 */
    public void onRenderTail(LimitedInventoryScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY,
            float delta) {
        if (this.hiddenText)
            return;
        Player player = screen.player;
        if (component == null) {
            component = SplitPersonalityComponent.KEY.get(player);
        }

        // 显示双重人格选择提示
        if (component != null && component.getMainPersonality() != null && !component.isDeath()) {
            Minecraft client = Minecraft.getInstance();
            int centerX = screen.width / 2;
            int centerY = screen.height / 2;

            // 标题文本
            MutableComponent titleText = Component
                    .translatable("screen.stupid_express.split_personality.inventory.choice.title")
                    .withStyle(ChatFormatting.GOLD);
            guiGraphics.drawCenteredString(client.font, titleText, centerX, centerY + 30, 0xFFFF00);

            // 按钮说明文本
            MutableComponent hintText = Component.translatable("or_2_argu",
                    Component.translatable("hud.stupid_express.split_personality.sacrifice")
                            .withStyle(ChatFormatting.GREEN),
                    Component.translatable("hud.stupid_express.split_personality.betray")
                            .withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY);
            guiGraphics.drawCenteredString(client.font, hintText, centerX, centerY + 45, 0xAAAAAA);

            if (component.isMainPersonality()) {
                MutableComponent mainChoice = component
                        .getMainPersonalityChoice() == SplitPersonalityComponent.ChoiceType.SACRIFICE
                                ? Component.translatable("hud.stupid_express.split_personality.choice_now",
                                        Component
                                                .translatable("hud.stupid_express.split_personality.sacrifice")
                                                .withStyle(ChatFormatting.DARK_GREEN))
                                        .withStyle(ChatFormatting.WHITE)
                                : Component.translatable("hud.stupid_express.split_personality.choice_now",
                                        Component.translatable("hud.stupid_express.split_personality.betray")
                                                .withStyle(ChatFormatting.DARK_RED))
                                        .withStyle(ChatFormatting.WHITE);
                guiGraphics.drawCenteredString(client.font, mainChoice, centerX, centerY + 85, 0xFFFFFF);
            } else {
                MutableComponent secondChoice = component
                        .getSecondPersonalityChoice() == SplitPersonalityComponent.ChoiceType.SACRIFICE
                                ? Component.translatable("hud.stupid_express.split_personality.choice_now",
                                        Component
                                                .translatable("hud.stupid_express.split_personality.sacrifice")
                                                .withStyle(ChatFormatting.DARK_GREEN))
                                        .withStyle(ChatFormatting.WHITE)
                                : Component.translatable("hud.stupid_express.split_personality.choice_now",
                                        Component.translatable("hud.stupid_express.split_personality.betray")
                                                .withStyle(ChatFormatting.DARK_RED))
                                        .withStyle(ChatFormatting.WHITE);
                guiGraphics.drawCenteredString(client.font, secondChoice, centerX, centerY + 85, 0xFFFFFF);
            }
        }
    }
}
