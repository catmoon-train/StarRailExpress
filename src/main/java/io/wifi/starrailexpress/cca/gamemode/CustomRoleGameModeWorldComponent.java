package io.wifi.starrailexpress.cca.gamemode;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.custom_role.CustomRoleUpdateHandler;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import net.fabricmc.api.EnvType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.*;
import java.util.stream.Collectors;

public class CustomRoleGameModeWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<CustomRoleGameModeWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("custom_roles"),
            CustomRoleGameModeWorldComponent.class);
    private final Level world;
    HashMap<String, SRERole> pathToRole = new HashMap<>();

    // 职业阵营 / 职业
    private final ArrayList<SRERole> available_roles = new ArrayList<>();

    public CustomRoleGameModeWorldComponent(Level world) {
        this.world = world;
    }

    public void clear() {
        this.available_roles.clear();
    }

    public void clearAndSync() {
        this.available_roles.clear();
        sync();
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public ArrayList<SRERole> getRoles() {
        return this.available_roles;
    }

    public List<SRERole> getRole(int type) {
        if (available_roles == null) {
            return Collections.emptyList();
        }
        return available_roles.stream()
                .filter(Objects::nonNull) // 过滤掉 key 为 null 的项
                .filter(r -> PlayerRoleWeightManager.getRoleType(r) == type)
                .collect(Collectors.toList());
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    public void reloadPathToRole() {
        pathToRole.clear();
        for (var r : TMMRoles.ROLES.entrySet()) {
            var role = r.getValue();
            pathToRole.putIfAbsent(role.identifier().getPath(), role);
        }
    }

    public void writeToSyncNbtWithPlayer(CompoundTag tag, HolderLookup.Provider registryLookup,
            ServerPlayer recipient) {
        if (this.available_roles.isEmpty())
            return;
        int teamType = CustomRoleGameModeTeamsPlayerComponent.KEY.get(recipient).getTeam();
        var roleInfoCompund = new ListTag();
        for (SRERole info : available_roles) {
            if (PlayerRoleWeightManager.getRoleType(info) == teamType) {
                String roleId = info.identifier().getPath();
                roleInfoCompund.add(StringTag.valueOf(roleId));
            }
        }
        if (!roleInfoCompund.isEmpty())
            tag.put("roles", roleInfoCompund);
    }

    public @Nullable SRERole getRoleFromPath(String path) {
        if (pathToRole.containsKey(path)) {
            return pathToRole.get(path);
        } else {
            reloadPathToRole();
            if (pathToRole.containsKey(path)) {
                return pathToRole.get(path);
            }
        }
        return null;
    }

    public void readFromSyncNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        // this.lockedToSupporters = nbtCompound.getBoolean("LockedToSupporters");
        // this.enableWeights = nbtCompound.getBoolean("EnableWeights");
        this.available_roles.clear();

        if (nbtCompound.contains("roles", CompoundTag.TAG_LIST)) {
            ListTag roleInfoCompund = nbtCompound.getList("roles", CompoundTag.TAG_STRING);
            for (var info : roleInfoCompund) {
                StringTag str = (StringTag) info;
                String rolePath = str.getAsString();
                SRERole role = getRoleFromPath(rolePath);
                if (role != null) {
                    this.available_roles.add(role);
                }
            }
        }

        if (this.world.isClientSide) {
            CustomRoleUpdateHandler.updateRoleSelection();
        }
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        this.writeToSyncNbtWithPlayer(tag, buf.registryAccess(), recipient);
        buf.writeNbt(tag);
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            this.readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    public void playerSelectedRole(ServerPlayer player, ResourceLocation roleId) {
        var crgmtpcca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(player);
        SRERole role = RoleUtils.getRole(roleId);
        if (crgmtpcca.getTeam() == PlayerRoleWeightManager.getRoleType(role)
                && this.available_roles.contains(role)) {
            player.displayClientMessage(Component
                    .translatable("gui.noellesroles.gambler.selected", RoleUtils.getRoleOrModifierNameWithColor(role))
                    .withStyle(ChatFormatting.GOLD), true);
            changeRoleButNoEvents(player, role);
            int idx = -1;
            for (int i = 0; i < this.available_roles.size(); i++) {
                if (available_roles.get(i).equals(role)) {
                    idx = i;
                    break;
                }
            }
            if (idx != -1) {
                available_roles.remove(idx);
            }
            this.syncToSpecificRoleTypePlayers(PlayerRoleWeightManager.getRoleType(role));
        } else {
            player.displayClientMessage(Component
                    .translatable("gui.noellesroles.gambler.failed", RoleUtils.getRoleOrModifierNameWithColor(role))
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    public void changeRoleButNoEvents(ServerPlayer player, SRERole role) {
        SRERoleWorldComponent roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
        roleWorldComponent.addRole(player.getUUID(), role, false);
        player.addEffect(new MobEffectInstance(
                ModEffects.SKILL_BANED,
                -1,
                10,
                true, // ambient - 环境效果（粒子更少更透明）
                false, // showParticles - 不显示粒子
                false // showIcon - 不显示图标
        ));
        CustomRoleGameModeTeamsPlayerComponent.KEY.get(player).setSelectedAndSync(true);
        SRE.REPLAY_MANAGER.recordPlayerRoleChange(player.getUUID(), SpecialGameModeRoles.CUSTOM_PENDING, role);
    }

    public void autoSelect(ServerPlayer player) {
        var crgmtpcca = CustomRoleGameModeTeamsPlayerComponent.KEY.get(player);
        ArrayList<ResourceLocation> roles = crgmtpcca.getAvailableRoles();
        if (roles.isEmpty()) {
            changeRoleButNoEvents(player, TMMRoles.CIVILIAN);
            return;
        } else {
            playerSelectedRole(player, roles.getFirst());
        }
    }

    public void addAllRoles(List<RoleInstance> expandedRoles) {
        for (var roleInstance : expandedRoles) {
            SRERole role = roleInstance.role();
            if (role != null)
                this.available_roles.add(role);
        }
    }

    public void syncToSpecificRoleTypePlayers(int roletype) {
        if (this.world instanceof ServerLevel serverLevel) {
            for (ServerPlayer p : serverLevel.players()) {
                if (CustomRoleGameModeTeamsPlayerComponent.KEY.get(p).getTeam() == roletype) {
                    KEY.syncWith(p, this.world.asComponentProvider());
                }
            }
        }
    }
}