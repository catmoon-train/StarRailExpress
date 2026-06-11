package org.agmas.noellesroles.init;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import dev.doctor4t.ratatouille.util.registrar.BlockRegistrar;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public interface SREFumoBlocks {

    public static ResourceKey<CreativeModeTab> BLOCK_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            SRE.id("fumo"));

    public static final BlockRegistrar blockRegistrar = new BlockRegistrar(Noellesroles.MOD_ID);
    public static final BlockEntityTypeRegistrar blockEntityRegistrar = new BlockEntityTypeRegistrar(
            Noellesroles.MOD_ID);
    // Custom Plushs (Test)

    Block MILK_DRAGON_PLUSH = registerBlock("milk_dragon_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BAKA_PLUSH = registerBlock("baka_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block FURANDORU_PLUSH = registerBlock("furandoru_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block REMILIA_PLUSH = registerBlock("remilia_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MISTIA_PLUSH = registerBlock("mystia_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MARISA_PLUSH = registerBlock("marisa_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block REIMU_PLUSH = registerBlock("reimu_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BAMBOO_PLUSH = registerBlock("bamboo_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block KAORUKO_PLUSH = registerBlock("kaoruko_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BACKVOICE_PLUSH = registerBlock("backvoice_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block BIANTWIN_PLUSH = registerBlock("biantwin_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block CANYUESAMA_PLUSH = registerBlock("canyuesama_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block DIO_PLUSH = registerBlock("dio_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block FUSHIMI_KONIRO_PLUSH = registerBlock("fushimi_koniro_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block GUANZHEQWQ_PLUSH = registerBlock("guanzheqwq_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block HAIMAN233_PLUSH = registerBlock("haiman233_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LENGXIAOCN_PLUSH = registerBlock("lengxiaocn_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LICRAFTLQ_PLUSH = registerBlock("licraftlq_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LUOYERUOSHUI_PLUSH = registerBlock("luoyeruoshui_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block MIFAN520_PLUSH = registerBlock("mifan520_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block NONE_PLUSH = registerBlock("none_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block OTITH_PLUSH = registerBlock("otith_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block THEF0RS4KEN_PLUSH = registerBlock("thef0rs4ken_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block TOMATO_PLUSH = registerBlock("tomato_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XIAO_HEI_HAND_PLUSH = registerBlock("xiao_hei_hand_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block XIAOZHANQWQ_PLUSH = registerBlock("xiaozhanqwq_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block ALLINTOKYO_PLUSH = registerBlock("allintokyo_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    /**
     * Block Entity
     */
    BlockEntityType<SREPlushBlockEntity> PLUSH_BLOCK_ENTITY = blockEntityRegistrar.create("plush",
            BlockEntityType.Builder.of(SREPlushBlockEntity::new, new Block[] { BAKA_PLUSH, FURANDORU_PLUSH,
                    REMILIA_PLUSH, MISTIA_PLUSH, MARISA_PLUSH, REIMU_PLUSH, BAMBOO_PLUSH,
                    KAORUKO_PLUSH, BACKVOICE_PLUSH, BIANTWIN_PLUSH, CANYUESAMA_PLUSH,
                    DIO_PLUSH, FUSHIMI_KONIRO_PLUSH, GUANZHEQWQ_PLUSH, HAIMAN233_PLUSH,
                    LENGXIAOCN_PLUSH, LICRAFTLQ_PLUSH, LUOYERUOSHUI_PLUSH, MIFAN520_PLUSH,
                    NONE_PLUSH, OTITH_PLUSH, THEF0RS4KEN_PLUSH, TOMATO_PLUSH,
                    XIAO_HEI_HAND_PLUSH, XIAOZHANQWQ_PLUSH, ALLINTOKYO_PLUSH, MILK_DRAGON_PLUSH }));

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerBlock(String id, T block, Item.Properties settings) {
        return blockRegistrar.createWithItem(id, block, settings, BLOCK_CREATIVE_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Block> T registerOpBlock(String id, T block) {
        return blockRegistrar.createWithItem(id, block, CreativeModeTabs.OP_BLOCKS);
    }

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLOCK_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.starrailexpress.fumo_blocks")).icon(() -> {
                    return new ItemStack(BAKA_PLUSH.asItem());
                })
                .build());
        blockRegistrar.registerEntries();
        blockEntityRegistrar.registerEntries();
    }
}
