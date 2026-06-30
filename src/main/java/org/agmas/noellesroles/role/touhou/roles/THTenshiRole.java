package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class THTenshiRole extends TouhouRole {
    public final static ArrayList<ShopEntry> SHOP = new ArrayList<>();
    static {
        // 监察员的商店

        var displayer = Items.BARRIER.getDefaultInstance();
        displayer.set(DataComponents.ITEM_NAME,
                Component.translatable("gui.noellesroles.tenshi.cooldown_item")
                        .withStyle(ChatFormatting.RED));
        SHOP.add(new ShopEntry(displayer, 0, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                return false;
            }
        });
    }

    public THTenshiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        addFlag("th_misc");
        setVigilanteTeam(true);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(ModItems.SCARLET_PERCEPTION_SWORD.getDefaultInstance());
        return items;
    }

    public List<ShopEntry> getShopEntries() {
        return SHOP;
    }
}
