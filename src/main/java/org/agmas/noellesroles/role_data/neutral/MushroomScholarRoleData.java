package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.neutral.mushroom_scholar.MushroomScholarRole;
import org.jetbrains.annotations.NotNull;

/** 菌菇学者的当前技能与技能冷却。 */
public class MushroomScholarRoleData extends SimpleRoleData {
    public enum Skill {
        CULTIVATION,
        ESSENCE
    }

    public static final int CULTIVATION_COOLDOWN_TICKS = 40 * 20;
    public static final int ESSENCE_COOLDOWN_TICKS = 90 * 20;

    public Skill selectedSkill = Skill.CULTIVATION;
    public int cultivationCooldownTicks;
    public int essenceCooldownTicks;

    public MushroomScholarRoleData(RoleDataContext context) {
        super(context);
    }

    public void toggleSkill() {
        selectedSkill = selectedSkill == Skill.CULTIVATION ? Skill.ESSENCE : Skill.CULTIVATION;
        sync();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.mushroom_scholar.switch_skill",
                    Component.translatable("hud.noellesroles.mushroom_scholar.skill."
                            + selectedSkill.name().toLowerCase())).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    public boolean useSelectedSkill(ServerPlayer serverPlayer) {
        return selectedSkill == Skill.CULTIVATION
                ? MushroomScholarRole.tryOpenCultivation(serverPlayer)
                : MushroomScholarRole.convertHeldMushroom(serverPlayer);
    }

    @Override
    public void serverTick() {
        boolean changed = false;
        if (cultivationCooldownTicks > 0) {
            cultivationCooldownTicks--;
            changed = true;
        }
        if (essenceCooldownTicks > 0) {
            essenceCooldownTicks--;
            changed = true;
        }
        if (changed && (cultivationCooldownTicks == 0 || essenceCooldownTicks == 0
                || cultivationCooldownTicks % 20 == 0 || essenceCooldownTicks % 20 == 0)) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (cultivationCooldownTicks > 0) cultivationCooldownTicks--;
        if (essenceCooldownTicks > 0) essenceCooldownTicks--;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("selectedSkill", selectedSkill.ordinal());
        tag.putInt("cultivationCooldownTicks", cultivationCooldownTicks);
        tag.putInt("essenceCooldownTicks", essenceCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        selectedSkill = Skill.values()[Math.floorMod(tag.getInt("selectedSkill"), Skill.values().length)];
        cultivationCooldownTicks = Math.max(0, tag.getInt("cultivationCooldownTicks"));
        essenceCooldownTicks = Math.max(0, tag.getInt("essenceCooldownTicks"));
    }
}
