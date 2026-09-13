/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.time_rewind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.api.time.internal.TimeRewindPlayerAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gives the time-rewind module one stable seam into vanilla player state.
 * Normal callers never need to know which packets and non-persistent fields
 * must be refreshed after loading player NBT into a live connection.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTimeRewindMixin implements TimeRewindPlayerAccess {
    @Unique
    private static final String NOELLESROLES_COOLDOWNS = "noellesroles:time_rewind_cooldowns";
    @Unique
    private static final String NOELLESROLES_VEHICLE = "noellesroles:time_rewind_vehicle";
    @Unique
    private static final String NOELLESROLES_CAMERA = "noellesroles:time_rewind_camera";

    @Override
    public CompoundTag noellesroles$captureTimeRewindState() {
        ServerPlayer player = (ServerPlayer) (Object) this;
        CompoundTag state = player.saveWithoutId(new CompoundTag());

        // RootVehicle recursively serializes an entity graph and is not consumed by
        // Entity#load on a live player. Keep only the existing vehicle UUID instead.
        state.remove("RootVehicle");
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            state.putUUID(NOELLESROLES_VEHICLE, vehicle.getUUID());
        }
        state.putUUID(NOELLESROLES_CAMERA, player.getCamera().getUUID());

        ListTag cooldownList = new ListTag();
        ItemCooldowns cooldowns = player.getCooldowns();
        cooldowns.cooldowns.forEach((item, instance) -> {
            int remaining = instance.endTime - cooldowns.tickCount;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (remaining > 0 && itemId != null) {
                CompoundTag entry = new CompoundTag();
                entry.putString("item", itemId.toString());
                entry.putInt("remaining", remaining);
                cooldownList.add(entry);
            }
        });
        state.put(NOELLESROLES_COOLDOWNS, cooldownList);
        return state;
    }

    @Override
    public void noellesroles$restoreTimeRewindState(CompoundTag state) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        UUID identity = player.getUUID();
        UUID vehicleId = state.hasUUID(NOELLESROLES_VEHICLE)
                ? state.getUUID(NOELLESROLES_VEHICLE)
                : null;
        UUID cameraId = state.hasUUID(NOELLESROLES_CAMERA)
                ? state.getUUID(NOELLESROLES_CAMERA)
                : identity;

        // Loading effects directly replaces the backing map without notifying every
        // client. Remove first, then re-add the loaded copies through vanilla hooks.
        player.closeContainer();
        player.stopRiding();
        player.removeAllEffects();
        player.load(state.copy());
        player.setUUID(identity);

        if (state.contains("playerGameType", Tag.TAG_INT)) {
            player.setGameMode(GameType.byId(state.getInt("playerGameType")));
        }

        List<MobEffectInstance> loadedEffects = player.getActiveEffects().stream()
                .map(MobEffectInstance::new)
                .toList();
        player.removeAllEffects();
        loadedEffects.forEach(player::addEffect);

        noellesroles$restoreCooldowns(player, state);

        if (vehicleId != null) {
            Entity vehicle = player.serverLevel().getEntity(vehicleId);
            if (vehicle != null && !vehicle.isRemoved()) {
                player.startRiding(vehicle, true);
            }
        }

        // Entity#load mutates a live server player without going through the usual
        // connection hooks. Force one coherent client refresh after all fields settle.
        player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        Entity camera = player.serverLevel().getEntity(cameraId);
        player.setCamera(camera == null || camera.isRemoved() ? player : camera);
        player.onUpdateAbilities();
        player.resetSentInfo();
        player.inventoryMenu.broadcastFullState();
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.connection.send(new ClientboundSetHealthPacket(player.getHealth(),
                player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
        player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress,
                player.totalExperience, player.experienceLevel));
    }

    @Unique
    private static void noellesroles$restoreCooldowns(ServerPlayer player, CompoundTag state) {
        ItemCooldowns cooldowns = player.getCooldowns();
        for (Item item : new ArrayList<>(cooldowns.cooldowns.keySet())) {
            cooldowns.removeCooldown(item);
        }

        if (!state.contains(NOELLESROLES_COOLDOWNS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = state.getList(NOELLESROLES_COOLDOWNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getString("item"));
            int remaining = entry.getInt("remaining");
            if (itemId == null || remaining <= 0) {
                continue;
            }
            BuiltInRegistries.ITEM.getOptional(itemId)
                    .ifPresent(item -> cooldowns.addCooldown(item, remaining));
        }
    }
}
