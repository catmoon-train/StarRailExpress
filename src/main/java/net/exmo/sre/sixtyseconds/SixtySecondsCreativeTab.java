package net.exmo.sre.sixtyseconds;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

/**
 * 末日60秒模式的统一创造标签页：本模式全部物品与方块（物品经 ModItems、
 * 方块物品经 ModBlocks 的 registrar groups 参数）归入此页，图标为末日时钟。
 */
public final class SixtySecondsCreativeTab {
    public static final ResourceKey<CreativeModeTab> SIXTY_SECONDS_GROUP = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Noellesroles.id("sixty_seconds"));

    private SixtySecondsCreativeTab() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SIXTY_SECONDS_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.sixty_seconds"))
                .icon(() -> new ItemStack(org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CLOCK))
                .build());
    }
}
