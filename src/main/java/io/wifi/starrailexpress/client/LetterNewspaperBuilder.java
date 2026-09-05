package io.wifi.starrailexpress.client;

import java.util.List;

import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.content.item.LetterItem;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

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

public class LetterNewspaperBuilder {

    public static void init() {
        LetterItem.clientUiOpener = () -> {

            if (SREClient.areaComponent == null || SREClient.cached_player == null
                    || SREClient.areaComponent.areasSettings == null)
                return;
            String mapNameKey = SREClient.areaComponent.mapDisplayName;
            if(mapNameKey == null){
                mapNameKey = "unknown";
            }
            MutableComponent body = Component.literal("");
            Component description = buildDescription();
            body.append(description);
            Component meeting = getMeetingDescription(SREClient.areaComponent);
            // meeting
            if (meeting != null) {
                body.append("\n\n").append(meeting);
            }
            // role
            if (SREClient.getCachedPlayerRole() != null) {
                Component roleName = RoleUtils.getRoleName(SREClient.getCachedPlayerRole());
                body.append("\n\n").append(Component.translatable("sre.letter.tip.body.role", roleName,
                        RoleUtils.getRoleDescription(SREClient.getCachedPlayerRole()),
                        Component.translatable("sre.letter.tip.body.role.click_to_show")
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("sre.letter.tip.body.role.click_to_show.hover_text")
                                                .withStyle(ChatFormatting.GREEN)))
                                        .withColor(ChatFormatting.GOLD)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                "/sre:client screen role_introduction"))),
                        NoellesrolesClient.roleIntroClientBind.getTranslatedKeyMessage().copy()
                                .withStyle(ChatFormatting.GOLD)));
            }
            // end
            body.append("\n\n").append(Component.translatable("sre.letter.tip.body.end"));
            body.append("\n\n").append(
                    Component.translatable("sre.letter.tip.body.end.drop_tip").withStyle(ChatFormatting.DARK_GRAY));
            NewspaperScreen screen = new NewspaperScreen(List.of(body),
                    Component.translatable("sre.letter.tip.header.title", Component.translatable(mapNameKey)),
                    Component.translatable("sre.letter.tip.header.subtitle", Component.translatable(mapNameKey)));
            Minecraft.getInstance().setScreen(screen);
        };
    }

    private static MutableComponent getAreaTip(AreasWorldComponent areaComponent) {
        final var message = Component.literal("");
        {
            message.append(areaComponent.areasSettings.canJump
                    ? Component.translatable("announcement.star.tip.can_jump")
                    : Component.translatable("announcement.star.tip.cant_jump"));
        }

        {
            message.append(Component.translatable("announcement.star.tip.split"))
                    .append(getWaterTip(areaComponent.areasSettings));
        }
        {
            message.append(Component.translatable("announcement.star.tip.split"))
                    .append(areaComponent.areasSettings.enableOxygenDrowning
                            ? Component.translatable("announcement.star.tip.will_drown")
                            : Component.translatable("announcement.star.tip.wont_drown"));

        }
        return message;
    }

    private static Component getWaterTip(AreasSettings areasSettings) {
        if ((areasSettings.canSwim || areasSettings.canJump) && areasSettings.canSimpleSwim
                && areasSettings.canUnderWater && areasSettings.allowInDeepWater) {
            return Component.translatable("announcement.star.tip.can_swim");
        } else if (!areasSettings.canSimpleSwim
                && !areasSettings.canUnderWater && !areasSettings.allowInDeepWater) {
            return Component.translatable("announcement.star.tip.cant_swim");
        } else if (areasSettings.canSimpleSwim) {

            return Component.translatable("announcement.star.tip.can_simple_swim");
        } else if (!areasSettings.allowInDeepWater || !areasSettings.canSimpleSwim) {
            return Component.translatable("announcement.star.tip.cant_underwater");
        } else if (!areasSettings.canUnderWater) {
            return Component.translatable("announcement.star.tip.cant_be_eye_underwater");
        } else if (!areasSettings.canSwim && !areasSettings.canJump) {
            return Component.translatable("announcement.star.tip.cant_swim_up");
        } else {
            // 处理剩余情况：canSimpleSwim=false, canUnderWater=true, allowInDeepWater=true,
            // (canSwim||canJump)=true
            return Component.translatable("announcement.star.tip.default");
        }
    }

    public static Component buildDescription() {
        if (SREClient.areaComponent == null || SREClient.cached_player == null
                || SREClient.areaComponent.areasSettings == null)
            return Component.translatable("sre.letter.tip.not_init");
        Component areaTip = getAreaTip(SREClient.areaComponent);
        return Component.translatable("sre.letter.tip.body.head", areaTip);
    }

    @Nullable
    public static Component getMeetingDescription(AreasWorldComponent areacca) {
        if (areacca == null || areacca.areasSettings == null)
            return null;
        if (areacca.areasSettings.meetingEnabled) {
            Component meetingType = null;
            MutableComponent meetingResultMsg = Component.literal("");
            MutableComponent startCooldownMsg = Component.literal("");
            if (areacca.areasSettings.bodyMeetingEnabled) {
                meetingType = Component.translatable("meeting.sre.body_meeting").withStyle(ChatFormatting.RED);
                startCooldownMsg.append("\n").append(Component.translatable("meeting.sre.entry.is_comming",
                        Component.translatable("meeting.sre.body_meeting").withStyle(ChatFormatting.RED),
                        Component.literal(String.format("%d", areacca.areasSettings.meetingStartCooldown))
                                .withStyle(ChatFormatting.GOLD)));
                meetingResultMsg.append("\n").append(Component.translatable("meeting.sre.entry.processor",
                        Component.translatable("meeting.sre.body_meeting").withStyle(ChatFormatting.RED),
                        getProcessorType(areacca.areasSettings, false))
                        .withStyle(ChatFormatting.GOLD));

            }
            {
                meetingType = meetingType == null
                        ? Component
                                .translatable(
                                        areacca.areasSettings.bellMeetingEnabled ? "meeting.sre.bell_meeting"
                                                : "meeting.sre.emergency_meeting")
                                .withStyle(ChatFormatting.LIGHT_PURPLE)
                        : Component.translatable("meeting.sre.entry.and", meetingType,
                                Component
                                        .translatable(areacca.areasSettings.bellMeetingEnabled
                                                ? "meeting.sre.bell_meeting"
                                                : "meeting.sre.emergency_meeting")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                if (areacca.areasSettings.bellMeetingEnabled)
                    startCooldownMsg.append("\n").append(Component.translatable("meeting.sre.entry.is_comming",
                            Component.translatable("meeting.sre.bell_meeting").withStyle(ChatFormatting.LIGHT_PURPLE),
                            Component
                                    .literal(
                                            String.format("%d", areacca.areasSettings.bellMeetingStartCooldown))
                                    .withStyle(ChatFormatting.GOLD)));

                meetingResultMsg.append("\n").append(Component.translatable("meeting.sre.entry.processor",
                        Component.translatable(areacca.areasSettings.bellMeetingEnabled
                                ? "meeting.sre.bell_meeting"
                                : "meeting.sre.emergency_meeting").withStyle(ChatFormatting.LIGHT_PURPLE),
                        getProcessorType(areacca.areasSettings, true))
                        .withStyle(ChatFormatting.GOLD));
            }
            if (meetingType != null) {
                final var entryMeetingMessage = Component.translatable("meeting.sre.description",
                        Component.translatable(areacca.mapDisplayName).withStyle(ChatFormatting.GREEN),
                        meetingType, meetingResultMsg,
                        startCooldownMsg).withStyle(ChatFormatting.GOLD);
                return entryMeetingMessage;
            }
            // meeting.sre.start_game_broadcast
        }
        return null;
    }

    private static Component getProcessorType(AreasSettings areasSettings, boolean emergency) {
        boolean noVote = false;
        var result = Component.translatable("meeting.sre.entry.kill").withStyle(ChatFormatting.DARK_RED);
        if (!areasSettings.meetingVoteEnabled) {
            result = Component.translatable("meeting.sre.entry.no_vote").withStyle(ChatFormatting.BLACK);
            noVote = true;
        } else {
            switch (areasSettings.meetingVoteProcessor) {
                case DEFAULT:
                case FORCE_KILL:
                case KILL:
                    result = Component.translatable("meeting.sre.entry.kill").withStyle(ChatFormatting.DARK_RED);
                    break;
                case GLOWING:
                    result = Component.translatable("meeting.sre.entry.glow").withStyle(ChatFormatting.GOLD);
                    break;
                default:
                    result = Component.translatable("meeting.sre.entry.custom");
                    break;
            }
        }
        if (emergency) {
            if (areasSettings.emergencyMeetingVoteEnabled == TrueFalseResult.FALSE) {
                result = Component.translatable("meeting.sre.entry.no_vote").withStyle(ChatFormatting.BLACK);
            } else if (areasSettings.emergencyMeetingVoteEnabled == TrueFalseResult.PASS) {
                if (noVote) {
                    return result;
                }
            }
            {
                switch (areasSettings.emergencyMeetingVoteProcessor) {
                    case FORCE_KILL:
                    case KILL:
                        result = Component.translatable("meeting.sre.entry.kill").withStyle(ChatFormatting.DARK_RED);
                        break;
                    case FUNCTION:
                        result = Component.translatable("meeting.sre.entry.custom");
                        break;
                    case GLOWING:
                        result = Component.translatable("meeting.sre.entry.glow")
                                .withStyle(ChatFormatting.GOLD);
                        break;
                    case DEFAULT:
                    default:
                        break;
                }
            }
        }
        return result;
    }
}
