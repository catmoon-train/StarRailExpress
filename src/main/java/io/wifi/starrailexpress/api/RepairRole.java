package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RepairRole extends NormalRole{
    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public RepairRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        setCanSeeTime(true);
        setCanSeeCoin(true);
        setMoodType(MoodType.FAKE);
        setCanUseInstinct(false);
        setCanAutoAddMoney(true);
        setCanBeRandomedByOtherRoles(false);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> objects = new ArrayList<>();
        objects.add(new ShopEntry(new ItemStack(Items.BARRIER), 0, ShopEntry.Type.TOOL){
            @Override
            public boolean canBuy(@NotNull Player player) {
                return false;
            }
        });
        return objects;
    }
}
