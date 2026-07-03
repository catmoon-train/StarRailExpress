package org.agmas.noellesroles.game.roles.neutral.mafia;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.event.ShouldReloadDerringer;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.*;

public class GodfatherComponent implements RoleComponent {
    public static final ComponentKey<GodfatherComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "godfather"),
            GodfatherComponent.class);

    private final Player player;
    public final Set<UUID> familyMembers = new HashSet<>();
    public final Map<UUID, ResourceLocation> previousRoles = new HashMap<>();
    public int loadedBullets = 0;
    public int maxLoadedBullets = 3;
    public int recruitLimit = 4;
    public long recruitCooldownUntil = 0;
    public int recruitCooldownSeconds = 110;

    public GodfatherComponent(Player player) { this.player = player; }

    @Override public Player getPlayer() { return player; }
    @Override public void init() {
        familyMembers.clear(); previousRoles.clear();
        loadedBullets = 0; maxLoadedBullets = 3; recruitLimit = 4;
        recruitCooldownUntil = 0;
        sync();
    }
    @Override public void clear() { init(); }
    public void sync() { KEY.sync(player); }
    @Override public boolean shouldSyncWith(ServerPlayer target) { return target == this.player; }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider r) {
        tag.putInt("LoadedBullets", loadedBullets);
        tag.putInt("MaxLoadedBullets", maxLoadedBullets);
        tag.putInt("RecruitLimit", recruitLimit);
        tag.putLong("RecruitCooldownUntil", recruitCooldownUntil);
        tag.putInt("RecruitCooldownSeconds", recruitCooldownSeconds);
        ListTag list = new ListTag();
        for (UUID id : familyMembers) list.add(StringTag.valueOf(id.toString()));
        tag.put("FamilyMembers", list);
    }
    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider r) {
        loadedBullets = tag.getInt("LoadedBullets");
        maxLoadedBullets = tag.getInt("MaxLoadedBullets");
        recruitLimit = tag.getInt("RecruitLimit");
        recruitCooldownUntil = tag.getLong("RecruitCooldownUntil");
        recruitCooldownSeconds = tag.getInt("RecruitCooldownSeconds");
        familyMembers.clear();
        if (tag.contains("FamilyMembers", Tag.TAG_LIST)) {
            for (Tag t : tag.getList("FamilyMembers", Tag.TAG_STRING))
                try { familyMembers.add(UUID.fromString(t.getAsString())); } catch (Exception ignored) {}
        }
    }
    @Override public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider r) {}
    @Override public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider r) {}

    public static void registerEvents(){
        ShouldReloadDerringer.EVENT.register((victim,killer,deathReason)->{
            if(RoleUtils.isPlayerTheJob(killer, ModRoles.GODFATHER)){
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }
}
