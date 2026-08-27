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

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.client.api.InvNoMoveScreen;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.init.ModItems;

public class ChefStartGameScreen extends Screen implements InvNoMoveScreen {

    public ChefStartGameScreen() {
        super(Component.translatable("screen.noellesroles.chef.title"));
    }

    final int BUTTON_WIDTH = 100;
    final int BUTTON_HEIGHT = 20;
    Button btn;
    Component textWidget2;
    boolean hasItem = false;

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);

        // 渲染提示
        Component hint = textWidget2;
        if (!hasItem)
            context.drawCenteredString(font, hint, width / 2, height / 2 + 30, 0x888888);

    }

    @Override
    protected void init() {
        super.init();
        int maxWidth = this.width;
        int maxHeight = this.height;
        int buttonX = maxWidth / 2 - BUTTON_WIDTH / 2;
        int buttonY = maxHeight / 2;
        if (SREItemUtils.countItem(this.minecraft.player, ModItems.FOOD_STUFF) >= 2
                && SREItemUtils.countItem(this.minecraft.player, (food) -> {
                    if (food.getItem() instanceof CocktailItem)
                        return false;
                    if (food.has(ModDataComponentTypes.COOKED))
                        return false;
                    return food.has(DataComponents.FOOD);
                }) >= 1) {
            hasItem = true;
        }
        btn = Button.builder(Component.translatable("screen.noellesroles.chef.start"), (bbtn) -> {
            if (hasItem) {
                this.minecraft.setScreen(new CookingGameScreen());
            }
        }).bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        textWidget2 = Component.translatable("screen.noellesroles.chef.not_enough_food_stuff")
                .withStyle(ChatFormatting.RED);

        if (!hasItem) {
            btn.active = false;
            btn.setTooltip(Tooltip.create(Component.translatable("screen.noellesroles.chef.not_enough_food_stuff")));
        }
        this.addRenderableWidget(btn);
    }
}