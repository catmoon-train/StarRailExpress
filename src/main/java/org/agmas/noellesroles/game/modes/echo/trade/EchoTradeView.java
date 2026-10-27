package org.agmas.noellesroles.game.modes.echo.trade;

import net.minecraft.nbt.*;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import java.util.*;

/** 仅当事人报价片段，不包含对手完整库存、身份或契约。 */
public record EchoTradeView(UUID id,long version,String status,UUID peer,String peerName,boolean proposer,
                            List<EchoResourceInstance> own,List<EchoResourceInstance> theirs,
                            boolean ownConfirmed,boolean peerConfirmed,long deadline,double range,String layoutError) {
    public EchoTradeView { own=List.copyOf(own); theirs=List.copyOf(theirs); if(own.size()>2 || theirs.size()>2) throw new IllegalArgumentException("INVALID_OFFER"); }
    public boolean active() { return status.equals("PROPOSED") || status.equals("EDITING"); }
    public CompoundTag write() {
        var tag=new CompoundTag(); tag.putUUID("id",id); tag.putLong("version",version); tag.putString("status",status);
        tag.putUUID("peer",peer); tag.putString("name",peerName); tag.putBoolean("proposer",proposer);
        tag.put("own",items(own)); tag.put("theirs",items(theirs)); tag.putBoolean("ownConfirmed",ownConfirmed); tag.putBoolean("peerConfirmed",peerConfirmed);
        tag.putLong("deadline",deadline); tag.putDouble("range",range); tag.putString("error",layoutError); return tag;
    }
    public static EchoTradeView read(CompoundTag tag) {
        return new EchoTradeView(tag.getUUID("id"),tag.getLong("version"),tag.getString("status"),tag.getUUID("peer"),tag.getString("name"),tag.getBoolean("proposer"),
                readItems(tag.getCompound("own")),readItems(tag.getCompound("theirs")),tag.getBoolean("ownConfirmed"),tag.getBoolean("peerConfirmed"),tag.getLong("deadline"),tag.getDouble("range"),tag.getString("error"));
    }
    private static CompoundTag items(List<EchoResourceInstance> items) {
        var slots=new ArrayList<EchoInventory.Slot>(); items.forEach(i -> slots.add(new EchoInventory.Slot(List.of(i),null)));
        while(slots.size()<6) slots.add(new EchoInventory.Slot(List.of(),null));
        return EchoInventoryNbt.write(new EchoInventory.Snapshot(0,slots,Map.of(),false));
    }
    private static List<EchoResourceInstance> readItems(CompoundTag tag) {
        return EchoInventoryNbt.read(tag).slots().stream().limit(2).flatMap(s -> s.items().stream().limit(1)).toList();
    }
}
