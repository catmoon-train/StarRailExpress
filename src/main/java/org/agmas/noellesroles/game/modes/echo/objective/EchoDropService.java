package org.agmas.noellesroles.game.modes.echo.objective;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 地面记录先于展示；达到展示上限或生成失败只延迟可视化，不删除实例或刷新寿命。 */
public final class EchoDropService {
    public static final String PARCEL_TAG="echo_parcel";
    private static final Set<UUID> SPAWNING=new HashSet<>();
    public static boolean spawning(UUID id) { return SPAWNING.contains(id); }
    public enum Kind { CRYSTAL, KEY, PACK, CARD, BLACK_CRYSTAL }
    public record Drop(UUID dropId,UUID instanceId,Kind kind,Vec3 pos,long expiresAt,String altar,UUID entityId) { }
    private final ServerLevel level;
    private final UUID matchId;
    private final Map<UUID,Drop> drops=new LinkedHashMap<>();
    public EchoDropService(ServerLevel level,UUID matchId) { this.level=level; this.matchId=matchId; }

    public Optional<Drop> spawn(UUID instance,Kind kind,Vec3 pos,long deadline,String altar) {
        if(drops.values().stream().anyMatch(d -> d.instanceId().equals(instance))) throw new IllegalStateException("重复地面实例");
        Drop drop=new Drop(UUID.randomUUID(),instance,kind,pos,deadline,altar,null);
        drops.put(drop.dropId(),drop);
        return Optional.of(drop);
    }
    /** 每秒最多补建16份，关键货物优先；非权威实体丢失后仍只补建原记录。 */
    public void refresh(long now) {
        for(Drop drop:List.copyOf(drops.values())) if(drop.entityId()!=null && missing(drop))
            drops.put(drop.dropId(),new Drop(drop.dropId(),drop.instanceId(),drop.kind(),drop.pos(),drop.expiresAt(),drop.altar(),null));
        int displayed=(int)drops.values().stream().filter(d -> d.entityId()!=null).count();
        int budget=16;
        var pending=drops.values().stream().filter(d -> d.entityId()==null && now<d.expiresAt())
                .sorted(Comparator.comparingInt(d -> d.kind()==Kind.KEY?0:d.kind()==Kind.BLACK_CRYSTAL?1:d.kind()==Kind.CRYSTAL?2:3)).toList();
        for(Drop drop:pending) {
            if(budget--<=0) break;
            if(!level.hasChunkAt(BlockPos.containing(drop.pos()))) continue;
            if(displayed>=256) {
                var victim=drops.values().stream().filter(d -> d.entityId()!=null && priority(d.kind())>priority(drop.kind()))
                        .max(Comparator.comparingInt(d -> priority(d.kind()))).orElse(null);
                if(victim==null) break;
                Entity entity=level.getEntity(victim.entityId()); if(entity!=null) entity.discard();
                drops.put(victim.dropId(),new Drop(victim.dropId(),victim.instanceId(),victim.kind(),victim.pos(),victim.expiresAt(),victim.altar(),null)); displayed--;
            }
            ArmorStand display=new ArmorStand(EntityType.ARMOR_STAND,level);
            display.setPos(drop.pos().x,drop.pos().y,drop.pos().z);
            display.setInvisible(true); display.setNoGravity(true); display.setInvulnerable(true); display.setSilent(true); display.setSmall(true);
            display.addTag(PARCEL_TAG); display.addTag("echo_match_"+matchId);
            display.setItemSlot(EquipmentSlot.HEAD,new ItemStack(switch(drop.kind()) {
                case KEY -> Items.TRIPWIRE_HOOK; case CRYSTAL -> Items.AMETHYST_CLUSTER; case BLACK_CRYSTAL -> Items.ECHO_SHARD; case PACK -> Items.BUNDLE; case CARD -> Items.PAPER;
            }));
            display.setCustomName(Component.translatable("echo.drop."+drop.kind().name().toLowerCase(Locale.ROOT))); display.setCustomNameVisible(true);
            SPAWNING.add(display.getUUID());
            try {
                if(level.addFreshEntity(display)) { drops.put(drop.dropId(),new Drop(drop.dropId(),drop.instanceId(),drop.kind(),drop.pos(),drop.expiresAt(),drop.altar(),display.getUUID())); displayed++; }
            } catch(RuntimeException failure) {
                display.discard(); org.agmas.noellesroles.Noellesroles.LOGGER.warn("回声展示生成失败，原实例等待补建 {}",drop.dropId(),failure);
            } finally { SPAWNING.remove(display.getUUID()); }
        }
    }
    private static int priority(Kind kind) { return switch(kind) { case KEY -> 0; case BLACK_CRYSTAL -> 1; case CRYSTAL -> 2; default -> 3; }; }
    public Optional<Drop> byEntity(UUID entityId) { return drops.values().stream().filter(d -> entityId.equals(d.entityId())).findFirst(); }
    public Optional<Drop> byId(UUID id) { return Optional.ofNullable(drops.get(id)); }
    public List<Drop> all() { return List.copyOf(drops.values()); }
    public boolean missing(Drop drop) { Entity entity=drop.entityId()==null?null:level.getEntity(drop.entityId()); return entity==null || !entity.isAlive(); }
    public void remove(UUID dropId) {
        Drop drop=drops.remove(dropId); if(drop==null || drop.entityId()==null) return;
        Entity entity=level.getEntity(drop.entityId()); if(entity!=null) entity.discard();
    }
    public void cleanup() { for(UUID drop:List.copyOf(drops.keySet())) remove(drop); }
}
