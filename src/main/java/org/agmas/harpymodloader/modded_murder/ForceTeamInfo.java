package org.agmas.harpymodloader.modded_murder;

/**
 * ForceTeamInfo
 */
public record ForceTeamInfo(int roleType, ForceTeamType type) {
    public static enum ForceTeamType{
        COMMAND,
        CARD,
        ROLE_WEIGHTS
    }
}
