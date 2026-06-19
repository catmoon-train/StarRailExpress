package io.wifi.starrailexpress.client.gui.screen.roster;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 玩家查看界面：只读展示当前生效的职业轮换名单。
 */
public class RoleRosterViewScreen extends AbstractRoleRosterScreen {
    public RoleRosterViewScreen() {
        super(Component.translatable("gui.sre.role_roster.view.title"), ClientRoleRosterCache.snapshot());
    }

    @Override
    protected boolean editable() {
        return false;
    }

    @Override
    protected boolean shouldShow(SRERole role) {
        return working.countFor(role.identifier().toString()) > 0;
    }

    @Override
    protected void buildControls() {
        // 刷新到最新缓存
        this.working = ClientRoleRosterCache.snapshot();
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.close"),
                        b -> this.onClose())
                .bounds(panelX + panelW - 90, panelY + panelH - 30, 80, 20).build());
    }
}
