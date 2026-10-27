package org.agmas.noellesroles.game.modes.echo.inventory;

import net.minecraft.nbt.*;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality;
import java.util.*;

/** 仅供本人S2C投影，不提供从客户端NBT重建权威库存的入口。 */
public final class EchoInventoryNbt {
    private EchoInventoryNbt() { }
    public static CompoundTag write(EchoInventory.Snapshot snapshot) {
        CompoundTag tag=new CompoundTag(); tag.putLong("revision",snapshot.revision()); tag.putBoolean("keyReserved",snapshot.keyReserved());
        ListTag slots=new ListTag();
        for(var slot:snapshot.slots()) {
            CompoundTag s=new CompoundTag(); if(slot.reservation()!=null) s.putUUID("reserved",slot.reservation());
            ListTag items=new ListTag();
            for(var item:slot.items()) {
                CompoundTag i=new CompoundTag(); i.putUUID("match",item.matchId()); i.putUUID("id",item.instanceId()); i.putUUID("origin",item.originEventId());
                i.putString("definition",item.definitionId()); if(item.quality()!=null) i.putString("quality",item.quality().name());
                if(snapshot.locks().containsKey(item.instanceId())) i.putUUID("lock",snapshot.locks().get(item.instanceId()));
                items.add(i);
            }
            s.put("items",items); slots.add(s);
        }
        tag.put("slots",slots); return tag;
    }
    public static EchoInventory.Snapshot read(CompoundTag tag) {
        List<EchoInventory.Slot> slots=new ArrayList<>(); Map<UUID,UUID> locks=new HashMap<>(); ListTag values=tag.getList("slots",Tag.TAG_COMPOUND);
        for(int n=0;n<6;n++) {
            CompoundTag s=n<values.size()?values.getCompound(n):new CompoundTag(); List<EchoResourceInstance> items=new ArrayList<>();
            ListTag entries=s.getList("items",Tag.TAG_COMPOUND);
            for(int m=0;m<Math.min(2,entries.size());m++) {
                CompoundTag i=entries.getCompound(m);
                var resource=i.contains("quality")?EchoResourceInstance.pack(i.getUUID("match"),i.getUUID("id"),PackQuality.valueOf(i.getString("quality")),i.getUUID("origin"))
                        :EchoResourceInstance.card(i.getUUID("match"),i.getUUID("id"),EchoCardRegistry.require(i.getString("definition")),i.getUUID("origin"));
                items.add(resource); if(i.hasUUID("lock")) locks.put(resource.instanceId(),i.getUUID("lock"));
            }
            slots.add(new EchoInventory.Slot(items,s.hasUUID("reserved")?s.getUUID("reserved"):null));
        }
        return new EchoInventory.Snapshot(tag.getLong("revision"),slots,locks,tag.getBoolean("keyReserved"));
    }
}
