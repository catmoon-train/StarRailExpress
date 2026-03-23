package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class RoleAnnouncementTexts {
    public static final Map<ResourceLocation, RoleAnnouncementText> ROLE_ANNOUNCEMENT_TEXTS = new HashMap<>();

    public static RoleAnnouncementText registerRoleAnnouncementText(ResourceLocation roleId,
            RoleAnnouncementText role) {
        ROLE_ANNOUNCEMENT_TEXTS.put(roleId, role);
        LoggerFactory.getLogger(RoleAnnouncementTexts.class)
                .debug("Register Harpy Job: " + role.getId().getPath());
        return role;
    }

    public static RoleAnnouncementText getFromName(String name) {
        for (var t : ROLE_ANNOUNCEMENT_TEXTS.entrySet()) {
            if (t.getValue().getId().getPath().toLowerCase().equals(name.toLowerCase())) {
                return t.getValue();
            }
        }
        return null;
    }

    // 为现有职业注册公告文本
    static {
        // 为每个注册的角色创建对应的公告文本
        for (SRERole role : TMMRoles.ROLES.values()) {
            ResourceLocation roleId = role.identifier();
            registerRoleAnnouncementText(roleId, new RoleAnnouncementText(roleId, role.getColor()));
        }
    }

    public static class RoleAnnouncementText {
        public ResourceLocation getId() {
            if (id == null) {
                return ResourceLocation.fromNamespaceAndPath("", "");
            }
            return id;
        }

        private final ResourceLocation id;
        public final int colour;
        public final Component roleText;
        public final Component titleText;
        public final Component welcomeText;
        public final Function<Integer, Component> premiseText;
        public final Function<Integer, Component> goalText;
        public final Component winText;

        public RoleAnnouncementText(ResourceLocation id, int colour) {
            this.id = id;
            this.colour = colour;
            this.roleText = Component.translatable("announcement.star.role." + this.id.getPath().toLowerCase())
                    .withColor(this.colour);
            this.titleText = Component.translatable("announcement.star.title." + this.id.getPath().toLowerCase())
                    .withColor(this.colour);
            this.welcomeText = Component.translatable("announcement.star.welcome", this.roleText).withColor(0xF0F0F0);
            this.premiseText = (count) -> Component
                    .translatable(count == 1 ? "announcement.star.premise" : "announcement.star.premises", count);
            this.goalText = (count) -> Component
                    .translatable(
                            (count == 1 ? "announcement.star.goals." : "announcement.star.goals.")
                                    + this.id.getPath().toLowerCase(),
                            count)
                    .withColor(this.colour);
            this.winText = Component.translatable("announcement.star.win." + this.id.getPath().toLowerCase())
                    .withColor(this.colour);
        }

        public RoleAnnouncementText(String name, int colour) {
            this(ResourceLocation.tryParse(name), colour);
        }

        public Component getLoseText() {
            ResourceLocation killerRoleId = ResourceLocation.fromNamespaceAndPath("starrailexpress", "killer");
            ResourceLocation civilianRoleId = ResourceLocation.fromNamespaceAndPath("starrailexpress", "civilian");

            if ("killer".equals(this.id.getPath())) {
                RoleAnnouncementText civilianText = ROLE_ANNOUNCEMENT_TEXTS.get(civilianRoleId);
                return civilianText != null ? civilianText.winText
                        : Component.literal("Passengers Win!").withColor(0xFFFFFF);
            } else {
                RoleAnnouncementText killerText = ROLE_ANNOUNCEMENT_TEXTS.get(killerRoleId);
                return killerText != null ? killerText.winText : Component.literal("Killers Win!").withColor(0xFF0000);
            }
        }

        public @Nullable Component getEndText(GameUtils.@NotNull WinStatus status, Component winner,
                SREGameRoundEndComponent roundEnd) {
            return switch (status) {
                case NONE -> null;
                case PASSENGERS, TIME -> this.id.getPath().equals("killer") ? this.getLoseText() : this.winText;
                case KILLERS -> this.id.getPath().equals("killer") ? this.winText : this.getLoseText();
                case GAMBLER ->
                    Component.translatable("announcement.star.win.gambler", winner)
                            .withColor(new Color(128, 0, 128).getRGB());
                case RECORDER ->
                    Component.translatable("announcement.star.win.recorder", winner)
                            .withColor(new Color(128, 128, 128).getRGB());
                case NIAN_SHOU ->
                    Component.translatable("announcement.star.win.nianshou", winner)
                            .withColor(new Color(255, 69, 0).getRGB());
                case LOVERS ->
                    Component.translatable("announcement.star.win.lovers", winner)
                            .withColor(new Color(243, 138, 255).getRGB());
                case CANDLE_BEARER ->
                    Component.translatable("announcement.star.win.candle_bearer", winner)
                            .withColor(new Color(255, 210, 120).getRGB());
                case LOOSE_END -> {
                    ResourceLocation looseEndRoleId = ResourceLocation.fromNamespaceAndPath("starrailexpress",
                            "loose_end");
                    RoleAnnouncementText looseEndText = ROLE_ANNOUNCEMENT_TEXTS.get(looseEndRoleId);
                    int looseEndColor = looseEndText != null ? looseEndText.colour : 0x9F0000;
                    yield Component.translatable("announcement.star.win.loose_end", winner).withColor(looseEndColor);
                }
                case NO_PLAYER ->
                    Component.translatable("announcement.star.win.noplayer", winner).withColor(Color.LIGHT_GRAY.getRGB());
                case CUSTOM ->
                    Component.translatable("announcement.star.win." + roundEnd.CustomWinnerID, winner)
                            .withColor(roundEnd.CustomWinnerColor);
                case CUSTOM_COMPONENT ->
                    Component.literal("").withColor(roundEnd.CustomWinnerColor).append(roundEnd.CustomWinnerTitle);
                default -> Component.translatable("announcement.star.win.unknown", winner).withColor(Color.ORANGE.getRGB());
            };
        }
    }

    /**
     * 根据角色ID获取对应的公告文本
     * 
     * @param roleId 角色的ResourceLocation标识符
     * @return 对应的公告文本，如果不存在则返回null
     */
    public static @Nullable RoleAnnouncementText getRoleAnnouncementText(ResourceLocation roleId) {
        return ROLE_ANNOUNCEMENT_TEXTS.get(roleId);
    }

    /**
     * 根据角色ID获取对应的公告文本
     * 
     * @param roleId 角色ID字符串
     * @return 对应的公告文本，如果不存在则返回null
     */
    public static @Nullable RoleAnnouncementText getRoleAnnouncementText(String roleId) {
        return ROLE_ANNOUNCEMENT_TEXTS.get(ResourceLocation.tryParse(roleId));
    }

    // 保留原有的静态常量访问方法以兼容旧代码
    public static final RoleAnnouncementText BLANK = new RoleAnnouncementText("", 0xFFFFFF);
    public static final RoleAnnouncementText CIVILIAN = getRoleAnnouncementText(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "civilian")) != null
                    ? getRoleAnnouncementText(ResourceLocation.fromNamespaceAndPath("starrailexpress", "civilian"))
                    : registerRoleAnnouncementText(
                            ResourceLocation.fromNamespaceAndPath("starrailexpress", "civilian"),
                            new RoleAnnouncementText(
                                    ResourceLocation.fromNamespaceAndPath("starrailexpress", "civilian"), 0x36E51B));
    public static final RoleAnnouncementText VIGILANTE = getRoleAnnouncementText(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "vigilante")) != null
                    ? getRoleAnnouncementText(ResourceLocation.fromNamespaceAndPath("starrailexpress", "vigilante"))
                    : registerRoleAnnouncementText(
                            ResourceLocation.fromNamespaceAndPath("starrailexpress", "vigilante"),
                            new RoleAnnouncementText(
                                    ResourceLocation.fromNamespaceAndPath("starrailexpress", "vigilante"),
                                    Color.CYAN.getRGB()));

    public static final RoleAnnouncementText KILLER = getRoleAnnouncementText(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "killer")) != null
                    ? getRoleAnnouncementText(ResourceLocation.fromNamespaceAndPath("starrailexpress", "killer"))
                    : registerRoleAnnouncementText(
                            ResourceLocation.fromNamespaceAndPath("starrailexpress", "killer"),
                            new RoleAnnouncementText(
                                    ResourceLocation.fromNamespaceAndPath("starrailexpress", "killer"), 0xC13838));
    public static final RoleAnnouncementText LOOSE_END = getRoleAnnouncementText(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "loose_end")) != null
                    ? getRoleAnnouncementText(ResourceLocation.fromNamespaceAndPath("starrailexpress", "loose_end"))
                    : registerRoleAnnouncementText(
                            ResourceLocation.fromNamespaceAndPath("starrailexpress", "loose_end"),
                            new RoleAnnouncementText(
                                    ResourceLocation.fromNamespaceAndPath("starrailexpress", "loose_end"),
                                    0x9F0000));
}