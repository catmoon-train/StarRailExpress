package org.agmas.noellesroles.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;

public class DeathPenaltyComponent implements RoleComponent, ServerTickingComponent {
    private final Player player;
    public long penaltyExpiry = 0;
    public UUID limitCameraUUID = null;
    public boolean chatEnabled = false;

    public void clearAll() {
        this.reset();
    }

    public void check() {
        if (!this.hasPenalty()) {
            return;
        } else {
            if (GameUtils.isPlayerAliveAndSurvival(this.player)) {
                this.reset();
                return;
            }
            if (this.penaltyExpiry < 0) {
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
                if (limitCameraUUID != null) {
                    Player cameraPlayer = this.player.level().getPlayerByUUID(limitCameraUUID);
                    if (cameraPlayer != null && GameUtils.isPlayerAliveAndSurvival(cameraPlayer)) {
                        return;
                    }
                }
                // boolean INSANE_alive = false;
                boolean CONSPIRATOR_alive = false;
                for (Player p : player.level().players()) {
                    if (gameWorldComponent.isRole(p, ModRoles.CONSPIRATOR)
                            && GameUtils.isPlayerAliveAndSurvival(p)) {
                        CONSPIRATOR_alive = true;
                    }
                    if (CONSPIRATOR_alive) {
                        if (this.penaltyExpiry == -2) {
                            this.penaltyExpiry = -1;
                            player.sendSystemMessage(
                                    Component.translatable("message.noellesroles.penalty.limit.god_job_couple")
                                            .withStyle(ChatFormatting.RED));
                            player.displayClientMessage(
                                    Component.translatable("message.noellesroles.penalty.limit.god_job_couple")
                                            .withStyle(ChatFormatting.RED),
                                    true);
                            if (player.hasPermissions(2)) {
                                player.sendSystemMessage(
                                        Component.translatable("message.noellesroles.admin.free_cam_hint")
                                                .withStyle(ChatFormatting.YELLOW));
                            }
                        }
                        return;
                    }
                }
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.penalty.unlimit").withStyle(ChatFormatting.GREEN),
                        true);
                player.sendSystemMessage(
                        Component.translatable("message.noellesroles.penalty.unlimit").withStyle(ChatFormatting.GREEN));
                this.reset();
                return;
                // 亡语杀手限制
            } else {
                if (player.level().getGameTime() >= this.penaltyExpiry) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.penalty.unlimit")
                            .withStyle(ChatFormatting.GREEN), true);
                    player.sendSystemMessage(Component.translatable("message.noellesroles.penalty.unlimit")
                            .withStyle(ChatFormatting.GREEN));
                    this.reset();
                    return;
                }
            }
        }
    }

    public DeathPenaltyComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void setPenalty(long durationTicks) {
        if (durationTicks < 0) {
            this.penaltyExpiry = -1;
            ModComponents.DEATH_PENALTY.sync(player);
            return;
        }
        this.penaltyExpiry = player.level().getGameTime() + durationTicks;
        ModComponents.DEATH_PENALTY.sync(player);
    }

    public boolean hasPenalty() {
        if (this.penaltyExpiry == 0)
            return false;
        if (this.penaltyExpiry < 0) {
            return true;
        }
        if (player.level().getGameTime() >= this.penaltyExpiry) {
            this.penaltyExpiry = -2;
        }
        return true;
    }

    @Override
    public void reset() {
        this.penaltyExpiry = 0;
        if (!player.level().isClientSide) {
            if (limitCameraUUID != null) {
                if (player instanceof ServerPlayer sp) {
                    sp.setCamera(null);
                }
            }
        }
        this.limitCameraUUID = null;
        ModComponents.DEATH_PENALTY.sync(player);

    }

    @Override
    public void clear() {
        this.reset();
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.penaltyExpiry = tag.getLong("penaltyExpiry");
        if (tag.contains("chatEnabled")) {
            this.chatEnabled = tag.getBoolean("chatEnabled");
        } else {
            this.chatEnabled = true;
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("penaltyExpiry", this.penaltyExpiry);
        if (this.limitCameraUUID != null) {
            tag.putBoolean("chatEnabled", false);
        }
    }

    @Override
    public void serverTick() {
        if (player != null) {
            if (player instanceof ServerPlayer sp) {
                if (limitCameraUUID != null) {
                    if (!sp.getCamera().getUUID().equals(limitCameraUUID)) {
                        var target = sp.level().getPlayerByUUID(limitCameraUUID);
                        if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                            sp.setCamera(target);
                        } else {
                            sp.setCamera(null);
                        }
                    }
                }
            }
        }
    }
}