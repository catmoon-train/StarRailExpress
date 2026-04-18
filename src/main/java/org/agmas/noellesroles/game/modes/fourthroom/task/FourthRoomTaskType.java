package org.agmas.noellesroles.game.modes.fourthroom.task;

public enum FourthRoomTaskType {
    DRINK_WATER("drink_water", "task.fourth_room.drink_water.description", 1, 3),
    USE_TOILET("use_toilet", "task.fourth_room.use_toilet.description", 1, 4),
    FIND_NOTE("find_note", "task.fourth_room.find_note.description", 2, 5),
    PHOTOGRAPH_BLOCK("photograph_block", "task.fourth_room.photograph_block.description", 2, 5);

    private final String id;
    private final String descriptionKey;
    private final int minReward;
    private final int maxReward;

    FourthRoomTaskType(String id, String descriptionKey, int minReward, int maxReward) {
        this.id = id;
        this.descriptionKey = descriptionKey;
        this.minReward = minReward;
        this.maxReward = maxReward;
    }

    public String id() {
        return id;
    }

    public String descriptionKey() {
        return descriptionKey;
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