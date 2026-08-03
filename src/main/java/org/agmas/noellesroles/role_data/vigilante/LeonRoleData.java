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

package org.agmas.noellesroles.role_data.vigilante;

import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 里昂组件 —— 被动「幸存之人」。
 *
 * <p>
 * 作为那场危机的幸存者，里昂会在场上人数减少到特定阈值时获得救命草药：
 * 当存活玩家剩下 {@link NoellesRolesConfig#leonBlueHerbAtPlayers} 位时获得蓝色草药
 * （右键立即刷新格斗体术），当剩下 {@link NoellesRolesConfig#leonRedHerbAtPlayers} 位时
 * 获得红色草药（长按右键为自己套上一层不可叠加的护盾）。每种草药一局仅发放一次。
 */
public class LeonRoleData extends SimpleRoleData {

    public LeonRoleData(RoleDataContext context) {
        super(context);
    }

    /** 是否已发放过蓝色草药（避免重复发放）。 */
    public boolean blueHerbGiven = false;

    /** 是否已发放过红色草药（避免重复发放）。 */
    public boolean redHerbGiven = false;

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }

        long alive = GameUtils.getAlivePlayerCount(player.level());

        if (!blueHerbGiven && alive <= NoellesRolesConfig.HANDLER.instance().leonBlueHerbAtPlayers) {
            blueHerbGiven = true;
            sync();
            giveHerb(serverPlayer, ModItems.LEON_BLUE_HERB.getDefaultInstance(),
                    "message.noellesroles.leon.blue_herb_received", ChatFormatting.AQUA);
        }

        if (!redHerbGiven && alive <= NoellesRolesConfig.HANDLER.instance().leonRedHerbAtPlayers) {
            redHerbGiven = true;
            sync();
            giveHerb(serverPlayer, ModItems.LEON_RED_HERB.getDefaultInstance(),
                    "message.noellesroles.leon.red_herb_received", ChatFormatting.RED);
        }
    }

    private void giveHerb(ServerPlayer serverPlayer, ItemStack stack, String messageKey, ChatFormatting color) {
        MCItemsUtils.insertStackInFreeSlot(serverPlayer, stack);
        serverPlayer.displayClientMessage(
                Component.translatable(messageKey).withStyle(color), false);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("blueHerbGiven", blueHerbGiven);
        tag.putBoolean("redHerbGiven", redHerbGiven);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        blueHerbGiven = tag.getBoolean("blueHerbGiven");
        redHerbGiven = tag.getBoolean("redHerbGiven");
    }
}
