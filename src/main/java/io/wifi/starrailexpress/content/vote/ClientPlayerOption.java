package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ClientPlayerOption(Component display, UUID uuid, Component description) implements VoteOption {
    @Override
    public Component display() {
        return display;
    }

    @Override
    public ResourceLocation typeId() {
        return ResourceLocation.withDefaultNamespace("player");
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public boolean isItem() {
        return false;
    }

    /**
     * Not used for client and server
     */
    @Override
    public String resultId() {
        return "";
    }
}