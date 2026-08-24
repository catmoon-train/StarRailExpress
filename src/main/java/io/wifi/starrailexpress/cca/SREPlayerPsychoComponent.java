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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import io.wifi.starrailexpress.network.TriggerStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SREPlayerPsychoComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerPsychoComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("psycho"),
            SREPlayerPsychoComponent.class);
    private final Player player;
    public int psychoTicks = -1;
    public int armour = 1;
    public int type = -1;
    private SREGameWorldComponent gameWorldComponent = null;
    public ItemStack savedItemSlot0 = null;

    public SREPlayerPsychoComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        if (checkIsGameRunning())
            return true;
        return false;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.stopPsychoAndRefreshPsychoCount(true);
        this.psychoTicks = -1;
        this.savedItemSlot0 = null;
        this.sync();
    }

    public void resetNotSync() {
        this.stopPsychoAndRefreshPsychoCount(false);
        this.psychoTicks = -1;
        this.savedItemSlot0 = null;
    }

    @Override
    public void clientTick() {
        if (!checkIsGameRunning()) {
            if (this.psychoTicks > 0)
                this.psychoTicks = -1;
            return;
        }

        if (this.psychoTicks <= 0)
            return;
        if (this.psychoTicks > 1) {
            if (this.player.isSpectator()) {
                this.psychoTicks = -1;
                return;
            }
            this.psychoTicks--;
        }
        Item psychoItem = TMMItems.BAT;
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (role != null) {
            psychoItem = role.getPsychoItem();
        }
        if (this.player.getMainHandItem().is(psychoItem))
            return;
        if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            for (int i = 0; i < 9; i++) {
                if (!this.player.getInventory().getItem(i).is(psychoItem))
                    continue;
                this.player.getInventory().selected = i;
                break;
            }
        }

    }

    @Override
    public void serverTick() {
        if (!checkIsGameRunning()) {
            if (this.psychoTicks > 0) {
                this.stopPsycho();
            }
            return;
        }
        if (this.psychoTicks <= 0)
            return;
        if (this.psychoTicks > 0) {
            if (this.player.isSpectator()) {
                this.stopPsychoAndRefreshPsychoCount(true);
                return;
            }
        }
        if (--this.psychoTicks == 0) {
            this.stopPsycho();
            this.sync();
        } else {
            if (this.psychoTicks % 200 == 0) { // 10s一次
                this.sync();
            }
        }

    }

    public boolean startPsycho() {
        return startPsycho(1d, GameConstants.getPsychoModeArmour(), false);
    }

    public boolean startPsycho_time(int time, int armour, boolean forceStart) {

        if (this.psychoTicks > 0)
            return false;
        this.savedItemSlot0 = null;

        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(this.player);
        boolean success = givePsychoItem(role);

        if (!success) {
            if (!forceStart)
                return false;
            savedItemSlot0 = player.getInventory().getItem(0).copy();
            player.getInventory().setItem(0, ItemStack.EMPTY);
            success = givePsychoItem(role);
            if (!success) {
                player.getInventory().setItem(0, savedItemSlot0);
                return false;
            }
        }
        if (success) {
            this.setPsychoTicks(time);
            this.setArmour(armour);
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
            gameWorldComponent.refreshPsychoCount(true);
            if (player instanceof ServerPlayer serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new TriggerStatusBarPayload("Psycho"));
            }
            if (role != null) {
                role.onPsychoStart(player, this);
            }
            return true;
        }
        return false;
    }

    private boolean givePsychoItem(SRERole role) {
        boolean success = false;
        if (role != null) {
            success = role.onPsychoGiveItem(player, this);
        } else {
            success = RoleUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.BAT));
        }
        return success;
    }

    public boolean startPsycho(double multtiplier, int armour, boolean forceStart) {
        return startPsycho_time((int) ((double) GameConstants.getPsychoTimer() * multtiplier), armour, forceStart);
    }

    @Override
    public void clear() {
        init();
    }

    public boolean stopPsychoAndSync() {
        boolean result = stopPsycho();
        sync();
        return result;
    }

    public boolean stopPsycho() {
        return stopPsycho(true);
    }

    public boolean stopPsycho(boolean refresh) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        this.psychoTicks = -1;
        if (this.player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new RemoveStatusBarPayload("Psycho"));
        }

        Item psychoItem = TMMItems.BAT;
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (role != null) {
            psychoItem = role.getPsychoItem();
        }

        MCItemsUtils.clearItem(player, psychoItem);
        if (checkIsGameRunning()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                if (role != null) {
                    role.onPsychoOver(player, this);
                }
            }
        }
        if (savedItemSlot0 != null && savedItemSlot0 != ItemStack.EMPTY) {
            if (player.getInventory().getItem(0) == ItemStack.EMPTY) {
                player.getInventory().setItem(0, savedItemSlot0);
            } else {
                if (!RoleUtils.insertStackInFreeSlot(player, savedItemSlot0)) {
                    player.drop(savedItemSlot0, false);
                }
            }
        }
        if (refresh)
            gameWorldComponent.refreshPsychoCount(true);
        return true;
    }

    public void stopPsychoAndRefreshPsychoCount(boolean shouldSync) {
        if (this.psychoTicks > 0)
            this.stopPsycho();
        if (shouldSync) {
            sync();
        }
    }

    public int getArmour() {
        return this.armour;
    }

    public void setArmour(int armour) {
        this.armour = armour;
        this.sync();
    }

    public int getPsychoTicks() {
        return this.psychoTicks;
    }

    public void setPsychoTicks(int ticks) {
        this.psychoTicks = ticks;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    public boolean checkIsGameRunning() {
        gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        return gameWorldComponent.isRunning();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        tag.putInt("psychoTicks", this.psychoTicks);
        tag.putInt("armour", this.armour);
        tag.putInt("type", this.type);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        this.psychoTicks = tag.contains("psychoTicks") ? tag.getInt("psychoTicks") : 0;
        this.armour = tag.contains("armour") ? tag.getInt("armour") : 1;
        this.type = tag.contains("type") ? tag.getInt("type") : -1;
    }
}