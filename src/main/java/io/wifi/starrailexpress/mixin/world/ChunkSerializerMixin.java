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
                    var newName = name.replaceAll("wathe", SRE.MOD_ID);
                    entry.putString("Name", newName);
                }
            }
        }
        
        return blockStates;
    }

}