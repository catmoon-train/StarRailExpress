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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class MorphlingRoleData extends SimpleRoleData {

    public UUID disguise;
    public int morphTicks = 0;
    public int tickR = 0;
    private SREGameWorldComponent gameWorldComponent = null;

    @Override
    public void init() {
        this.stopMorph(false);
    }

    public boolean checkIsGameRunning() {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        return gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        if (!checkIsGameRunning())
            return false;
        return true;
    }

    @Override
    public void clear() {
        this.init();
    }

    public MorphlingRoleData(RoleDataContext context) {
        super(context);
    }

    public void clientTick() {
        if (!checkIsGameRunning()) {
            this.morphTicks = 0;
            return;
        }
        if (this.morphTicks != 0) {
            if (this.morphTicks > 0) {
                this.morphTicks--;
            } else {
                this.morphTicks++;
            }
        }
    }

    public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRole(this.player, ModRoles.MORPHLING))
            return;
        if (!checkIsGameRunning()) {
            this.morphTicks = 0;
            return;
        }
        if (this.morphTicks != 0) {
            ++tickR;
            if (this.morphTicks > 0) {
                if (disguise != null) {
                    if (player.level().getPlayerByUUID(disguise) != null) {
                        // if (((ServerPlayer) player.level().getPlayerByUUID(disguise)).gameMode
                        // .getGameModeForPlayer() == GameType.SPECTATOR) {
                        // stopMorph();
                        // return;
                        // }
                    } else {
                        stopMorph(false);
                        return;
                    }
                } else {
                    stopMorph(false);
                    return;
                }

                if (--this.morphTicks == 0) {
                    this.stopMorph(true);
                    return;
                }
            } else if (this.morphTicks < 0) {
                this.morphTicks++;
                if (this.morphTicks == 0) {
                    sync();
                    return;
                }
            }

            if (tickR % 200 == 0) {
                sync();
            }
        }
    }

    public boolean startMorph(UUID id) {
        if (player instanceof ServerPlayer)
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);
        setMorphTicks(GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().morphlingMorphDuration));
        disguise = id;
        this.sync();

        // 回放记录：变形者改变自身皮肤
        if (player instanceof ServerPlayer) {
            Player target = player.level().getPlayerByUUID(id);
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.shapeshifter.change_skin",
                            GameReplayUtils.getReplayPlayerDisplayText((ServerPlayer) player, true),
                            target != null ? GameReplayUtils.getReplayPlayerDisplayText(target, true)
                                    : Component.literal("<???>")));
        }
        return true;
    }

    public void stopMorph() {
        stopMorph(false);
    }

    /**
     * Stop morphing. If {@code startCooldown} is true, start the configured
     * cooldown (negative ticks).
     * If false, simply end morphing without applying cooldown.
     */
    public void stopMorph(boolean startCooldown) {
        if (startCooldown) {
            this.morphTicks = -GameConstants.getInTicks(0,
                    NoellesRolesConfig.HANDLER.instance().morphlingMorphCooldown);
        } else {
            this.morphTicks = 0;
        }
        this.sync();
    }

    public int getMorphTicks() {
        return this.morphTicks;
    }

    public void setMorphTicks(int ticks) {
        this.morphTicks = ticks;
        this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        // Always sync morphTicks so clients can know cooldown (negative) or ready state
        // (0).
        tag.putInt("morphTicks", this.morphTicks);
        if (this.morphTicks > 0 && disguise != null) {
            tag.putUUID("disguise", this.disguise);
        }
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.morphTicks = tag.contains("morphTicks") ? tag.getInt("morphTicks") : 0;
        this.disguise = tag.contains("disguise") ? tag.getUUID("disguise") : player.getUUID();
    }

}
