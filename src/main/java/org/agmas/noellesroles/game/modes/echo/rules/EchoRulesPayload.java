package org.agmas.noellesroles.game.modes.echo.rules;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.UUID;

/** 仅初次绑定和重连发送；规则不夹带在高频RoleData同步里。 */
public record EchoRulesPayload(UUID match,ResourceLocation dimension,EchoRuleConfig rules) implements CustomPacketPayload {
    public static final Type<EchoRulesPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles","echo/rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EchoRulesPayload> CODEC=StreamCodec.of((b,p) -> {
        b.writeUUID(p.match()); b.writeResourceLocation(p.dimension()); b.writeUtf(EchoRuleRepository.json(p.rules()),65536);
    },b -> {
        var match=b.readUUID(); var dimension=b.readResourceLocation();
        try { return new EchoRulesPayload(match,dimension,EchoRuleRepository.parse(b.readUtf(65536))); }
        catch(IOException invalid) { throw new IllegalArgumentException("无效回声规则快照",invalid); }
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
