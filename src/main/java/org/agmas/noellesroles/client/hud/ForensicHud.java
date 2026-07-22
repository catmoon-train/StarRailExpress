package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ForensicHud 通用物证渲染
 */
public class ForensicHud {
    public static void registerEvents() {
        OnRenderRoleName.RENDER_END.register((player, range, ctx,
                tickCounter, font) -> {
            renderDoor(font, player, ctx, tickCounter);
        });
    }

    /** 8 向罗盘的语言键，按 Minecraft 偏航角顺序排列（0=南）。 */
    private static final String[] DIR_KEYS = {
            "forensic.dir.s", "forensic.dir.sw", "forensic.dir.w", "forensic.dir.nw",
            "forensic.dir.n", "forensic.dir.ne", "forensic.dir.e", "forensic.dir.se"
    };

    public static void renderDoor(Font renderer, Player player, FakeGuiGraphics context,
            DeltaTracker tickCounter) {
        SREConfig cfg = SREConfig.instance();
        if (cfg == null || !cfg.enableForensicEvidence || !cfg.forensicDoorMark) {
            return;
        }
        if (!SREClient.isPlayerAliveAndInSurvival()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult bhr)) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof DoorBlockEntity)) {
            be = mc.level.getBlockEntity(pos.below());
        }
        if (!(be instanceof DoorBlockEntity door)) {
            return;
        }
        long tamperedTime = door.getTamperedTime();
        if (tamperedTime < 0) {
            return;
        }
        int seconds = (int) Math.max(0, (mc.level.getGameTime() - tamperedTime) / 20);
        String toolKey = door.getTamperedTool() == 1 ? "forensic.door.crowbar" : "forensic.door.lockpick";
        Component line = Component.translatable("forensic.door.tampered",
                Component.translatable(toolKey), seconds).withColor(0xE0A030);

        context.pose().pushPose();
        context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F, 0.0F);
        context.pose().scale(SREClient.bodyHUDScale, SREClient.bodyHUDScale, 1.0F);
        context.drawString(renderer, line, -renderer.width(line) / 2, 32, CommonColors.WHITE);
        context.pose().popPose();
    }

    public static void renderCorpse(Font renderer, Player player, PlayerBodyEntity targetBody, FakeGuiGraphics context,
            DeltaTracker tickCounter) {
        // 通用物证
        {
            SREConfig cfg = SREConfig.instance();
            if (cfg == null || !cfg.enableForensicEvidence) {
                return;
            }
            if (!cfg.forensicShowWeaponCategory && !cfg.forensicShowCorpseFacing) {
                return;
            }

            var body = targetBody;
            // 死者/旁观者由验尸官 HUD 的旁观分支处理，这里只服务存活的普通玩家
            if (!SREClient.isPlayerAliveAndInSurvival()) {
                return;
            }
            SRERole selfrole = SREClient.getCachedPlayerRole();
            // 杀手沿用原来的显示：无需蹲下即可看到；非杀手需蹲下才能查看（线索需要"动作"，避免被动全知）
            boolean isKiller = selfrole != null && selfrole.canUseKiller();
            if (!isKiller && !player.isShiftKeyDown()) {
                return;
            }
            // 验尸官（能看精确死因）走专属 HUD，避免重复渲染
            if (selfrole != null && selfrole.canSeeBodyDeathReason(SREClient.cached_player)) {
                return;
            }

            PlayerBodyEntityComponent comp = (PlayerBodyEntityComponent) PlayerBodyEntityComponent.KEY.get(body);
            if (comp == null) {
                return;
            }

            context.pose().pushPose();
            context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F,
                    0.0F);
            context.pose().scale(SREClient.bodyHUDScale, SREClient.bodyHUDScale, 1.0F);

            // 秃鹫吃过的尸体：痕迹已被破坏
            if (comp.vultured) {
                Component destroyed = Component.translatable("forensic.corpse.destroyed").withColor(0x888888);
                context.drawString(renderer, destroyed, -renderer.width(destroyed) / 2, 32, CommonColors.WHITE);
                context.pose().popPose();
                return;
            }

            int y = 32;

            // 拖痕（第4批）：尸体被葬仪曳柩移动过
            if (cfg.forensicShowDragMark) {
                var clientLevel = Minecraft.getInstance().level;
                if (clientLevel != null) {
                    SREGameWorldComponent gw = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(clientLevel);
                    if (gw != null && gw.wasCorpseDragged(body.getPlayerUuid())) {
                        Component dragged = Component.translatable("forensic.corpse.dragged").withColor(0xE0A030);
                        context.drawString(renderer, dragged, -renderer.width(dragged) / 2, y, CommonColors.WHITE);
                        y += 16;
                    }
                }
            }

            if (cfg.forensicShowWeaponCategory) {
                String reasonStr = body.getDeathReason();
                ResourceLocation reason = (reasonStr == null || reasonStr.isBlank())
                        ? GameConstants.DeathReasons.GENERIC
                        : ResourceLocation.tryParse(reasonStr);
                ForensicCategory category = ForensicCategory.fromDeathReason(reason);
                Component line = Component.translatable("forensic.corpse.cause",
                        Component.translatable(category.langKey)).withColor(0xC0C0C0);
                context.drawString(renderer, line, -renderer.width(line) / 2, y, CommonColors.WHITE);
                y += 16;
            }

            if (cfg.forensicShowCorpseFacing) {
                float yaw = body.getYRot();
                int idx = Mth.floor(((yaw % 360.0F + 360.0F) % 360.0F + 22.5F) / 45.0F) & 7;
                Component dir = Component.translatable(DIR_KEYS[idx]);
                Component line = Component.translatable("forensic.corpse.facing", dir).withColor(0xC0C0C0);
                context.drawString(renderer, line, -renderer.width(line) / 2, y, CommonColors.WHITE);
                y += 16;
            }

            // 死亡时间线（第4批）：按尸体存在时长（tickCount 越大死得越早）排出本具尸体的死亡顺序
            if (cfg.forensicShowDeathOrder) {
                int rank = 1;
                int total = 0;
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    for (net.minecraft.world.entity.Entity e : level.entitiesForRendering()) {
                        if (e instanceof PlayerBodyEntity pb) {
                            total++;
                            if (pb != body && pb.tickCount > body.tickCount) {
                                rank++;
                            }
                        }
                    }
                }
                if (total > 1) {
                    Component line = Component.translatable("forensic.corpse.order", rank, total).withColor(0xC0C0C0);
                    context.drawString(renderer, line, -renderer.width(line) / 2, y, CommonColors.WHITE);
                }
            }

            context.pose().popPose();
        }
    }

}
