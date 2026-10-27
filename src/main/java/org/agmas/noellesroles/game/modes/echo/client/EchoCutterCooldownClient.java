package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemCooldowns;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;

import java.util.UUID;

/** 只冻结原版截流器冷却条目的本地时间轴，不保存第二份权威攻击截止。 */
public final class EchoCutterCooldownClient {
    private static LocalPlayer previousPlayer;
    private static UUID previousMatch;
    private static int previousTick;
    private static ItemCooldowns.CooldownInstance previousEntry;
    private EchoCutterCooldownClient() { }

    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(EchoCutterCooldownClient::tick); }

    private static void tick(Minecraft client) {
        var player=client.player;
        var data=EchoBagClient.data();
        boolean current=player!=null && client.level!=null && data!=null && data.matchId()!=null
                && SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode
                && data.phase()!=EchoPhase.SETTLING && data.phase()!=EchoPhase.FINISHED
                && !player.isSpectator() && !player.isCreative()
                && data.faction()==EchoFaction.INTERCEPTOR && data.life().standing();
        if(!current) {
            if(player!=null && player==previousPlayer) player.getCooldowns().removeCooldown(EchoContent.CUTTER);
            previousPlayer=null; previousMatch=null; previousEntry=null; return;
        }
        var cooldowns=player.getCooldowns();
        var entry=cooldowns.cooldowns.get(EchoContent.CUTTER);
        if(player==previousPlayer && data.matchId().equals(previousMatch)
                && SREGameTimeComponent.KEY.get(client.level).isTimeFrozen()
                && previousEntry!=null && (entry==previousEntry || entry==null)) {
            // 即使原版本 tick 恰好删除了剩 1 tick 的条目，也用上一帧条目平移还原。
            // 新收到的服务端冷却包不平移，以其最新剩余量为准。
            int elapsed=Math.max(0,cooldowns.tickCount-previousTick);
            entry=new ItemCooldowns.CooldownInstance(previousEntry.startTime+elapsed,previousEntry.endTime+elapsed);
            cooldowns.cooldowns.put(EchoContent.CUTTER,entry);
        }
        previousPlayer=player; previousMatch=data.matchId(); previousTick=cooldowns.tickCount; previousEntry=entry;
    }
}
