package org.agmas.noellesroles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConfigWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<ConfigWorldComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "config"), ConfigWorldComponent.class);
    public boolean insaneSeesMorphs = true;
    public boolean naturalVoodoosAllowed = false;
    public boolean masterKeyIsVisible = true;
    private final Level world;
    private final Map<UUID, Integer> redPacketTimers = new HashMap<>();
    private static final int RED_PACKET_DELAY_TICKS = 300; // 15秒 = 300 ticks

    public void reset() {
        this.redPacketTimers.clear();
        this.sync();
    }

    public void addRedPacketTimer(UUID playerUUID) {
        redPacketTimers.put(playerUUID, RED_PACKET_DELAY_TICKS);
    }

    public ConfigWorldComponent(Level world) {
        this.world = world;
    }

    public Player getPlayer() {
        return null;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        insaneSeesMorphs = NoellesRolesConfig.HANDLER.instance().insanePlayersSeeMorphs;
        naturalVoodoosAllowed = NoellesRolesConfig.HANDLER.instance().voodooNonKillerDeaths;
        tag.putBoolean("insaneSeesMorphs", this.insaneSeesMorphs);
        tag.putBoolean("naturalVoodoosAllowed", this.naturalVoodoosAllowed);
        tag.putBoolean("masterKeyIsVisible", this.masterKeyIsVisible);
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (tag.contains("insaneSeesMorphs"))
            this.insaneSeesMorphs = tag.getBoolean("insaneSeesMorphs");
        if (tag.contains("naturalVoodoosAllowed"))
            this.naturalVoodoosAllowed = tag.getBoolean("naturalVoodoosAllowed");
        if (tag.contains("masterKeyIsVisible"))
            this.masterKeyIsVisible = tag.getBoolean("masterKeyIsVisible");
    }

    @Override
    public void serverTick() {
        // 处理红包延迟发放
        processRedPacketTimers();
    }

    private void processRedPacketTimers() {
        if (!redPacketTimers.isEmpty() && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            redPacketTimers.entrySet().removeIf(entry -> {
                UUID playerUUID = entry.getKey();
                int ticksLeft = entry.getValue();

                if (ticksLeft <= 0) {
                    // 倒计时结束，发放金币
                    ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerUUID);
                    if (player != null && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
                        io.wifi.starrailexpress.cca.SREPlayerShopComponent shopComponent = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY
                                .get(player);
                        shopComponent.addToBalance(100);

                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.nianshou.red_packet_received_delayed", 100)
                                        .withStyle(ChatFormatting.GOLD),
                                true);
                    }
                    return true; // 移除该条目
                } else {
                    // 减少倒计时
                    entry.setValue(ticksLeft - 1);
                    return false; // 保留该条目
                }
            });
        }
    }
}
