package org.agmas.noellesroles.mixin.client.roles.thief;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Gui.class)
public abstract class ThiefHudMixin {
    @Shadow public abstract Font getFont();

}