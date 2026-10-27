package org.agmas.noellesroles.game.modes.echo.result;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;

import java.util.List;
import java.util.Locale;

/** 在标准回放和远端记录消费结果之前填好全体参与者，而不是只遍历在线玩家。 */
public final class EchoResultAdapter {
    private EchoResultAdapter() { }

    public static void apply(ServerLevel level, EchoResultSnapshot snapshot) {
        SREGameRoundEndComponent round = SREGameRoundEndComponent.KEY.get(level);
        round.setRoundEndData(List.of(), GameUtils.WinStatus.CUSTOM_COMPONENT);
        round.CustomWinnerPlayers.clear();
        round.CustomWinnerExtraRoleIds.clear();
        round.CustomWinnerID = "echo_freight";
        round.CustomWinnerTitle = Component.translatable("echo.result." + snapshot.outcome().name().toLowerCase(Locale.ROOT));
        round.CustomWinnerSubtitle = Component.translatable(snapshot.debug()?"echo.runtime.result_debug":"echo.runtime.result",
                Component.translatable(EchoResultReason.translationKey(snapshot.reason())));
        round.CustomWinnerColor = switch (snapshot.outcome()) {
            case CARRIERS -> 0x44BB66;
            case INTERCEPTORS -> 0xCC2233;
            case VOID -> 0xCCAA22;
        };
        for (EchoResultSnapshot.PlayerResult player : snapshot.players()) {
            round.players.add(round.new RoundEndData(new GameProfile(player.uuid(), player.name()),
                    player.lifeState() == EchoLifeState.ELIMINATED, player.hasWin()));
            if (player.hasWin()) round.CustomWinnerPlayers.add(player.uuid());
        }
        round.sync();
    }
}
