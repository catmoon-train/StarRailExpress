package org.agmas.noellesroles.role.touhou.roles;

import java.util.Set;

import org.agmas.harpymodloader.modifiers.SREModifier;

import io.wifi.starrailexpress.api.TouhouRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

public class THHoujuuNueRole extends TouhouRole {

    public THHoujuuNueRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 当赋予modifier时调用，如果需要操作modifiers列表可以直接操纵，不需要同步，也不需要调用WorldModifierComponent的sync
     * 
     * @param player
     * @param modifiers
     */
    public void onAssignedModifiers(ServerPlayer player, Set<SREModifier> modifiers) {
        // - 自带 Jeb 与隐秘修饰符，且可与其他修饰符共存。
        modifiers.add(SEModifiers.JEB_);
        modifiers.add(SEModifiers.SECRETIVE);
    };
}
