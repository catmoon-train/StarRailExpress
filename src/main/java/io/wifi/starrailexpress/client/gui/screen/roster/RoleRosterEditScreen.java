package io.wifi.starrailexpress.client.gui.screen.roster;

import com.google.gson.Gson;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.data.ClientRoleRosterCache;
import io.wifi.starrailexpress.network.RoleRosterUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 管理员编辑界面：随机抽选、手动增减各职业数量、开关名单，并保存到服务器（再由服务器同步到数据库）。
 */
public class RoleRosterEditScreen extends AbstractRoleRosterScreen {
    private static final Gson GSON = new Gson();
    private Button toggleButton;

    /** 随机抽选的目标职业数量（不含平民，平民始终包含）。 */
    private int randomCount = 5;

    public RoleRosterEditScreen() {
        super(Component.translatable("gui.sre.role_roster.edit.title"), ClientRoleRosterCache.snapshot());
    }

    @Override
    protected boolean editable() {
        return true;
    }

    @Override
    protected boolean shouldShow(SRERole role) {
        return true;
    }

    @Override
    protected int hintCount() {
        if (working.roleCounts == null) return 0;
        int count = 0;
        for (Integer v : working.roleCounts.values()) {
            if (v != null && v > 0) count++;
        }
        return count;
    }

    @Override
    protected void buildControls() {
        int by = panelY + panelH - 30;
        int bx = panelX + 10;

        // [Random Draw(70)] [- (12)] [count] [+ (12)] [Clear(54)] [Toggle(66)]  ...  [Save(60)] [Close(60)]
        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.randomize"),
                        b -> randomizeLocal())
                .bounds(bx, by, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"),
                        b -> {
                            randomCount = Math.max(1, randomCount - 1);
                        })
                .bounds(bx + 74, by + 4, 12, 12).build());

        addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> {
                            randomCount = Math.min(99, randomCount + 1);
                        })
                .bounds(bx + 104, by + 4, 12, 12).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.clear"),
                        b -> working.roleCounts.clear())
                .bounds(bx + 120, by, 54, 20).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(),
                        b -> {
                            working.enabled = !working.enabled;
                            toggleButton.setMessage(toggleLabel());
                        })
                .bounds(bx + 178, by, 66, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.save"),
                        b -> save())
                .bounds(panelX + panelW - 150, by, 60, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.sre.role_roster.close"),
                        b -> this.onClose())
                .bounds(panelX + panelW - 86, by, 60, 20).build());
    }

    private Component toggleLabel() {
        return working.enabled
                ? Component.translatable("gui.sre.role_roster.toggle.on")
                : Component.translatable("gui.sre.role_roster.toggle.off");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        // 在 [-] 和 [+] 按钮之间绘制随机数量
        int bx = panelX + 10;
        int by = panelY + panelH - 30;
        String countStr = String.valueOf(randomCount);
        int textWidth = this.font.width(countStr);
        g.drawString(this.font, countStr, bx + 90 - textWidth / 2, by + 6, RoleRosterStyle.ACCENT, true);
    }

    /**
     * 随机抽选 {@link #randomCount} 个非平民职业，每个职业分配 1 个名额。
     * 平民始终保留（数量 = max(2, 在线人数)），且确保至少包含一个杀手职业。
     */
    private void randomizeLocal() {
        Random random = new Random();
        int targetPlayers = onlinePlayerCount();
        working.roleCounts.clear();

        // 收集可选的非平民职业
        List<SRERole> pool = new ArrayList<>();
        boolean hasKillerInPool = false;
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (!isRosterEligible(role)) continue;
            if (role == TMMRoles.CIVILIAN) continue;
            pool.add(role);
            if (role.canUseKiller()) hasKillerInPool = true;
        }

        // 随机抽选 randomCount 个
        Collections.shuffle(pool, random);
        int toPick = Math.min(randomCount, pool.size());
        boolean hasKiller = false;
        for (int i = 0; i < toPick; i++) {
            SRERole role = pool.get(i);
            working.roleCounts.put(role.identifier().toString(), 1);
            if (role.canUseKiller()) hasKiller = true;
        }

        // 确保至少有一个杀手职业
        if (!hasKiller && hasKillerInPool) {
            for (SRERole role : pool) {
                if (role.canUseKiller() && !working.roleCounts.containsKey(role.identifier().toString())) {
                    working.roleCounts.put(role.identifier().toString(), 1);
                    break;
                }
            }
            // 如果池子里的杀手已被抽完，就替换一个非杀手职业
            if (!hasKiller) {
                for (SRERole role : pool) {
                    if (role.canUseKiller()) {
                        working.roleCounts.put(role.identifier().toString(), 1);
                        break;
                    }
                }
            }
        }

        // 平民始终包含
        working.roleCounts.put(TMMRoles.CIVILIAN.identifier().toString(), Math.max(2, targetPlayers));
    }

    private int onlinePlayerCount() {
        try {
            if (this.minecraft != null && this.minecraft.getConnection() != null) {
                int size = this.minecraft.getConnection().getListedOnlinePlayers().size();
                if (size > 0) {
                    return size;
                }
            }
        } catch (Throwable ignored) {
            // 使用默认值
        }
        return 8;
    }

    private void save() {
        working.normalized();
        ClientPlayNetworking.send(new RoleRosterUpdatePayload("set", GSON.toJson(working)));
        this.onClose();
    }
}
