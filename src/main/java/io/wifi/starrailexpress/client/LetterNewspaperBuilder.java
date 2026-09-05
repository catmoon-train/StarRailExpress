package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.NewspaperScreen;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.content.item.LetterItem;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LetterNewspaperBuilder {

    public static void init() {
        LetterItem.clientUiOpener = LetterNewspaperBuilder::openNewspaperScreen;
    }

    private static void openNewspaperScreen() {
        if (!isClientReady()) {
            return;
        }

        MutableComponent body = buildLetterBody();
        String mapNameKey = getMapNameKey();
        Component title = Component.translatable(
                "sre.letter.tip.header.title",
                Component.translatable(mapNameKey));
        Component subtitle = Component.translatable(
                "sre.letter.tip.header.subtitle",
                Component.translatable(mapNameKey));

        NewspaperScreen screen = new NewspaperScreen(List.of(body), title, subtitle);
        Minecraft.getInstance().setScreen(screen);
    }

    private static boolean isClientReady() {
        return SREClient.areaComponent != null
                && SREClient.cached_player != null
                && SREClient.areaComponent.areasSettings != null;
    }

    private static String getMapNameKey() {
        String key = SREClient.areaComponent.mapDisplayName;
        return key == null ? "unknown" : key;
    }

    private static MutableComponent buildLetterBody() {
        MutableComponent body = Component.literal("");
        body.append(buildDescription());
        body.append("\n\n");

        Component meeting = buildMeetingDescription(SREClient.areaComponent);
        if (meeting != null) {
            body.append(meeting);
            body.append("\n\n");
        }

        if (SREClient.getCachedPlayerRole() != null) {
            body.append(buildRoleSection());
            body.append("\n\n");
        }

        body.append(Component.translatable("sre.letter.tip.body.end"));
        body.append("\n\n");
        body.append(Component.translatable("sre.letter.tip.body.end.drop_tip")
                .withStyle(ChatFormatting.DARK_GRAY));
        return body;
    }

    private static Component buildDescription() {
        AreasWorldComponent area = SREClient.areaComponent;
        Component areaTip = buildAreaTip(area);
        return Component.translatable("sre.letter.tip.body.head", areaTip);
    }

    private static Component buildAreaTip(AreasWorldComponent area) {
        MutableComponent tip = Component.literal("");

        tip.append(area.areasSettings.canJump
                ? Component.translatable("announcement.star.tip.can_jump")
                : Component.translatable("announcement.star.tip.cant_jump"));

        tip.append(Component.translatable("announcement.star.tip.split"))
                .append(buildWaterTip(area.areasSettings));

        tip.append(Component.translatable("announcement.star.tip.split"))
                .append(area.areasSettings.enableOxygenDrowning
                        ? Component.translatable("announcement.star.tip.will_drown")
                        : Component.translatable("announcement.star.tip.wont_drown"));

        return tip;
    }

    private static Component buildWaterTip(AreasSettings settings) {
        boolean canSwim = settings.canSwim || settings.canJump;
        boolean fullSwim = canSwim && settings.canSimpleSwim
                && settings.canUnderWater && settings.allowInDeepWater;
        boolean noSwim = !settings.canSimpleSwim
                && !settings.canUnderWater && !settings.allowInDeepWater;

        if (fullSwim) {
            return Component.translatable("announcement.star.tip.can_swim");
        }
        if (noSwim) {
            return Component.translatable("announcement.star.tip.cant_swim");
        }
        if (settings.canSimpleSwim) {
            return Component.translatable("announcement.star.tip.can_simple_swim");
        }
        if (!settings.allowInDeepWater || !settings.canSimpleSwim) {
            return Component.translatable("announcement.star.tip.cant_underwater");
        }
        if (!settings.canUnderWater) {
            return Component.translatable("announcement.star.tip.cant_be_eye_underwater");
        }
        if (!canSwim) {
            return Component.translatable("announcement.star.tip.cant_swim_up");
        }
        // 剩余情况：canSimpleSwim=false, canUnderWater=true, allowInDeepWater=true,
        // canSwim=true
        return Component.translatable("announcement.star.tip.default");
    }

    private static Component buildRoleSection() {
        var role = SREClient.getCachedPlayerRole();
        Component roleName = RoleUtils.getRoleNameWithColor(role);
        Component clickHint = Component.translatable("sre.letter.tip.body.role.click_to_show")
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("sre.letter.tip.body.role.click_to_show.hover_text")
                                        .withStyle(ChatFormatting.GREEN)))
                        .withColor(ChatFormatting.DARK_RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/sre:client screen role_introduction")));

        return Component.translatable(
                "sre.letter.tip.body.role",
                roleName,
                clickHint,
                NoellesrolesClient.roleIntroClientBind.getTranslatedKeyMessage().copy()
                        .withStyle(ChatFormatting.GOLD));
    }

    @Nullable
    private static Component buildMeetingDescription(AreasWorldComponent area) {
        if (area == null || area.areasSettings == null) {
            return null;
        }
        AreasSettings settings = area.areasSettings;
        if (!settings.meetingEnabled) {
            return null;
        }

        MutableComponent meetingType = Component.literal("");
        MutableComponent meetingResultMsg = Component.literal("");
        MutableComponent startCooldownMsg = Component.literal("");

        // 身体会议
        if (settings.bodyMeetingEnabled) {
            Component bodyMeeting = Component.translatable("meeting.sre.body_meeting");
            appendMeetingType(meetingType, bodyMeeting);
            startCooldownMsg.append("\n").append(Component.translatable(
                    "meeting.sre.entry.is_comming",
                    bodyMeeting,
                    Component.literal(String.valueOf(settings.meetingStartCooldown))));
            meetingResultMsg.append("\n").append(Component.translatable(
                    "meeting.sre.entry.processor",
                    bodyMeeting,
                    buildProcessorType(settings, false)));
        }

        // 铃铛会议 / 紧急会议
        Component secondaryMeeting = Component.translatable(
                settings.bellMeetingEnabled ? "meeting.sre.bell_meeting" : "meeting.sre.emergency_meeting");
        appendMeetingType(meetingType, secondaryMeeting);

        if (settings.bellMeetingEnabled) {
            startCooldownMsg.append("\n").append(Component.translatable(
                    "meeting.sre.entry.is_comming",
                    secondaryMeeting,
                    Component.literal(String.valueOf(settings.bellMeetingStartCooldown))));
        }

        meetingResultMsg.append("\n").append(Component.translatable(
                "meeting.sre.entry.processor",
                secondaryMeeting,
                buildProcessorType(settings, true)));

        return Component.translatable(
                "meeting.sre.description",
                meetingType,
                meetingResultMsg,
                startCooldownMsg);
    }

    private static void appendMeetingType(MutableComponent target, Component type) {
        if (target.getSiblings().isEmpty()) {
            target.append(type);
        } else {
            target = Component.translatable("meeting.sre.entry.and", target.copy(), type);
        }
    }

    private static Component buildProcessorType(AreasSettings settings, boolean emergency) {
        if (!settings.meetingVoteEnabled) {
            return Component.translatable("meeting.sre.entry.no_vote")
                    .withStyle(ChatFormatting.BLACK);
        }

        if (emergency && settings.emergencyMeetingVoteEnabled == TrueFalseResult.FALSE) {
            return Component.translatable("meeting.sre.entry.no_vote")
                    .withStyle(ChatFormatting.BLACK);
        }

        Component normalResult = switch (settings.meetingVoteProcessor) {
            case FORCE_KILL, KILL -> Component.translatable("meeting.sre.entry.kill")
                    .withStyle(ChatFormatting.DARK_RED);
            case GLOWING -> Component.translatable("meeting.sre.entry.glow")
                    .withStyle(ChatFormatting.DARK_AQUA);
            default -> Component.translatable("meeting.sre.entry.custom");
        };

        if (!emergency) {
            return normalResult;
        }

        if (settings.emergencyMeetingVoteEnabled == TrueFalseResult.PASS
                && !settings.meetingVoteEnabled) {
            return normalResult;
        }

        return switch (settings.emergencyMeetingVoteProcessor) {
            case FORCE_KILL, KILL -> Component.translatable("meeting.sre.entry.kill")
                    .withStyle(ChatFormatting.DARK_RED);
            case GLOWING -> Component.translatable("meeting.sre.entry.glow")
                    .withStyle(ChatFormatting.GOLD);
            case FUNCTION -> Component.translatable("meeting.sre.entry.custom");
            default -> normalResult;
        };
    }
}