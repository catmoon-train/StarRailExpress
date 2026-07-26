package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.BlockEntityTypeRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.SREItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block.SREPlushItem;
import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;

public interface SREFumoBlocks {

    public static ResourceKey<CreativeModeTab> FUMO_BLOCK_CREATIVE_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            SRE.id("fumo"));

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
    Block AQIONG_PLUSH = registerBlock("aqiong_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block HAOZI_PLUSH = registerBlock("haozi_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    Block LIANGJIE_PLUSH = registerBlock("liangjie_plush",
            new SREPlushBlock(Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    // 自定义玩家 plush：单个动态方块，按绑定的玩家名渲染该玩家皮肤
    Block CUSTOM_PLAYER_PLUSH = registerBlock("custom_player_plush",
            new org.agmas.noellesroles.content.block.CustomPlayerPlushBlock(
                    Properties.ofFullCopy(Blocks.LIGHT_BLUE_WOOL).noOcclusion()));
    // 物品

    Item MILK_DRAGON_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(MILK_DRAGON_PLUSH, new Item.Properties().stacksTo(64)));
    Item BAKA_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(BAKA_PLUSH, new Item.Properties().stacksTo(64))); // 已存在，保留
    Item FURANDORU_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(FURANDORU_PLUSH, new Item.Properties().stacksTo(64)));
    Item REMILIA_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(REMILIA_PLUSH, new Item.Properties().stacksTo(64)));
    Item MISTIA_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(MISTIA_PLUSH, new Item.Properties().stacksTo(64)));
    Item MARISA_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(MARISA_PLUSH, new Item.Properties().stacksTo(64)));
    Item REIMU_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(REIMU_PLUSH, new Item.Properties().stacksTo(64)));
    Item BAMBOO_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(BAMBOO_PLUSH, new Item.Properties().stacksTo(64)));
    Item KAORUKO_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(KAORUKO_PLUSH, new Item.Properties().stacksTo(64)));
    Item BACKVOICE_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(BACKVOICE_PLUSH, new Item.Properties().stacksTo(64)));
    Item BIANTWIN_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(BIANTWIN_PLUSH, new Item.Properties().stacksTo(64)));
    Item CANYUESAMA_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(CANYUESAMA_PLUSH, new Item.Properties().stacksTo(64)));
    Item DIO_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(DIO_PLUSH, new Item.Properties().stacksTo(64)));
    Item FUSHIMI_KONIRO_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(FUSHIMI_KONIRO_PLUSH, new Item.Properties().stacksTo(64)));
    Item GUANZHEQWQ_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(GUANZHEQWQ_PLUSH, new Item.Properties().stacksTo(64)));
    Item HAIMAN233_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(HAIMAN233_PLUSH, new Item.Properties().stacksTo(64)));
    Item LENGXIAOCN_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(LENGXIAOCN_PLUSH, new Item.Properties().stacksTo(64)));
    Item LICRAFTLQ_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(LICRAFTLQ_PLUSH, new Item.Properties().stacksTo(64)));
    Item LUOYERUOSHUI_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(LUOYERUOSHUI_PLUSH, new Item.Properties().stacksTo(64)));
    Item MIFAN520_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(MIFAN520_PLUSH, new Item.Properties().stacksTo(64)));
    Item NONE_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(NONE_PLUSH, new Item.Properties().stacksTo(64)));
    Item OTITH_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(OTITH_PLUSH, new Item.Properties().stacksTo(64)));
    Item THEF0RS4KEN_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(THEF0RS4KEN_PLUSH, new Item.Properties().stacksTo(64)));
    Item TOMATO_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(TOMATO_PLUSH, new Item.Properties().stacksTo(64)));
    Item XIAO_HEI_HAND_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(XIAO_HEI_HAND_PLUSH, new Item.Properties().stacksTo(64)));
    Item XIAOZHANQWQ_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(XIAOZHANQWQ_PLUSH, new Item.Properties().stacksTo(64)));
    Item ALLINTOKYO_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(ALLINTOKYO_PLUSH, new Item.Properties().stacksTo(64)));
    Item AQIONG_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(AQIONG_PLUSH, new Item.Properties().stacksTo(64)));
    Item HAOZI_PLUSH_ITEM = SREItems.registerBlock(new SREPlushItem(HAOZI_PLUSH, new Item.Properties().stacksTo(64)));
    Item LIANGJIE_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(LIANGJIE_PLUSH, new Item.Properties().stacksTo(64)));
    Item CUSTOM_PLAYER_PLUSH_ITEM = SREItems
            .registerBlock(new SREPlushItem(CUSTOM_PLAYER_PLUSH, new Item.Properties().stacksTo(64)));
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
                    XIAO_HEI_HAND_PLUSH, XIAOZHANQWQ_PLUSH, ALLINTOKYO_PLUSH, MILK_DRAGON_PLUSH,
                    AQIONG_PLUSH, HAOZI_PLUSH, LIANGJIE_PLUSH, CUSTOM_PLAYER_PLUSH }));

    public static Block registerBlock(String id, Block block) {
        return onlyRegisterBlock(Noellesroles.id(id), block);
    }

    static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FUMO_BLOCK_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.starrailexpress.fumo_blocks")).icon(() -> {
                    return new ItemStack(BAKA_PLUSH.asItem());
                })
                .build());

        // 修改物品分类
        ItemGroupEvents.modifyEntriesEvent(FUMO_BLOCK_CREATIVE_GROUP)
                .register((itemGroup) -> {
                    itemGroup.accept(MILK_DRAGON_PLUSH_ITEM);
                    itemGroup.accept(BAKA_PLUSH_ITEM);
                    itemGroup.accept(FURANDORU_PLUSH_ITEM);
                    itemGroup.accept(REMILIA_PLUSH_ITEM);
                    itemGroup.accept(MISTIA_PLUSH_ITEM);
                    itemGroup.accept(MARISA_PLUSH_ITEM);
                    itemGroup.accept(REIMU_PLUSH_ITEM);
                    itemGroup.accept(BAMBOO_PLUSH_ITEM);
                    itemGroup.accept(KAORUKO_PLUSH_ITEM);
                    itemGroup.accept(BACKVOICE_PLUSH_ITEM);
                    itemGroup.accept(BIANTWIN_PLUSH_ITEM);
                    itemGroup.accept(CANYUESAMA_PLUSH_ITEM);
                    itemGroup.accept(DIO_PLUSH_ITEM);
                    itemGroup.accept(FUSHIMI_KONIRO_PLUSH_ITEM);
                    itemGroup.accept(GUANZHEQWQ_PLUSH_ITEM);
                    itemGroup.accept(HAIMAN233_PLUSH_ITEM);
                    itemGroup.accept(LENGXIAOCN_PLUSH_ITEM);
                    itemGroup.accept(LICRAFTLQ_PLUSH_ITEM);
                    itemGroup.accept(LUOYERUOSHUI_PLUSH_ITEM);
                    itemGroup.accept(MIFAN520_PLUSH_ITEM);
                    itemGroup.accept(NONE_PLUSH_ITEM);
                    itemGroup.accept(OTITH_PLUSH_ITEM);
                    itemGroup.accept(THEF0RS4KEN_PLUSH_ITEM);
                    itemGroup.accept(TOMATO_PLUSH_ITEM);
                    itemGroup.accept(XIAO_HEI_HAND_PLUSH_ITEM);
                    itemGroup.accept(XIAOZHANQWQ_PLUSH_ITEM);
                    itemGroup.accept(ALLINTOKYO_PLUSH_ITEM);
                    itemGroup.accept(AQIONG_PLUSH_ITEM);
                    itemGroup.accept(HAOZI_PLUSH_ITEM);
                    itemGroup.accept(LIANGJIE_PLUSH_ITEM);
                    itemGroup.accept(CUSTOM_PLAYER_PLUSH_ITEM);
                });
        blockEntityRegistrar.registerEntries();
    }

    public static Block onlyRegisterBlock(ResourceLocation res, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, res, block);
    }
}
