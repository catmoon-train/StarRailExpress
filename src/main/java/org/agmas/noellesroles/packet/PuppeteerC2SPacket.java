package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 傀儡师技能包
 * 用于客户端请求使用假人技能
 */
public record PuppeteerC2SPacket(Action action) implements CustomPacketPayload {
    
    public static final Type<PuppeteerC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "puppeteer_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PuppeteerC2SPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT.map(Action::fromId, Action::getId),
        PuppeteerC2SPacket::action,
        PuppeteerC2SPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public enum Action {
        USE_PUPPET(0),      // 使用假人技能
        RETURN_TO_BODY(1);  // 主动返回本体
        
        private final int id;
        
        Action(int id) {
            this.id = id;
        }
        
        public int getId() {
            return id;
        }
        
        public static Action fromId(int id) {
            return switch (id) {
                case 1 -> RETURN_TO_BODY;
                default -> USE_PUPPET;
            };
        }
    }
}