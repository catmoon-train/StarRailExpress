package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SRETNTTagGameMode extends SREMurderGameMode {
    public SRETNTTagGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);

    }

    /**
     * 尝试杀死玩家时触发（GameUtils.killPlayer传递）
     * 
     * @param victim      受害者
     * @param spawnBody   生成尸体
     * @param _killer     杀手（为空认为无杀手）
     * @param deathReason 死亡原因
     * @param forceDeath  强制死亡
     */
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        if (forceDeath) {
            super.killPlayer(victim, spawnBody, _killer, deathReason, true);
            return;
        }
        GameUtils.teleportBackToRoom(victim);
        return;
    }

    /**
     * 阻止躲藏者之间/谋杀者之间互相伤害，以及谋杀者死亡后返回出生点。
     * 
     * @param victim      受害者
     * @param spawnBody   生成尸体
     * @param _killer     杀手（为空认为无杀手）
     * @param deathReason 死亡原因
     * @param forceDeath  强制死亡
     */
}
