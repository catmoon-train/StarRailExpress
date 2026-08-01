/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.screen;


import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 模组 ScreenHandler 注册
 */
public class ModScreenHandlers {
    
    /**
     * 射命丸文传递界面的 ScreenHandler 类型
     * 使用 ExtendedScreenHandlerType 来传递目标玩家的 UUID
     */
    public static final ExtendedScreenHandlerType<PostmanScreenHandler, UUID> POSTMAN_SCREEN_HANDLER =
        Registry.register(
            BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "postman"),
            new ExtendedScreenHandlerType<>(
                (syncId, playerInventory, data) -> new PostmanScreenHandler(syncId, playerInventory, data),
                UUIDUtil.STREAM_CODEC.cast()
            )
        );
    
    /**
     * 探员审查界面的 ScreenHandler 类型
     * 使用 ExtendedScreenHandlerType 来传递目标玩家的 UUID
     */
    public static final ExtendedScreenHandlerType<DetectiveInspectScreenHandler, UUID> DETECTIVE_INSPECT_SCREEN_HANDLER =
        Registry.register(
            BuiltInRegistries.MENU,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "detective_inspect"),
            new ExtendedScreenHandlerType<>(
                (syncId, playerInventory, data) -> new DetectiveInspectScreenHandler(syncId, playerInventory, data),
                UUIDUtil.STREAM_CODEC.cast()
            )
        );
    // 
    /**
     * 初始化并注册所有 ScreenHandler
     */
    public static void init() {
    }
}