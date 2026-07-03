package pro.fazeclan.river.stupid_express.constants;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.index.TMMDescItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.role.arsonist.item.LighterItem;

import static io.wifi.starrailexpress.index.TMMItems.*;

public class SEItems {

    private static ItemRegistrar registrar = new ItemRegistrar(StupidExpress.MOD_ID);

    @SuppressWarnings("unchecked")
    public static final Item JERRY_CAN = registrar.create("jerry_can", new Item(new Item.Properties().stacksTo(1)), TOOLS_GROUP, SRE_ALL_GROUP);
    @SuppressWarnings("unchecked")
    public static final Item LIGHTER = registrar.create("lighter", new LighterItem(new Item.Properties().stacksTo(1)), WEAPONS_GROUP, SRE_ALL_GROUP);

    public static final TagKey<Item> DRINKS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(StupidExpress.MOD_ID, "drinks"));

    public static void init() {
        registrar.registerEntries();
        TMMDescItems.introItems.add(JERRY_CAN);
        TMMDescItems.introItems.add(LIGHTER);
    }

}
