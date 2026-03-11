package io.wifi.starrailexpress.mixin.world;



import com.llamalad7.mixinextras.sugar.Local;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.Registry;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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