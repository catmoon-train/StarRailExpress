package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.packet.MafiaActionC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.api.TMMRoles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GodfatherRecruitScreen extends Screen {
    private final List<SRERole> familyRoles = new ArrayList<>();

    public GodfatherRecruitScreen() {
        super(Component.translatable("screen.noellesroles.godfather.recruit"));
    }

    @Override
    protected void init() {
        super.init();

        // 自动收集所有被标记为 isMafiaTeam 的职业（排除教父自身）
        // 附属模组只要给职业设置 .setMafiaTeam(true)，就会自动出现在教父的招募 GUI 中
        familyRoles.clear();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role.isMafiaTeam() && !role.identifier().equals(ModRoles.GODFATHER.identifier())) {
                familyRoles.add(role);
            }
        }
        // 按职业路径稳定排序，保证 GUI 排版稳定
        familyRoles.sort(Comparator.comparing(r -> r.identifier().getPath()));

        int cx = width / 2;
        int midY = height / 2 - 50;
        int bw = 100, bh = 50, gap = 10;

        // 两列网格，每个 isMafiaTeam 职业一个按钮（排版与原来一致）
        int rows = (familyRoles.size() + 1) / 2;
        for (int i = 0; i < familyRoles.size(); i++) {
            SRERole role = familyRoles.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = cx + (col == 0 ? -bw - gap / 2 : gap / 2);
            int y = midY + row * (bh + gap);
            var id = role.identifier();
            addRenderableWidget(Button.builder(
                    Component.translatable("role." + id.getNamespace() + "." + id.getPath()),
                    btn -> sendRecruit(id.toString()))
                    .pos(x, y).size(bw, bh).build());
        }

        // Close button
        int closeY = midY + rows * (bh + gap) + 10;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                btn -> onClose())
                .pos(cx - 50, closeY).size(100, 20).build());
    }

    private void sendRecruit(String rolePath) {
        Minecraft client = Minecraft.getInstance();
        if (client.crosshairPickEntity instanceof Player target && client.player != null) {
            ClientPlayNetworking.send(new MafiaActionC2SPacket(MafiaActionC2SPacket.RECRUIT_ROLE, target.getUUID(), rolePath));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, height / 2 - 80, 0xFFFFFF);
    }
}
