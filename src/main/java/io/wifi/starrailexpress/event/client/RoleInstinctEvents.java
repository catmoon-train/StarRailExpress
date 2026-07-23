package io.wifi.starrailexpress.event.client;

import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;

public class RoleInstinctEvents {
    @FunctionalInterface
    public interface InnerRoleInstinctFunction {
        /**
         * 直觉事件
         * 
         * @param client            客户端
         * @param viewer            看的人
         * @param target            被看的实体
         * @param isInstinctEnabled 是否启用直觉
         * @return
         */
        TrueFalseAndCustomResult<Integer> getInstinctHighlight(Minecraft client, LocalPlayer viewer, Entity target,
                boolean isInstinctEnabled);
    }

    public static class CustomRenderEvent<T> {
        public HashMap<T, ArrayList<InnerRoleInstinctFunction>> role_events = new HashMap<>();
        public HashMap<T, ArrayList<InnerRoleInstinctFunction>> role_been_seen_events = new HashMap<>();

        public ArrayList<InnerRoleInstinctFunction> getFunctions(T identifier) {
            return role_events.get(identifier);
        }

        public void register(T identifier, InnerRoleInstinctFunction consumer) {
            role_events.computeIfAbsent(identifier, (a) -> {
                return new ArrayList<InnerRoleInstinctFunction>();
            });
            role_events.get(identifier).add(consumer);
        }

        public boolean removeConsumer(ResourceLocation identifier) {
            if (role_events.containsKey(identifier)) {
                role_events.remove(identifier);
                return true;
            }
            return false;
        }
    }

    /**
     * 职业看其他人的高亮事件
     * 
     * @param identifier
     * @param consumer
     */
    public final static CustomRenderEvent<ResourceLocation> OBSERVER_HIGHLIGHT_EVENT = new CustomRenderEvent<>();
    
    /**
     * 职业被其他人看的高亮事件
     * 
     * @param identifier
     * @param consumer
     */
    public final static CustomRenderEvent<ResourceLocation> TARGET_HIGHLIGHT_EVENT = new CustomRenderEvent<>();
}