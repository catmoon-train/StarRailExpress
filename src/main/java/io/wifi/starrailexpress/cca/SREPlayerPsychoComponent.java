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
import net.minecraft.world.InteractionHand;
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

import java.util.List;

public class SREPlayerPsychoComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerPsychoComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("psycho"),
            SREPlayerPsychoComponent.class);
    private final Player player;
    public int psychoTicks = -1;
    public int armour = 1;
    public int type = -1;
    private SREGameWorldComponent gameWorldComponent = null;
    public ItemStack savedItemSlot0 = null;
    /** 只记录本次 Psycho 为玩家新发放的武器，结束时不删除玩家原有武器。 */
    private Item grantedPsychoWeapon = null;
    // 本tick内有sync请求，推迟到该玩家serverTick统一发包，把同tick内的重复广播合并成一次
    private boolean syncPending = false;

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
        if (this.player.level().isClientSide)
            return;
        this.syncPending = true;
    }

    /**
     * 服务端tick开头统一把本tick内积累的同步请求发出，避免同一tick内重复全服广播。
     */
    private void flushPendingSync() {
        if (!this.syncPending)
            return;
        this.syncPending = false;
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.stopPsychoAndRefreshPsychoCount(true);
        this.psychoTicks = -1;
        this.savedItemSlot0 = null;
        this.grantedPsychoWeapon = null;
        this.sync();
    }

    public void resetNotSync() {
        this.stopPsychoAndRefreshPsychoCount(false);
        this.psychoTicks = -1;
        this.savedItemSlot0 = null;
        this.grantedPsychoWeapon = null;
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
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (isPsychoSupportedWeapon(role, this.player.getMainHandItem()))
            return;
        if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
            int slot = findPsychoWeaponSlot(role, 9);
            if (slot >= 0) {
                this.player.getInventory().selected = slot;
                return;
            }
        }
        if (isPsychoSupportedWeapon(role, this.player.getOffhandItem()))
            return;

    }

    @Override
    public void serverTick() {
        flushPendingSync();
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
            SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
            if (!isPsychoSupportedWeapon(role, this.player.getMainHandItem())
                    && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player)) {
                equipExistingPsychoWeapon(role);
            }
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
        this.grantedPsychoWeapon = null;

        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(this.player);
        boolean success = equipExistingPsychoWeapon(role);
        if (!success) {
            success = givePsychoItem(role);
        }

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
        List<Item> supportedWeapons = getPsychoSupportedWeapons(role);
        int[] countsBefore = supportedWeapons.stream().mapToInt(this::countItem).toArray();
        boolean success = role != null
                ? role.onPsychoGiveItem(player, this)
                : RoleUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.BAT));
        if (!success) {
            return false;
        }
        for (int i = 0; i < supportedWeapons.size(); i++) {
            Item weapon = supportedWeapons.get(i);
            if (countItem(weapon) > countsBefore[i]) {
                if (role == null || role.shouldClearGrantedPsychoWeapon(player, weapon)) {
                    grantedPsychoWeapon = weapon;
                }
                break;
            }
        }
        // Dream 等职业可以合法地启动 Psycho 而不发放手持物。
        equipExistingPsychoWeapon(role);
        return true;
    }

    private List<Item> getPsychoSupportedWeapons(SRERole role) {
        if (role == null) {
            return List.of(TMMItems.BAT);
        }
        List<Item> weapons = role.getPsychoSupportedWeapons(player);
        return weapons == null ? List.of() : weapons.stream().filter(item -> item != null).distinct().toList();
    }

    private boolean isPsychoSupportedWeapon(SRERole role, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return role == null ? stack.is(TMMItems.BAT) : role.isPsychoSupportedWeapon(player, stack);
    }

    /** 供切槽、滚轮等输入限制共用。 */
    public boolean isPsychoSupportedWeapon(ItemStack stack) {
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        return isPsychoSupportedWeapon(role, stack);
    }

    public boolean hasPsychoSupportedWeapon() {
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        return isPsychoSupportedWeapon(role, player.getOffhandItem())
                || findPsychoWeaponSlot(role, player.getInventory().getContainerSize()) >= 0;
    }

    private int findPsychoWeaponSlot(SRERole role, int slotLimit) {
        int limit = Math.min(slotLimit, player.getInventory().items.size());
        for (Item weapon : getPsychoSupportedWeapons(role)) {
            for (int slot = 0; slot < limit; slot++) {
                if (player.getInventory().getItem(slot).is(weapon)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /** 已有武器优先：主手已持有则不动；快捷栏切槽；背包与当前主手槽交换；只在别处都没有时才把副手换入主手。 */
    private boolean equipExistingPsychoWeapon(SRERole role) {
        if (isPsychoSupportedWeapon(role, player.getMainHandItem())) {
            return true;
        }
        int slot = findPsychoWeaponSlot(role, player.getInventory().items.size());
        if (slot >= 0) {
            if (slot < 9) {
                player.getInventory().selected = slot;
            } else {
                int selected = player.getInventory().selected;
                ItemStack weapon = player.getInventory().getItem(slot);
                ItemStack displaced = player.getInventory().getItem(selected);
                player.getInventory().setItem(selected, weapon);
                player.getInventory().setItem(slot, displaced);
            }
            syncInventory();
            return true;
        }
        if (isPsychoSupportedWeapon(role, player.getOffhandItem())) {
            swapSelectedWithOffhand();
            return true;
        }
        return false;
    }

    private void swapSelectedWithOffhand() {
        int selected = player.getInventory().selected;
        ItemStack offhand = player.getOffhandItem();
        ItemStack displaced = player.getInventory().getItem(selected);
        player.setItemInHand(InteractionHand.OFF_HAND, displaced);
        player.getInventory().setItem(selected, offhand);
        syncInventory();
    }

    private void syncInventory() {
        player.getInventory().setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
    }

    private int countItem(Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
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

        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(player);
        if (grantedPsychoWeapon != null) {
            MCItemsUtils.clearItem(player, grantedPsychoWeapon);
            grantedPsychoWeapon = null;
        }
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

    public void stopPsychoIfNeccessary() {
        if (this.psychoTicks > 0) {
            this.stopPsycho();
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

    public boolean havePsycho() {
        return this.psychoTicks > 0;
    }

    public boolean inPsycho() {
        return this.psychoTicks > 0;
    }
}
