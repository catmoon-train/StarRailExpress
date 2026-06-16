package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 任务点烹饪小游戏
 * 无时间限制，接住N个目标食材，食材使用原版材质图标
 */
public class CookingMinigameScreen extends Screen {

    private static final int PAN_WIDTH = 80;
    private static final int FOOD_SIZE = 24;
    private static final int TARGET_COUNT = 5;

    // 食材类型对应的 buff 纹理 (来自 noellesroles:textures/gui/cooking/buff{id}.png)
    private static final int[] FOOD_BUFF_IDS = { 1, 4, -1, 7, 6 };
    private static final int[] FOOD_COLORS = {
            0xFF4ACB73, 0xFF4A8BFF, 0xFFFF6B6B, 0xFFFFD700, 0xFFAA66FF
    };

    private static final ResourceLocation PAN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "textures/gui/cooking/pan.png");

    private final Runnable onSuccess;
    private final Random rng = new Random();

    private ResourceLocation[] foodTextures;
    private float panX;
    private boolean movingLeft, movingRight;

    private final List<FoodItem> foods = new ArrayList<>();
    private int spawnTimer;

    private int targetType;
    private int caughtCount;

    public CookingMinigameScreen(BlockPos questPos, Runnable onSuccess) {
        super(Component.translatable("screen.starrailexpress.cooking_minigame"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        super.init();
        panX = (this.width - PAN_WIDTH) / 2f;
        foods.clear();
        caughtCount = 0;
        spawnTimer = 30;

        foodTextures = new ResourceLocation[FOOD_BUFF_IDS.length];
        for (int i = 0; i < FOOD_BUFF_IDS.length; i++) {
            foodTextures[i] = ResourceLocation.fromNamespaceAndPath(
                    "noellesroles", "textures/gui/cooking/buff" + FOOD_BUFF_IDS[i] + ".png");
        }
        pickTarget();
    }

    private void pickTarget() {
        targetType = rng.nextInt(foodTextures.length);
    }

    @Override
    public void tick() {
        super.tick();
        if (foodTextures == null) return;
        if (movingLeft && panX > 0) panX -= 8;
        if (movingRight && panX + PAN_WIDTH < this.width) panX += 8;

        if (--spawnTimer <= 0) {
            spawnTimer = 18 + rng.nextInt(25);
            foods.add(new FoodItem(30 + rng.nextFloat() * (this.width - 60), -FOOD_SIZE,
                    rng.nextInt(foodTextures.length)));
        }

        for (FoodItem food : foods) food.y += 4.5f;

        List<FoodItem> toRemove = new ArrayList<>();
        for (FoodItem food : foods) {
            int panY = this.height - 42;
            if (food.y + FOOD_SIZE >= panY
                    && food.y + FOOD_SIZE <= panY + 20
                    && food.x + FOOD_SIZE > panX
                    && food.x < panX + PAN_WIDTH) {
                if (food.type == targetType) {
                    caughtCount++;
                    if (caughtCount >= TARGET_COUNT) {
                        onSuccess.run();
                        onClose();
                        return;
                    }
                }
                toRemove.add(food);
            } else if (food.y > this.height) {
                toRemove.add(food);
            }
        }
        foods.removeAll(toRemove);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (foodTextures == null) return;

        // 目标图标 + 进度
        int iconX = this.width / 2 - 30;
        int iconY = 10;
        guiGraphics.blit(foodTextures[targetType], iconX, iconY, 0, 0, FOOD_SIZE, FOOD_SIZE, FOOD_SIZE, FOOD_SIZE);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.starrailexpress.cooking_count", caughtCount, TARGET_COUNT),
                iconX + FOOD_SIZE + 4, iconY + (FOOD_SIZE - 8) / 2, FOOD_COLORS[targetType]);

        // 掉落食材
        for (FoodItem food : foods) {
            guiGraphics.blit(foodTextures[food.type],
                    (int) food.x, (int) food.y, 0, 0, FOOD_SIZE, FOOD_SIZE, FOOD_SIZE, FOOD_SIZE);
        }

        // 平底锅
        int panY = this.height - 42;
        guiGraphics.blit(PAN_TEXTURE, (int) panX, panY, 0, 0,
                PAN_WIDTH, 14, PAN_WIDTH, 14);

        // 操作提示
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.starrailexpress.cooking_hint"),
                this.width / 2, this.height - 15, 0xAAAAAA);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0x521A1A2E, 0x5216213E);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT) { movingLeft = true; return true; }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT) { movingRight = true; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT) { movingLeft = false; return true; }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT) { movingRight = false; return true; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static class FoodItem {
        float x, y;
        int type;
        FoodItem(float x, float y, int type) { this.x = x; this.y = y; this.type = type; }
    }
}
