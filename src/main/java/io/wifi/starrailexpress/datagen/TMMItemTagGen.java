package io.wifi.starrailexpress.datagen;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Items;
import java.util.concurrent.CompletableFuture;

public class TMMItemTagGen extends FabricTagProvider.ItemTagProvider {

    public TMMItemTagGen(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider arg) {
        this.getOrCreateTagBuilder(TMMItemTags.GUNS)
                .add(TMMItems.REVOLVER)
                .add(TMMItems.DERRINGER);

        this.getOrCreateTagBuilder(TMMItemTags.PSYCHOSIS_ITEMS)

                .add( Items.AIR)
                .add(TMMItems.LETTER)
                .add(TMMItems.FIRECRACKER)
                .add(TMMItems.KNIFE)
                .add(TMMItems.REVOLVER)
                .add(TMMItems.GRENADE)
                .add(TMMItems.POISON_VIAL)
                .add(TMMItems.SCORPION)
                .add(TMMItems.LOCKPICK)
                .add(TMMItems.CROWBAR)
                .add(TMMItems.BODY_BAG);
    }
}
