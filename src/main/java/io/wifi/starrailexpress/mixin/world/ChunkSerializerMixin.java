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

package io.wifi.starrailexpress.mixin.world;


import io.wifi.starrailexpress.SRE;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSerializer.class)
public class ChunkSerializerMixin {
    @Redirect(method = "read",at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;getCompound(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;",ordinal = 1))
    private static CompoundTag read(CompoundTag instance, String string) {

        var blockStates = instance.getCompound("block_states");
        
        if (blockStates.contains("palette")) {
            var paletteList = blockStates.getList("palette", Tag.TAG_COMPOUND);
            for (int i = 0; i < paletteList.size(); i++) {
                var entry = paletteList.getCompound(i);
                if (entry.contains("Name")) {
                    var name = entry.getString("Name");
                    var newName = name.replaceFirst("^wathe:", SRE.TMM_MOD_ID + ":");
                    entry.putString("Name", newName);
                }
            }
        }
        
        return blockStates;
    }

}