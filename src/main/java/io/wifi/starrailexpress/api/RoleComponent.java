package io.wifi.starrailexpress.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
/**
 * @author canyuesama
 */
public interface RoleComponent extends AutoSyncedComponent {
    Player getPlayer();
    void reset();
    void clear();

    @Override
    default boolean shouldSyncWith(ServerPlayer player) {
        return this.getPlayer() == player;
    }
}
