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

package pro.fazeclan.river.stupid_express.modifier.knight.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.UUID;

public class KnightComponent implements RoleComponent {

    public static final ComponentKey<KnightComponent> KEY =
            ComponentRegistry.getOrCreate(StupidExpress.id("knight"), KnightComponent.class);

    private final Player player;

    private UUID knight;

    public KnightComponent(Player player) {
        this.player = player;
    }

    public UUID getKnight() {
        return this.knight;
    }

    public void setKnight(UUID uuid) {
        this.knight = uuid;
        sync();
    }

    public boolean isKnight() {
        return this.knight != null && this.knight.equals(player.getUUID());
    }

    public void init() {
        this.knight = null;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.knight = tag.contains("knight") ? tag.getUUID("knight") : null;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.knight != null) {
            tag.putUUID("knight", this.knight);
        }
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}