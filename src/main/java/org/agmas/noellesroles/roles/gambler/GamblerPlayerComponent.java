package org.agmas.noellesroles.roles.gambler;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.TMMRoles;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GamblerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<GamblerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gambler"), GamblerPlayerComponent.class);
    private final Player player;
    public boolean usedAbility = false;

    // 角色抽取相关
    public List<ResourceLocation> availableRoles = new ArrayList<>();
    public ResourceLocation selectedRole = null;
    public int roleDrawTimer = 0;
    public static final int DRAW_INTERVAL = 30 * 20; // 30秒 //debug

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void reset() {
        this.usedAbility = false;
        this.availableRoles.clear();
        this.selectedRole = null;
        this.roleDrawTimer = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.reset();
    }

    public GamblerPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
    }

    public void serverTick() {
        if (player.level().isClientSide)
            return;

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.GAMBLER))
            return;
        if (!gameWorld.isRunning())
            return;

        roleDrawTimer++;
        if (roleDrawTimer >= DRAW_INTERVAL) {
            roleDrawTimer = 0;
            drawNewRole();
        }
        if (roleDrawTimer % 20 == 0) {
            sync();
        }
    }

    private void drawNewRole() {
        List<Role> allRoles = new ArrayList<>(Noellesroles.getEnableRoles_ServerSide());

        // 过滤掉禁用的角色、赌徒自己、已经在列表中的角色
        List<Role> validRoles = allRoles.stream()
                .filter(role -> !HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().getPath()))
                .filter(role -> !role.identifier().equals(ModRoles.GAMBLER_ID))
                .filter(role -> !role.identifier().equals(ModRoles.DIO_ID))
                .filter(role -> !role.identifier().equals(ModRoles.JOJO_ID))
                .filter(role -> !role.identifier().equals(ModRoles.BEST_VIGILANTE_ID)) // 排除更好的义警
                .filter(role -> !role.identifier().equals(TMMRoles.LOOSE_END.identifier())) // 排除亡命徒
                .filter(role -> !role.identifier().equals(TMMRoles.DISCOVERY_CIVILIAN.identifier())) // 排除游客
                .filter(role -> !availableRoles.contains(role.identifier()))
                .collect(Collectors.toList());

        if (!validRoles.isEmpty()) {
            Collections.shuffle(validRoles);
            Role drawnRole = validRoles.getFirst();
            if (player instanceof ServerPlayer serverPlayer) {

                if (drawnRole.canUseKiller()) {
                    CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, TMMItems.KNIFE.getDefaultInstance());
                } else if (drawnRole.isInnocent()) {
                    CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, TMMItems.KEY.getDefaultInstance());
                } else if (drawnRole.isVigilanteTeam()) {
                    CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, TMMItems.REVOLVER.getDefaultInstance());
                } else if (!drawnRole.isInnocent()) {
                    CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, TMMItems.POISON_VIAL.getDefaultInstance());
                }
                serverPlayer.gameEvent(GameEvent.ITEM_INTERACT_FINISH);

            }
            availableRoles.add(drawnRole.identifier());
            // 如果还没有选择角色，默认选择第一个抽到的
            if (selectedRole == null) {
                selectedRole = drawnRole.identifier();
            }

            this.sync();
        }
    }

    public void selectRole(ResourceLocation roleId) {
        if (availableRoles.contains(roleId)) {
            this.selectedRole = roleId;
            this.sync();
        }
    }

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("usedAbility", this.usedAbility);

        ListTag rolesTag = new ListTag();
        for (ResourceLocation roleId : availableRoles) {
            rolesTag.add(StringTag.valueOf(roleId.toString()));
        }
        tag.put("availableRoles", rolesTag);

        if (selectedRole != null) {
            tag.putString("selectedRole", selectedRole.toString());
        }

        tag.putInt("roleDrawTimer", roleDrawTimer);
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.usedAbility = tag.getBoolean("usedAbility");

        availableRoles.clear();
        if (tag.contains("availableRoles")) {
            ListTag rolesTag = tag.getList("availableRoles", Tag.TAG_STRING);
            for (int i = 0; i < rolesTag.size(); i++) {
                ResourceLocation roleId = ResourceLocation.tryParse(rolesTag.getString(i));
                if (roleId != null) {
                    availableRoles.add(roleId);
                }
            }
        }

        if (tag.contains("selectedRole")) {
            selectedRole = ResourceLocation.tryParse(tag.getString("selectedRole"));
        } else {
            selectedRole = null;
        }

        roleDrawTimer = tag.getInt("roleDrawTimer");
    }
}