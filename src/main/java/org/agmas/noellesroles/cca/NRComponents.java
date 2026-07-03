package org.agmas.noellesroles.cca;

import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

/**
 * C4组件初始化器 - 注册CCA组件
 */
public class NRComponents implements EntityComponentInitializer, WorldComponentInitializer {

    public NRComponents() {}

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // 不需要实体组件
    }

    @Override
    public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        registry.register(C4BackComponent.KEY, C4BackComponent::new);
    }
}
