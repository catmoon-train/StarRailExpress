package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * 末日60秒：准星指向其他玩家时，在名字下方显示其家庭关系「第 X 队 · 父亲/母亲/妹妹/哥哥」
 * （同队绿色 / 他队蓝色；倒地者整行红色并追加 [倒地]，方便队友识别救援）。
 * <p>
 * 挂 {@link OnRenderRoleName#RENDER_PLAYER_EXTRA}（遵守 RoleNameRenderer 不改本体的约定）；
 * 他人的队伍/家庭身份经 {@code SixtySecondsStatsComponent} 的精简同步变体下发（追踪开始时自动补发）。
 */
public final class SixtySecondsNameTag {

    private SixtySecondsNameTag() {
    }

    public static void register() {
        OnRenderRoleName.RENDER_PLAYER_EXTRA.register((self, target, context, tickCounter, font) -> {
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                    || SixtySecondsMod.MODE == null
                    || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
                return;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(target);
            if (stats.teamId < 0 || stats.familyPosition == null) {
                return;
            }
            boolean sameTeam = SixtySecondsStatsComponent.KEY.get(self).teamId == stats.teamId;
            MutableComponent text = Component.translatable("hud.noellesroles.sixty_seconds.nametag_family",
                    stats.teamId + 1,
                    Component.translatable("hud.noellesroles.sixty_seconds.family."
                            + stats.familyPosition.name().toLowerCase(Locale.ROOT)));
            if (stats.downed) {
                text = text.append(Component.literal(" "))
                        .append(Component.translatable("hud.noellesroles.sixty_seconds.nametag_downed"));
            }
            int color = stats.downed ? 0xFFFF6060 : sameTeam ? 0xFF7CE87C : 0xFFA9D4FF;
            // 名字画在 y=16（RoleNameRenderer 已平移到准星中心并缩放 0.6），这行紧跟其下
            context.drawString(font, text, -font.width(text) / 2, 16 + font.lineHeight + 2, color);
        });
    }
}
