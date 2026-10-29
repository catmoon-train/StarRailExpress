package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.GameMode;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** 重置完成、身份初始化前的异步准备闸门；返回真只推迟初始化，下个准备 tick 再询问。 */
@FunctionalInterface
public interface DeferGameInitialize {
    Event<DeferGameInitialize> EVENT=EventFactory.createArrayBacked(DeferGameInitialize.class,
            listeners -> (level,mode,players) -> {
                for(var listener:listeners) if(listener.defer(level,mode,players)) return true;
                return false;
            });
    boolean defer(ServerLevel level,GameMode mode,List<ServerPlayer> players);
}
