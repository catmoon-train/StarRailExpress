package org.agmas.noellesroles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ConfigWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    private static final String SKILL_ECHO_ROLE_TRANSLATION_PREFIX = "announcement.star.role.noellesroles.";
    public static final ComponentKey<ConfigWorldComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "config"), ConfigWorldComponent.class);
    public boolean insaneSeesMorphs = true;
    public boolean naturalVoodoosAllowed = false;
    public boolean masterKeyIsVisible = true;
    private final Level world;
    private final Map<UUID, Integer> redPacketTimers = new HashMap<>();
    private static final int RED_PACKET_DELAY_TICKS = 300; // 15秒 = 300 ticks
    private final Set<String> skillEchoAnnouncedRoles = new HashSet<>();
    private int skillEchoRandomTicker = 0;

    public void reset() {
        this.redPacketTimers.clear();
        this.skillEchoAnnouncedRoles.clear();
        this.skillEchoRandomTicker = 0;
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
        processSkillEchoRandomBroadcast();
    }

    public void onPlayerUsedSkill(ServerPlayer player) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        if (!config.skillEchoEventEnabled) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRunning()) {
            return;
        }
        SRERole role = gameWorld.getRole(player);
        if (role == null) {
            return;
        }
        announceSkillEchoForRole(role);
    }

    private void processSkillEchoRandomBroadcast() {
        if (!(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        if (!config.skillEchoEventEnabled) {
            skillEchoAnnouncedRoles.clear();
            skillEchoRandomTicker = 0;
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (!gameWorld.isRunning()) {
            skillEchoAnnouncedRoles.clear();
            skillEchoRandomTicker = 0;
            return;
        }
        if (!config.skillEchoRandomBroadcastEnabled) {
            skillEchoRandomTicker = 0;
            return;
        }

        skillEchoRandomTicker++;
        int intervalTicks = Math.max(1, config.skillEchoRandomIntervalSeconds * 20);
        if (skillEchoRandomTicker < intervalTicks) {
            return;
        }
        skillEchoRandomTicker = 0;

        List<SRERole> unannouncedRoles = collectUnannouncedAliveRoles(serverLevel, gameWorld);
        if (unannouncedRoles.isEmpty()) {
            return;
        }
        SRERole chosenRole = unannouncedRoles.get(serverLevel.getRandom().nextInt(unannouncedRoles.size()));
        announceSkillEchoForRole(chosenRole);
    }

    private List<SRERole> collectUnannouncedAliveRoles(net.minecraft.server.level.ServerLevel serverLevel,
                                                       SREGameWorldComponent gameWorld) {
        List<SRERole> roles = new ArrayList<>();
        Set<String> roleKeys = new HashSet<>();
        for (ServerPlayer serverPlayer : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                continue;
            }
            SRERole role = gameWorld.getRole(serverPlayer);
            if (role == null) {
                continue;
            }
            String roleKey = getRoleKey(role);
            if (!skillEchoAnnouncedRoles.contains(roleKey) && roleKeys.add(roleKey)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private void announceSkillEchoForRole(SRERole role) {
        if (!(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        String roleKey = getRoleKey(role);
        if (skillEchoAnnouncedRoles.contains(roleKey)) {
            return;
        }
        skillEchoAnnouncedRoles.add(roleKey);

        String rolePath = role.getIdentifier() != null ? role.getIdentifier().getPath() : "unknown";
        Component roleName = Component.translatableWithFallback(
                SKILL_ECHO_ROLE_TRANSLATION_PREFIX + rolePath,
                rolePath);
        Component message = Component.translatable("message.noellesroles.skill_echo.heard", roleName)
                .withStyle(ChatFormatting.GOLD);

        for (ServerPlayer target : serverLevel.getServer().getPlayerList().getPlayers()) {
            target.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.9F, 1.0F);
            BroadcastCommand.BroadcastMessage(target, message);
        }
    }

    private String getRoleKey(SRERole role) {
        ResourceLocation id = role.getIdentifier();
        if (id == null) {
            return "unknown";
        }
        return id.toString();
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
