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

package io.wifi.starrailexpress.plush;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.api.PlushApi;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.agmas.noellesroles.content.block.SREPlushItem;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 玩家当前「身份玩偶」的编码。用于像帽子一样按显示皮肤拥有者绑定。
 * <p>
 * 编码：空字符串表示无玩偶；否则为物品 ID，可选附加 {@code |p:玩家名} 或 {@code |t:贴图}。
 */
public final class PlushEquipmentIdentity {
    public static final String NONE = "";

    private final ResourceLocation itemId;
    private final @Nullable String profileName;
    private final @Nullable ResourceLocation texture;

    public PlushEquipmentIdentity(ResourceLocation itemId, @Nullable String profileName,
            @Nullable ResourceLocation texture) {
        this.itemId = itemId;
        this.profileName = profileName;
        this.texture = texture;
    }

    public static boolean isPlushItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SREPlushItem;
    }

    public static @Nullable PlushEquipmentIdentity fromStack(ItemStack stack) {
        if (!isPlushItem(stack)) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String profileName = null;
        ResourceLocation texture = stack.get(SREDataComponentTypes.TEXTURE);
        if (stack.has(DataComponents.PROFILE)) {
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            if (profile != null) {
                profileName = profile.name().orElse(null);
            }
        }
        return new PlushEquipmentIdentity(itemId, profileName, texture);
    }

    public static PlushEquipmentIdentity decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String itemPart = encoded;
        String profileName = null;
        ResourceLocation texture = null;
        int sep = encoded.indexOf('|');
        if (sep >= 0) {
            itemPart = encoded.substring(0, sep);
            String extra = encoded.substring(sep + 1);
            if (extra.startsWith("p:")) {
                profileName = extra.substring(2);
                if (profileName.isBlank()) {
                    profileName = null;
                }
            } else if (extra.startsWith("t:")) {
                texture = ResourceLocation.tryParse(extra.substring(2));
            }
        }
        ResourceLocation itemId = ResourceLocation.tryParse(itemPart);
        if (itemId == null) {
            return null;
        }
        return new PlushEquipmentIdentity(itemId, profileName, texture);
    }

    public String encode() {
        if (itemId == null) {
            return NONE;
        }
        String base = itemId.toString();
        if (texture != null) {
            return base + "|t:" + texture;
        }
        if (profileName != null && !profileName.isBlank()) {
            return base + "|p:" + profileName;
        }
        return base;
    }

    public ItemStack toStack() {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (texture != null) {
            stack.set(SREDataComponentTypes.TEXTURE, texture);
        }
        if (profileName != null && !profileName.isBlank()) {
            UUID profileId = UUID.nameUUIDFromBytes(("sre-plush:" + profileName).getBytes(StandardCharsets.UTF_8));
            stack.set(DataComponents.PROFILE, new ResolvableProfile(new GameProfile(profileId, profileName)));
        }
        return stack;
    }

    public ResourceLocation itemId() {
        return itemId;
    }

    public boolean isNamedSponsorPath() {
        if (itemId == null || !PlushApi.PLUSH_NAMESPACE.equals(itemId.getNamespace())) {
            return false;
        }
        return itemId.getPath().endsWith(PlushApi.PLUSH_SUFFIX)
                && !itemId.getPath().equals("custom_player_plush");
    }

    public String sponsorSkinName() {
        if (!isNamedSponsorPath()) {
            return "";
        }
        String path = itemId.getPath();
        return path.substring(0, path.length() - PlushApi.PLUSH_SUFFIX.length());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlushEquipmentIdentity that)) {
            return false;
        }
        return Objects.equals(itemId, that.itemId)
                && Objects.equals(profileName, that.profileName)
                && Objects.equals(texture, that.texture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, profileName, texture);
    }
}
