package org.agmas.noellesroles.client;

import net.minecraft.network.chat.Component;

public record BroadcastMessageInfo(Component message, long destroyTime) {
}
