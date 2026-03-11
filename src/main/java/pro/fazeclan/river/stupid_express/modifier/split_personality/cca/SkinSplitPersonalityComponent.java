package pro.fazeclan.river.stupid_express.modifier.split_personality.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.entity.player.Player;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.UUID;

public class SkinSplitPersonalityComponent implements AutoSyncedComponent {

    public static final ComponentKey<SkinSplitPersonalityComponent> KEY = ComponentRegistry
            .getOrCreate(StupidExpress.id("skin_split_personality"), SkinSplitPersonalityComponent.class);

    private final Player player;

    public UUID getSkinToAppearAs() {
        return skinToAppearAs;
    }

    public SkinSplitPersonalityComponent setSkinToAppearAs(UUID skinToAppearAs) {
        this.skinToAppearAs = skinToAppearAs;
        return this;
    }

    private UUID skinToAppearAs = null;

    public SkinSplitPersonalityComponent(Player player, UUID skinToAppearAs) {
        this.player = player;
        this.skinToAppearAs = skinToAppearAs;

    }

    public SkinSplitPersonalityComponent(Player player) {
        this.player = player;
    }

    public void clear() {
        skinToAppearAs = null;
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        if (compoundTag.contains("skin_to_appear_as")) {
            skinToAppearAs = compoundTag.getUUID("skin_to_appear_as");
        }
    }

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        if (skinToAppearAs != null) {
            compoundTag.putUUID("skin_to_appear_as", skinToAppearAs);
        }

    }

    public void sync() {
        KEY.sync(player);
    }
}
