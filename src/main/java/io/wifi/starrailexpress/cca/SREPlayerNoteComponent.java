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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class SREPlayerNoteComponent implements RoleComponent {
    public static final ComponentKey<SREPlayerNoteComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("note"), SREPlayerNoteComponent.class);
    public final Player player;
    public String[] text = new String[]{"", "", "", ""};
    public boolean written = false;

    public SREPlayerNoteComponent(Player player) {
        this.player = player;
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
        this.text = new String[]{"", "", "", ""};
        this.written = false;
        this.sync();
    }
    @Override
    public void clear(){
        init();
    }
    public void setNote(@NotNull String s, String s1, String s2, String s3) {
        this.text = new String[]{s, s1, s2, s3};
        this.written = !s.isEmpty() || !s1.isEmpty() || !s2.isEmpty() || !s3.isEmpty();
        this.sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putString("line1", this.text[0]);
        tag.putString("line2", this.text[1]);
        tag.putString("line3", this.text[2]);
        tag.putString("line4", this.text[3]);
        tag.putBoolean("written", this.written);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.text[0] = tag.getString("line1");
        this.text[1] = tag.getString("line2");
        this.text[2] = tag.getString("line3");
        this.text[3] = tag.getString("line4");
        this.written = tag.getBoolean("written");
    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
    }
}