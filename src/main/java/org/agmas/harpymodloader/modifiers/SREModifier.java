package org.agmas.harpymodloader.modifiers;

import io.wifi.starrailexpress.api.SRERole;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.agmas.harpymodloader.Harpymodloader;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class SREModifier {

    public ResourceLocation identifier;
    public int color;
    public ArrayList<SRERole> cannotBeAppliedTo;
    public ArrayList<SRERole> canOnlyBeAppliedTo;
    public boolean killerOnly;
    public boolean civilianOnly;
    public Consumer<ServerPlayer> serverTickEvent = null;
    public Consumer<Player> clientTickEvent = null;

    public SREModifier setClientGameTickEvent(Consumer<Player> event) {
        this.clientTickEvent = event;
        return this;
    };

    public SREModifier setServerGameTickEvent(Consumer<ServerPlayer> event) {
        this.serverTickEvent = event;
        return this;
    };

    public void autoGameTickEvent(Player player) {
        if (player instanceof ServerPlayer sl) {
            this.serverGameTickEvent(sl);
        } else {
            this.clientGameTickEvent(player);
        }
    }

    public void clientGameTickEvent(Player player) {
        if (clientTickEvent != null)
            clientTickEvent.accept(player);
    }

    public void serverGameTickEvent(ServerPlayer player) {
        if (serverTickEvent != null)
            serverTickEvent.accept(player);
    }

    public SREModifier setMax(int count) {
        Harpymodloader.MODIFIER_MAX.put(this.identifier, count);
        return this;
    };

    public SREModifier(ResourceLocation identifier, int color, ArrayList<SRERole> cannotBeAppliedTo,
            ArrayList<SRERole> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        this.identifier = identifier;
        this.color = color;
        this.cannotBeAppliedTo = cannotBeAppliedTo;
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
        this.killerOnly = killerOnly;
        this.civilianOnly = civilianOnly;
    }

    public ResourceLocation identifier() {
        return this.identifier;
    }

    public MutableComponent getName() {
        return getName(false);
    }

    public MutableComponent getName(boolean color) {
        // Log.info(LogCategory.GENERAL,
        // Language.getInstance().hasTranslation("announcement.star.modifier." +
        // identifier().getPath())+"");
        if (!Language.getInstance().has("announcement.star.modifier." + identifier().toLanguageKey())
                && Language.getInstance().has("announcement.star.modifier." + identifier().getPath())) {
            return Component.translatable("announcement.star.modifier." + identifier().getPath());
        }
        final MutableComponent text = Component
                .translatable("announcement.star.modifier." + identifier().toLanguageKey());
        if (color) {
            return text.withColor(color());
        }
        return text;
    }

    public int color() {
        return this.color;
    }

    public ArrayList<SRERole> canOnlyBeAppliedTo() {
        return canOnlyBeAppliedTo;
    }

    public ArrayList<SRERole> cannotBeAppliedTo() {
        return cannotBeAppliedTo;
    }

    public void setCannotBeAppliedTo(ArrayList<SRERole> cannotBeAppliedTo) {
        this.cannotBeAppliedTo = cannotBeAppliedTo;
    }

    public void setCanOnlyBeAppliedTo(ArrayList<SRERole> canOnlyBeAppliedTo) {
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
    }
}
