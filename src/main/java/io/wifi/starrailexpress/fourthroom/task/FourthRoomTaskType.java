package io.wifi.starrailexpress.fourthroom.task;

public enum FourthRoomTaskType {
    DRINK_WATER("drink_water", "Drink water somewhere outside the room.", 1, 3),
    USE_TOILET("use_toilet", "Find a toilet and stay there for a moment.", 1, 4),
    FIND_NOTE("find_note", "Search a room for a hidden sticky note.", 2, 5),
    PHOTOGRAPH_BLOCK("photograph_block", "Photograph a target block chosen by the host.", 2, 5);

    private final String id;
    private final String description;
    private final int minReward;
    private final int maxReward;

    FourthRoomTaskType(String id, String description, int minReward, int maxReward) {
        this.id = id;
        this.description = description;
        this.minReward = minReward;
        this.maxReward = maxReward;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public int minReward() {
        return minReward;
    }

    public int maxReward() {
        return maxReward;
    }

    public static FourthRoomTaskType byId(String id) {
        for (FourthRoomTaskType value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }
}