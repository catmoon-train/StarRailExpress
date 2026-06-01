package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.agmas.noellesroles.client.HotbarStorageMenu;

public class ModMenus {
    public static final ExtendedScreenHandlerType<HotbarStorageMenu, BlockPos> HOTBAR_STORAGE = Registry.register(
            BuiltInRegistries.MENU,
            SRE.id("dnf_hotbar_storage"),
            new ExtendedScreenHandlerType<>(HotbarStorageMenu::new, BlockPos.STREAM_CODEC.cast()));

    private ModMenus() {
    }

    public static void initialize() {
    }
}
