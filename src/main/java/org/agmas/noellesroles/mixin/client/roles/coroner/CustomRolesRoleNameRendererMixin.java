package org.agmas.noellesroles.mixin.client.roles.coroner;

import java.awt.Color;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.client.HarpymodloaderClient;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
@Mixin(RoleNameRenderer.class)
public abstract class CustomRolesRoleNameRendererMixin {

    @Shadow
    private static float nametagAlpha;

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I", ordinal = 0))
    private static void b(Font renderer, @NotNull LocalPlayer lp, GuiGraphics context, DeltaTracker tickCounter,
            CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
            return;
        if (HarpymodloaderClient.hudRole != null) {
            if (SREClient.isPlayerSpectatingOrCreative()) {
                MutableComponent name = Harpymodloader.getRoleName(HarpymodloaderClient.hudRole);
                if (HarpymodloaderClient.modifiers != null) {
                    for (SREModifier modifier : HarpymodloaderClient.modifiers) {
                        name.append(
                                Component.literal(" [").append(modifier.getName()).append("]")
                                        .withColor(modifier.color));
                    }
                }
                // 死亡惩罚

                Player player = Minecraft.getInstance().player;
                int di_color = HarpymodloaderClient.hudRole.color();
                var deathPenalty = ModComponents.DEATH_PENALTY.get(player);
                boolean hasPenalty = false;
                if (deathPenalty != null)
                    hasPenalty = deathPenalty.hasPenalty();
                final var worldModifierComponent = WorldModifierComponent.KEY
                        .get(player.level());
                if (worldModifierComponent != null) {
                    if (worldModifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
                        var splitComponent = SplitPersonalityComponent.KEY.get(player);
                        if (splitComponent != null && !splitComponent.isDeath()) {
                            hasPenalty = true;
                        }
                    }
                }

                if (hasPenalty) {
                    name = Component.translatable("message.noellesroles.penalty.limit.role");
                    di_color = Color.RED.getRGB();
                }

                context.drawString(renderer, name, -renderer.width(name) / 2, 0,
                        di_color | (int) (nametagAlpha * 255.0F) << 24);
            }
        }
        if (NoellesrolesClient.hudTarget != null) {
            if (SREClient.gameComponent.isRole(Minecraft.getInstance().player.getUUID(), ModRoles.ATTENDANT)) {
                String room_name_ = "No Room";

                if (GameUtils.roomToPlayer.containsKey(NoellesrolesClient.hudTarget.getUUID())) {
                    int room_number = GameUtils.roomToPlayer.get(NoellesrolesClient.hudTarget.getUUID());
                    room_name_ = "Room " + room_number;
                }
                var room_name = Component.translatable("message.noellesroles.attendant.room_show",
                        Component.literal(room_name_).withStyle(ChatFormatting.GOLD));
                // NoellesrolesClient.hudTarget
                var _color = Color.MAGENTA.getRGB();

                context.drawString(renderer, room_name, -renderer.width(room_name) / 2, -20,
                        _color | (int) (nametagAlpha * 255.0F) << 24);
            }
        }
    }

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private static void b(Font renderer, @NotNull LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter,
            CallbackInfo ci, @Local Player target) {
        SREGameWorldComponent gameWorldComponent = SREClient.gameComponent;
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
        if (gameWorldComponent.getRole(target) != null) {
            NoellesrolesClient.hudTarget = target;
            HarpymodloaderClient.hudRole = gameWorldComponent.getRole(target);
            HarpymodloaderClient.modifiers = worldModifierComponent.getModifiers(target);
        } else {
            NoellesrolesClient.hudTarget = target;
            HarpymodloaderClient.hudRole = TMMRoles.CIVILIAN;
            HarpymodloaderClient.modifiers = null;
        }
    }
}
