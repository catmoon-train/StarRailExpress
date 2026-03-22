package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.item.CocktailItem;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.ModDataComponentTypes;
import org.agmas.noellesroles.init.ModItems;

public class ChefStartGameScreen extends Screen {

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
        if (TMMItemUtils.hasItem(this.minecraft.player, ModItems.FOOD_STUFF) >= 2
                && TMMItemUtils.hasItem(this.minecraft.player, (food) -> {
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