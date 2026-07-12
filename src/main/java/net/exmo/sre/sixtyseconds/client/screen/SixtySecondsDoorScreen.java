package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.content.block.DoorPurpose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 事件门 / 拜访门的 GUI 壳（P0）：只显示标题 + 关闭按钮，业务逻辑留 TODO。
 * 后续接 S2C 数据 + C2S 动作（参照 {@code RepairRoleShopScreen}）。
 */
public class SixtySecondsDoorScreen extends Screen {
    private final int purpose;

    public SixtySecondsDoorScreen(int purpose) {
        super(titleFor(purpose));
        this.purpose = purpose;
    }

    private static Component titleFor(int purpose) {
        return Component.translatable(purpose == DoorPurpose.VISIT.ordinal()
                ? "message.noellesroles.sixty_seconds.visit_door"
                : "message.noellesroles.sixty_seconds.event_door");
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height / 2 + 20, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 20, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
