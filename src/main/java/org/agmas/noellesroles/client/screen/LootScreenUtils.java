package org.agmas.noellesroles.client.screen;

import io.wifi.StarRailExpressID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

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
}
