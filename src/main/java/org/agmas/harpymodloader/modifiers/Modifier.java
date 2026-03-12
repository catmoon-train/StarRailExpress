package org.agmas.harpymodloader.modifiers;


import io.wifi.starrailexpress.api.Role;
import java.util.ArrayList;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public class Modifier {

    public ResourceLocation identifier;
    public int color;
    public ArrayList<Role> cannotBeAppliedTo;
    public ArrayList<Role> canOnlyBeAppliedTo;
    public boolean killerOnly;
    public boolean civilianOnly;

    public Modifier(ResourceLocation identifier, int color, ArrayList<Role> cannotBeAppliedTo, ArrayList<Role> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
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
        // Log.info(LogCategory.GENERAL, Language.getInstance().hasTranslation("announcement.star.modifier." + identifier().getPath())+"");
        if (!Language.getInstance().has("announcement.star.modifier." + identifier().toLanguageKey()) && Language.getInstance().has("announcement.star.modifier." + identifier().getPath())) {
            return Component.translatable("announcement.star.modifier." + identifier().getPath());
        }
        final MutableComponent text = Component.translatable("announcement.star.modifier." + identifier().toLanguageKey());
        if (color) {
            return text.withColor(color());
        }
        return text;
    }

    public int color() {
        return this.color;
    }

    public ArrayList<Role> canOnlyBeAppliedTo() {
        return canOnlyBeAppliedTo;
    }

    public ArrayList<Role> cannotBeAppliedTo() {
        return cannotBeAppliedTo;
    }

    public void setCannotBeAppliedTo(ArrayList<Role> cannotBeAppliedTo) {
        this.cannotBeAppliedTo = cannotBeAppliedTo;
    }

    public void setCanOnlyBeAppliedTo(ArrayList<Role> canOnlyBeAppliedTo) {
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
    }
}
