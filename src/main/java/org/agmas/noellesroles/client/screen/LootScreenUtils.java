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

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class LootScreenUtils {
    public static ResourceLocation getItemResourceLocation(String itemName) {
        ResourceLocation ans = null;
        if (itemName.equals("coin")) {
            ans = StarRailExpressID.watheId("textures/font/coin.png");
        }
        else {
            ans = ResourceLocation.fromNamespaceAndPath("starrailexpress",
                    "textures/item/" +
                            itemName
                            + ".png");
        }
        boolean exists = Minecraft.getInstance().getResourceManager()
                .getResource(ans).isPresent();
        if (!exists) {
            int splitCounter = 0;
            for (char c : itemName.toCharArray())
                if (c == '/')
                    ++splitCounter;
            if (splitCounter > 2) {
                String totalPath = itemName.substring(itemName.indexOf('/') + 1);
                totalPath = totalPath.substring(totalPath.indexOf('/') + 1);
                int nameSpaceSplitIdx = totalPath.indexOf('/');
                String nameSpace = totalPath.substring(0, nameSpaceSplitIdx);
                String assetPath = totalPath.substring(nameSpaceSplitIdx + 1);
                ans = ResourceLocation.fromNamespaceAndPath(nameSpace, assetPath);
            }
        }
        return ans;
    }
    public static ResourceLocation getCoinResourceLocation() {
        return StarRailExpressID.watheId("textures/font/coin.png");
    }
    public static void openLootInfoScreen(Minecraft environment) {
        if (environment != null) {
            if (environment.screen instanceof LootInfoScreen) {
                Screen screen = environment.screen;
                environment.setScreen(screen);
            }
            else
                environment.setScreen(new LootInfoScreen());
        }
    }
    public static void renderPixelScaleSkinItem(int x, int y, int pixelSize, GuiGraphics guiGraphics,
                                                @NotNull ItemStack itemType, String skinName) {
        ItemStack skinItem = itemType.copy();
        skinItem.set(SREDataComponentTypes.SKIN, skinName);
        // 使用缩放渲染
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(pixelSize, pixelSize, 1.0f);
        guiGraphics.renderFakeItem(skinItem, 0, 0);
        poseStack.popPose();
    }
}
