package org.agmas.noellesroles.game.modes.echo.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.agmas.noellesroles.game.modes.echo.map.EchoChunkPreparation;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;

import java.util.Comparator;
import java.util.UUID;

/** 每次准入独占UUID的FULL票据；不使用永久强制区块，也绝不移除其它系统的票据。 */
final class EchoChunkTickets {
    private static final TicketType<UUID> TYPE=TicketType.create("echo_runtime",Comparator.<UUID>naturalOrder());
    private EchoChunkTickets() { }
    static EchoChunkPreparation acquire(ServerLevel level,EchoMapConfig.Box area,long now) {
        UUID owner=UUID.randomUUID();
        return new EchoChunkPreparation(area,new EchoChunkPreparation.Access() {
            @Override public void request(EchoChunkPreparation.Chunk chunk) {
                // 半径0对应FULL(33)，只保留可安全读写的区块，不强制整图实体tick。
                level.getChunkSource().addRegionTicket(TYPE,new ChunkPos(chunk.x(),chunk.z()),0,owner);
            }
            @Override public boolean loaded(EchoChunkPreparation.Chunk chunk) {
                return level.getChunkSource().getChunkNow(chunk.x(),chunk.z())!=null;
            }
            @Override public void release(EchoChunkPreparation.Chunk chunk) {
                level.getChunkSource().removeRegionTicket(TYPE,new ChunkPos(chunk.x(),chunk.z()),0,owner);
            }
        },now);
    }
}
