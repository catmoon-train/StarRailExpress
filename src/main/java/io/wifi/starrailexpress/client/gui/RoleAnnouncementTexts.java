package io.wifi.starrailexpress.client.gui;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.agmas.noellesroles.utils.RoleUtils;
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
    public static final RoleAnnouncementText DEFAULT = registerRoleAnnouncementText(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "inner_blank"),
            new RoleAnnouncementText(
                    ResourceLocation.fromNamespaceAndPath("starrailexpress", "inner_blank"), 0x36E51B));

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
        public final Component welcomeText;
        public final Function<Integer, Component> premiseText;
        public final Function<Integer, Component> goalText;
        public final Component winText;

        public RoleAnnouncementText(ResourceLocation id) {
            // 用于滚木
            this.id = id;
            this.colour = 0xffffff;
            this.roleText = Component.translatable("announcement.star.inner_blank");
            this.welcomeText = Component.translatable("announcement.star.welcome", this.roleText).withColor(0xF0F0F0);
            this.premiseText = (count) -> Component.translatable(
                    count == 1 ? "announcement.star.premise" : "announcement.star.premises", count);
            this.winText = Component
                    .translatable("announcement.star.win." + this.id.getPath().toLowerCase())
                    .withColor(this.colour);
            this.goalText =(count)-> Component.translatable("announcement.star.goals.inner_blank").withColor(this.colour);
        }

        public RoleAnnouncementText(ResourceLocation id, int colour) {
            this.id = id;
            this.colour = colour;
            // 检查自定义职业
            var r = TMMRoles.getRole(id);
            if (r == null) {
                if (id.getNamespace().equals("customrole")) {
                    var cd = io.wifi.starrailexpress.customrole.CustomRoleLoader.getCustomRoleData(id.getPath());
                    // 服务端不可用时，回退到客户端网络同步的数据
                    if (cd == null) {
                        cd = io.wifi.starrailexpress.client.network.CustomRoleClientNetwork.getSyncedRole(id.getPath());
                    }
                    if (cd != null && !cd.displayName.isEmpty()) {
                        this.roleText = Component.literal(cd.displayName).withColor(this.colour);
                        String goals = cd.goals.isEmpty() ? cd.description : cd.goals;
                        this.goalText = (count) -> Component.literal(goals).withColor(this.colour);
                        this.welcomeText = Component.translatable("announcement.star.welcome", this.roleText)
                                .withColor(0xF0F0F0);
                        this.premiseText = (count) -> Component.translatable(
                                count == 1 ? "announcement.star.premise" : "announcement.star.premises", count);
                        this.winText = Component
                                .translatable("announcement.star.win." + this.id.getPath().toLowerCase())
                                .withColor(this.colour);
                        return;
                    }
                }
                this.roleText = RoleUtils.getRoleNameWithColor(id);
                this.welcomeText = Component.translatable("announcement.star.welcome", this.roleText)
                        .withColor(0xF0F0F0);
                this.premiseText = (count) -> Component
                        .translatable(count == 1 ? "announcement.star.premise" : "announcement.star.premises", count);
                this.goalText = (count) -> Component.translatable("announcement.star.goals." + id.getPath())
                        .withColor(this.colour);
                this.winText = Component.translatable("announcement.star.win." + this.id.getPath())
                        .withColor(this.colour);
                return;
            }

            this.roleText = RoleUtils.getRoleNameWithColor(id);
            this.welcomeText = Component.translatable("announcement.star.welcome", this.roleText).withColor(0xF0F0F0);
            this.premiseText = (count) -> Component
                    .translatable(count == 1 ? "announcement.star.premise" : "announcement.star.premises", count);
            this.goalText = (count) -> r.getGoal().copy()
                    .withColor(this.colour);
            this.winText = Component.translatable("announcement.star.win." + this.id.getPath())
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

    public static final Component CIVILIAN_TITLE_TEXT = Component.translatable("announcement.star.title.civilian")
            .withColor(0x36E51B);
    public static final Component KILLER_TITLE_TEXT = Component.translatable("announcement.star.title.killer")
            .withColor(0xC13838);
    public static final Component LOOSE_END_TITLE_TEXT = Component.translatable("announcement.star.title.loose_end")
            .withColor(0x9F0000);
    public static final Component NEUTRAL_TITLE_TEXT = Component.translatable("announcement.star.title.neutral")
            .withColor(Color.YELLOW.getRGB());
    public static final Component VIGILANTE_TITLE_TEXT = Component.translatable("announcement.star.title.vigilante")
            .withColor(Color.CYAN.getRGB());
}