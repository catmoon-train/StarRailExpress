package io.wifi.events.day_night_fight.block;

import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

public interface DNFBlocks {
    BlockRegistrar registrar = new BlockRegistrar(SRE.TMM_MOD_ID);

    Block CLUE_POINT = registrar.create("clue_point",
            new CluePointBlock(Block.Properties.ofFullCopy(Blocks.END_ROD)
                    .noCollission()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 14)
                    .sound(SoundType.WOOD)
                    .randomTicks()
            ));
    Block WHITE_BLOCK = registrar.create("white_block",
            new WhiteBlock(Block.Properties.ofFullCopy(TMMBlocks.DARK_STEEL)
                    .lightLevel(state -> 14)
                    .sound(SoundType.WOOD)
            ));
    Block UNDERWORLD_DOOR = registrar.create("dnf_underworld_door",
            new DNFUnderworldDoorBlock(Block.Properties.ofFullCopy(Blocks.WHITE_CONCRETE)
                    .noCollission()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 15)
                    .sound(SoundType.WOOD)
            ));
    Block CLEANING_TASK_POINT = registrar.create("dnf_cleaning_task_point",
            new DNFTaskPointBlock(DNFTaskPointBlock.TaskPointType.CLEANING,
                    Block.Properties.ofFullCopy(Blocks.LIGHT_GRAY_CARPET)
                            .noCollission()
                            .strength(0.3F)
                            .lightLevel(state -> 6)
                            .sound(SoundType.WOOL)));
    Block EXCHANGE_TASK_POINT = registrar.create("dnf_exchange_task_point",
            new DNFTaskPointBlock(DNFTaskPointBlock.TaskPointType.EXCHANGE,
                    Block.Properties.ofFullCopy(Blocks.EMERALD_BLOCK)
                            .noCollission()
                            .strength(0.6F)
                            .lightLevel(state -> 8)
                            .sound(SoundType.METAL)));

    static void initialize() {
        registrar.registerEntries();
    }
}
