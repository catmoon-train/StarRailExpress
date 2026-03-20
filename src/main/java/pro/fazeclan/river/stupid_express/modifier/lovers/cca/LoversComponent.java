package pro.fazeclan.river.stupid_express.modifier.lovers.cca;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.UUID;

public class LoversComponent implements AutoSyncedComponent {

    public static final ComponentKey<LoversComponent> KEY = ComponentRegistry.getOrCreate(StupidExpress.id("lovers"),
            LoversComponent.class);

    private final Player player;

    private UUID lover;

    public UUID getLover() {
        return this.lover;
    }

    public void setLover(UUID lover) {
        this.lover = lover;
    }

    public LoversComponent(Player player) {
        this.player = player;
    }

    public void reset() {
        this.lover = null;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public boolean won() {
        if (this.lover == null) {
            return false;
        }
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        var serverLevel = (ServerLevel) serverPlayer.level();
        if (GameUtils.isPlayerEliminated(this.player)) {
            return false;
        }
        var lover = getLoverAsPlayer();
        if (lover == null) {
            return false;
        }
        if (GameUtils.isPlayerEliminated(lover)) {
            return false;
        }
        var remainingPlayers = serverLevel.getPlayers(GameUtils::isPlayerAliveAndSurvival);
        return remainingPlayers.size() == 2;
    }

    public boolean isLover() {
        return this.lover != null;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return sp == this.player;
    }

    public Player getLoverAsPlayer() {
        return this.player.level().getPlayerByUUID(this.lover);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.lover = tag.contains("lover") ? tag.getUUID("lover") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.lover != null)
            tag.putUUID("lover", this.lover);
    }
}
