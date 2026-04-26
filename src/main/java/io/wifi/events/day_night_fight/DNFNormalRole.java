package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

public class DNFNormalRole extends NormalRole {
    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public DNFNormalRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.setPassiveIncome(canUseKiller);
        this.setNeutrals(isInnocent == false && canUseKiller == false);
        this.setCanBeRandomedByOtherRoles(false);
    }

    /**
     * 获取一局里最大可出现此职业数量。-1表示不变。
     * 
     * @param gameWorldComponent
     * @param serverLevel
     * @param players
     * @return
     */
    @Override
    public int getRoundMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        if (!gameWorldComponent.gameMode.identifier.equals(SREGameModes.DAY_NIGHT_FIGHT.identifier))
            return 0;
        return super.getRoundMaxCount(serverLevel, gameWorldComponent, players);
    }
}
