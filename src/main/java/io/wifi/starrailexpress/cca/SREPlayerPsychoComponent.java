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
        this.sync();
        this.psychoTicks = -1;
    }

    public void resetNotSync() {
        this.stopPsychoAndRefreshPsychoCount(false);
        this.psychoTicks = -1;
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
        if (this.psychoTicks > 0)
            return false;
        SRERole role = SRERoleWorldComponent.KEY.get(this.player.level()).getRole(this.player);
        boolean success = false;
        if (role != null) {
            success = role.onPsychoGiveItem(player, this);
        } else {
            success = RoleUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.BAT));
        }
        if (success) {
            this.setPsychoTicks(GameConstants.getPsychoTimer());
            this.setArmour(GameConstants.getPsychoModeArmour());
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
            gameWorldComponent.setPsychosActive(gameWorldComponent.getPsychosActive() + 1);
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

    @Override
    public void clear() {
        init();
    }

    public int stopPsychoAndSync() {
        int result = stopPsycho();
        sync();
        return result;
    }

    public int stopPsycho() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        int result = gameWorldComponent.getPsychosActive();
        if (result >= 1) {
            gameWorldComponent.setPsychosActive(result - 1);
        }
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
        return result;
    }

    public void stopPsychoAndRefreshPsychoCount(boolean shouldSync) {
        if (this.stopPsycho() > 0) {
            int count = 0;
            if (this.player instanceof ServerPlayer sp) {
                var players = sp.level().players();
                for (var pl : players) {
                    var ppc = SREPlayerPsychoComponent.KEY.maybeGet(pl).orElse(null);
                    if (ppc != null) {
                        if (ppc.psychoTicks > 0) {
                            count++;
                        }
                    }
                }
            }
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
            gameWorldComponent.setPsychosActive(count, shouldSync);
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