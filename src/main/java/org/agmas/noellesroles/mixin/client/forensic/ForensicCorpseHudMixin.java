package org.agmas.noellesroles.mixin.client.forensic;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.forensic.ForensicCategory;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.ModComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 通用物证 · 第1批：凶器大类下放 + 尸体朝向。
 *
 * <p>验尸官的死因/死亡时间数据本就同步给所有客户端，这里在 {@link RoleNameRenderer#renderHud}
 * 末尾为"看着尸体的存活普通玩家"渲染一个粗粒度尸检：凶器大类 + 死者面朝方向。
 * 验尸官（能看精确死因）与旁观者走各自的专属 HUD，这里主动让位以免重复。
 *
 * <p>纯读取：不修改任何通用类（PlayerBodyEntity / 组件 / killPlayer）。
 */
@Mixin(RoleNameRenderer.class)
public abstract class ForensicCorpseHudMixin {

    /** 8 向罗盘的语言键，按 Minecraft 偏航角顺序排列（0=南）。 */
    private static final String[] DIR_KEYS = {
            "forensic.dir.s", "forensic.dir.sw", "forensic.dir.w", "forensic.dir.nw",
            "forensic.dir.n", "forensic.dir.ne", "forensic.dir.e", "forensic.dir.se"
    };

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void sre$forensicCorpseHud(Font renderer, LocalPlayer player, FakeGuiGraphics context,
            DeltaTracker tickCounter, CallbackInfo ci) {
        SREConfig cfg = SREConfig.instance();
        if (cfg == null || !cfg.enableForensicEvidence) {
            return;
        }
        if (!cfg.forensicShowWeaponCategory && !cfg.forensicShowCorpseFacing) {
            return;
        }

        var body = NoellesrolesClient.targetBody;
        if (body == null) {
            return;
        }
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
        // 死亡惩罚期间不提供线索
        var deathPenalty = ModComponents.DEATH_PENALTY.get(Minecraft.getInstance().player);
        if (deathPenalty != null && deathPenalty.hasPenalty()) {
            return;
        }

        PlayerBodyEntityComponent comp = (PlayerBodyEntityComponent) PlayerBodyEntityComponent.KEY.get(body);
        if (comp == null) {
            return;
        }

        context.pose().pushPose();
        context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F, 0.0F);
        context.pose().scale(0.6F, 0.6F, 1.0F);

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
            int seconds = body.tickCount / 20;
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
