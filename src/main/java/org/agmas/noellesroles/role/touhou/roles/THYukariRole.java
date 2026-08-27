package org.agmas.noellesroles.role.touhou.roles;

import org.agmas.noellesroles.handler.utils.THYukariPortalManager;
import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TouhouRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class THYukariRole extends TouhouRole {

    public void resetVariables() {
        THYukariPortalManager.reset();
    }

    public THYukariRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    public static void registerSkills() {
        RoleSkill.register(THMiscRoles.YAKUMO_YUKARI,
                RoleSkill.skill(SRE.id("yukari/place"), "skill.noellesroles.yakumo_yukari.place", (ctx) -> {
                    final var player = ctx.player();
                    if (THYukariPortalManager.PORTAL_POS_1 == null) {
                        player.displayClientMessage(Component
                                .translatable("message.noellesroles.yakumo_yukari.portal.place.failed.pos", 1)
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }

                    if (THYukariPortalManager.PORTAL_POS_2 == null) {
                        player.displayClientMessage(Component
                                .translatable("message.noellesroles.yakumo_yukari.portal.place.failed.pos", 2)
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    if (!THYukariPortalManager.createPortal(player.serverLevel(), THYukariPortalManager.PORTAL_POS_1,
                            THYukariPortalManager.PORTAL_POS_2)) {
                        player.displayClientMessage(Component
                                .translatable("message.noellesroles.yakumo_yukari.portal.place.failed")
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    player.displayClientMessage(Component
                            .translatable("message.noellesroles.yakumo_yukari.portal.place.success")
                            .withStyle(ChatFormatting.AQUA), true);
                    return true;
                }).showOnHud(true).recordReplay().noAnnouncement().cooldownSeconds(90).build(),
                RoleSkill.skill(SRE.id("yukari/break"), "skill.noellesroles.yakumo_yukari.break", (ctx) -> {
                    final var player = ctx.player();
                    if (!THYukariPortalManager.hasPortal()) {
                        player.displayClientMessage(Component
                                .translatable("message.noellesroles.yakumo_yukari.portal.break.failed")
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    THYukariPortalManager.removeAlivePortals(player.serverLevel());

                    player.displayClientMessage(Component
                            .translatable("message.noellesroles.yakumo_yukari.portal.break.success")
                            .withStyle(ChatFormatting.YELLOW), true);
                    return true;
                }).showOnHud(true).recordReplay().noAnnouncement().cooldownSeconds(5).build(),
                RoleSkill.skill(SRE.id("yukari/pos1"), "skill.noellesroles.yakumo_yukari.pos1", (ctx) -> {
                    final var player = ctx.player();
                    final var level = player.serverLevel();
                    final Vec3 position = player.blockPosition().getCenter();
                    if (!THYukariPortalManager.checkPortalPos(level, position)) {
                        player.displayClientMessage(Component
                                .translatable("message.noellesroles.yakumo_yukari.portal.pos.failed.not_vaild", 1,
                                        String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z))
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    THYukariPortalManager.PORTAL_POS_1 = position;
                    player.displayClientMessage(Component
                            .translatable("message.noellesroles.yakumo_yukari.portal.pos.success", 1,
                                    String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z))
                            .withStyle(ChatFormatting.GREEN), true);
                    return true;
                }).showOnHud(true).noAnnouncement().cooldownSeconds(5).build(),
                RoleSkill.skill(SRE.id("yukari/pos2"), "skill.noellesroles.yakumo_yukari.pos2", (ctx) -> {
                    final var player = ctx.player();
                    final var level = player.serverLevel();
                    final Vec3 position = player.blockPosition().getCenter();
                    if (!THYukariPortalManager.checkPortalPos(level, position)) {
                        player.displayClientMessage(Component
                                .translatable("message.noellesroles.yakumo_yukari.portal.pos.failed.not_vaild", 2,
                                        String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z))
                                .withStyle(ChatFormatting.RED), true);
                        return false;
                    }
                    THYukariPortalManager.PORTAL_POS_2 = position;
                    player.displayClientMessage(Component
                            .translatable("message.noellesroles.yakumo_yukari.portal.pos.success", 2,
                                    String.format("%.1f, %.1f, %.1f", position.x, position.y, position.z))
                            .withStyle(ChatFormatting.GREEN), true);
                    return true;
                }).showOnHud(true).noAnnouncement().cooldownSeconds(5).build());
    }
}
