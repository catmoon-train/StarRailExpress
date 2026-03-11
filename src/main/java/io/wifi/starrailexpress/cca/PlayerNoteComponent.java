package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class PlayerNoteComponent implements RoleComponent {
    public static final ComponentKey<PlayerNoteComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("note"), PlayerNoteComponent.class);
    public final Player player;
    public String[] text = new String[]{"", "", "", ""};
    public boolean written = false;

    public PlayerNoteComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void reset() {
        this.text = new String[]{"", "", "", ""};
        this.written = false;
        this.sync();
    }
    @Override
    public void clear(){
        reset();
    }
    public void setNote(@NotNull String s, String s1, String s2, String s3) {
        this.text = new String[]{s, s1, s2, s3};
        this.written = !s.isEmpty() || !s1.isEmpty() || !s2.isEmpty() || !s3.isEmpty();
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putString("line1", this.text[0]);
        tag.putString("line2", this.text[1]);
        tag.putString("line3", this.text[2]);
        tag.putString("line4", this.text[3]);
        tag.putBoolean("written", this.written);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.text[0] = tag.getString("line1");
        this.text[1] = tag.getString("line2");
        this.text[2] = tag.getString("line3");
        this.text[3] = tag.getString("line4");
        this.written = tag.getBoolean("written");
    }
}