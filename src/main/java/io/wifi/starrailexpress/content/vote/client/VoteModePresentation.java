package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.VoteOption;
import net.minecraft.network.chat.Component;

/** Client-side names and descriptions for known game-mode vote result ids. */
public final class VoteModePresentation {
    private VoteModePresentation() {}

    public static Component name(VoteOption option) {
        return name(option.resultId(), option.display());
    }

    public static Component name(String modeId, Component fallback) {
        String path = path(modeId);
        if (path.isBlank()) return fallback;
        return Component.translatableWithFallback("gui.sre.vote_flow.mode_name." + path, fallback.getString());
    }

    public static Component description(VoteOption option) {
        Component fallback = option.description() == null
                ? Component.translatable("gui.sre.vote_flow.mode_fallback")
                : option.description();
        String path = path(option.resultId());
        if (path.isBlank()) return fallback;
        return Component.translatableWithFallback("gui.sre.vote_flow.mode_description." + path,
                fallback.getString());
    }

    public static String path(String modeId) {
        if (modeId == null || modeId.isBlank()) return "";
        String value = modeId.startsWith("mode:") ? modeId.substring("mode:".length()) : modeId;
        int namespace = value.indexOf(':');
        return namespace >= 0 ? value.substring(namespace + 1) : value;
    }
}
