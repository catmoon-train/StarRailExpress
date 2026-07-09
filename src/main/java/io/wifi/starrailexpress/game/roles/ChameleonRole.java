package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 变色龙模式两个职业的公共父类：开局自动穿上整套变色龙衣服。
 *
 * <p>
 * 衣服是“房间装备”（room gear），结算时由
 * {@link ChameleonCompat#closeRoom(MinecraftServer)} 统一回收。
 * 头盔给的是普通款——玩家进入方块伪装时变色龙会自动把它换成高清款。
 * </p>
 */
public class ChameleonRole extends NormalRole {

    public ChameleonRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        addFlag("inner.other_gamemode");
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        super.onInit(server, serverPlayer);
        ChameleonCompat.equipGear(serverPlayer);
    }
}
