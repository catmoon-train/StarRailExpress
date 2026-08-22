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

package org.agmas.noellesroles.game.roles.killer.hunter;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

public class HunterPlayerComponent implements RoleComponent {

    private final Player player;

    // 杀敌计数
    public int killCount = 0;
    private static final int KILLS_PER_BONUS = 3;

    public HunterPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    public void sync() {
        ModComponents.HUNTER.sync(this.player);
    }

    @Override
    public void init() {
        this.killCount = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    /**
     * 猎人击杀玩家后调用
     * - 弓进入20秒原版物品冷却
     * - 累计杀敌数
     * - 每3杀给予1根毒箭
     */
    public void onKill() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        // 弓20秒原版物品冷却
        player.getCooldowns().addCooldown(Items.BOW, 400); // 20秒 = 400 tick

        // 累计杀敌
        killCount++;
        sync();

        // 每3杀奖励毒箭
        if (killCount % KILLS_PER_BONUS == 0) {
            givePoisonArrow();
        }
    }

    /**
     * 给予玩家1根毒箭
     */
    private void givePoisonArrow() {
        ItemStack poisonArrow = Items.TIPPED_ARROW.getDefaultInstance();
        poisonArrow.set(DataComponents.ITEM_NAME, Component.translatable("item.poison_arrow.name"));
        poisonArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
        poisonArrow.set(DataComponents.MAX_STACK_SIZE, 1);
        RoleUtils.insertStackInFreeSlot(player, poisonArrow);

        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(
                    Component.translatable("message.noellesroles.hunter.bonus_arrow"),
                    true);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("killCount", this.killCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.killCount = tag.getInt("killCount");
    }
}
