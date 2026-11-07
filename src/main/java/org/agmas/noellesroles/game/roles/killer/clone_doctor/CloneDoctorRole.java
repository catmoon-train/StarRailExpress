package org.agmas.noellesroles.game.roles.killer.clone_doctor;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.RoleAllegianceEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 克隆博士：职业能力可临时改变，克隆之人标记代表不变的杀手身份。 */
public final class CloneDoctorRole extends NormalRole {
    public static final net.minecraft.resources.ResourceLocation COPY_SKILL = Noellesroles.id("clone_doctor_copy");

    public CloneDoctorRole() {
        super(Noellesroles.id("clone_doctor"), 0x69C6A1, false, true, MoodType.FAKE, Integer.MAX_VALUE, true);
        setCanBeRandomedByOtherRoles(false);
        setDefaultMax(1);
    }

    public static boolean isClone(Player player) {
        return player != null && isClone(player.level(), player.getUUID());
    }

    public static boolean isClone(Level level, UUID player) {
        return player != null && WorldModifierComponent.KEY.get(level).isModifier(player, NRModifiers.CLONED_PERSON);
    }

    public static boolean isCivilianCopy(Player player) {
        if (!isClone(player)) return false;
        SRERole role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        return role != null && role != ModRoles.CLONE_DOCTOR && role.isInnocent() && !role.isVigilanteTeam();
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer player) {
        super.onInit(server, player);
        WorldModifierComponent.KEY.get(player.level()).addModifier(player, NRModifiers.CLONED_PERSON, true);
        CloneDoctorSessionManager.ensureState(player);
    }

    @Override
    public void onAssignedModifiers(ServerPlayer player, Set<SREModifier> modifiers) {
        modifiers.add(NRModifiers.CLONED_PERSON);
    }

    public static void registerEvents() {
        RoleAllegianceEvents.OVERRIDE.register((level, playerId) ->
                isClone(level, playerId) ? ModRoles.CLONE_DOCTOR : null);
        RoleSkill.register(ModRoles.CLONE_DOCTOR,
                RoleSkill.skill(COPY_SKILL, "skill.noellesroles.clone_doctor.copy", context ->
                        CloneDoctorSessionManager.beginCopy(context.player(), context.getTargetAsPlayer()))
                        .withTarget().showOnHud(true).recordReplay().build());
        CloneDoctorSessionManager.registerEvents();
        CloneSubstituteManager.registerEvents();
    }

    public static void handleAction(ServerPlayer player, UUID target, boolean returnToSelf) {
        if (returnToSelf) CloneDoctorSessionManager.returnToSelf(player);
        else CloneDoctorSessionManager.createSubstitute(player, target);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(
                new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), 60, ShopEntry.Type.POISON),
                new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL),
                new ShopEntry(ModItems.AREA_BLACKOUT.getDefaultInstance(), 175, ShopEntry.Type.TOOL),
                new ShopEntry(ModItems.SLIME_GRENADE.getDefaultInstance(), 50, ShopEntry.Type.TOOL) {
                    @Override public boolean canBuy(Player player) {
                        return CloneDoctorSessionManager.canBuySlime(player) && super.canBuy(player);
                    }
                    @Override public boolean onBuy(Player player) {
                        if (!canBuy(player) || !super.onBuy(player)) return false;
                        CloneDoctorSessionManager.slimeBought(player);
                        return true;
                    }
                });
    }
}
