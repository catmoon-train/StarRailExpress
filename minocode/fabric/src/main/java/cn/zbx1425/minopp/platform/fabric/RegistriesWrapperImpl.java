package cn.zbx1425.minopp.platform.fabric;


import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.platform.GroupedItem;
import cn.zbx1425.minopp.platform.RegistriesWrapper;
import cn.zbx1425.minopp.platform.RegistryObject;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class RegistriesWrapperImpl implements RegistriesWrapper {

    @Override
    public void registerBlock(String id, RegistryObject<Block> block) {
        Registry.register(BuiltInRegistries.BLOCK, Mino.id(id), block.get());
    }

    @Override
    public void registerBlockAndItem(String id, RegistryObject<Block> block, ResourceKey<CreativeModeTab> tab) {
        Registry.register(BuiltInRegistries.BLOCK, Mino.id(id), block.get());
        final BlockItem blockItem = new BlockItem(block.get(), new Item.Properties());
        Registry.register(BuiltInRegistries.ITEM, Mino.id(id), blockItem);
        ItemGroupEvents.modifyEntriesEvent(tab).register(consumer -> consumer.accept(blockItem));
    }

    @Override
    public void registerItem(String id, RegistryObject<GroupedItem> item) {
        Registry.register(BuiltInRegistries.ITEM, Mino.id(id), item.get());
        ItemGroupEvents.modifyEntriesEvent(item.get().tabSupplier.get()).register(consumer -> consumer.accept(item.get()));
    }

    @Override
    public void registerBlockEntityType(String id, RegistryObject<? extends BlockEntityType<? extends BlockEntity>> blockEntityType) {
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Mino.id(id), blockEntityType.get());
    }

    @Override
    public void registerEntityType(String id, RegistryObject<? extends EntityType<? extends Entity>> entityType) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, Mino.id(id), entityType.get());
    }

    @Override
    public void registerSoundEvent(String id, SoundEvent soundEvent) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, Mino.id(id), soundEvent);
    }

    @Override
    public <T> void registerDataComponentType(String id, RegistryObject<DataComponentType<T>> componentType) {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mino.id(id), componentType.get());
    }
}