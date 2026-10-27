package org.agmas.noellesroles.game.modes.echo.state;

import java.util.*;

/** 会话持有的货槽；清除或替换职业投影不能删除资源归属。 */
public final class EchoCargoState {
    private final List<UUID> items=new ArrayList<>();
    public List<UUID> items() { return List.copyOf(items); }
    public void add(UUID instance,int capacity) {
        Objects.requireNonNull(instance);
        if(items.size()>=capacity || items.contains(instance)) throw new IllegalStateException("货槽已满或实例重复");
        items.add(instance);
    }
    public void remove(UUID instance) {
        if(!items.remove(instance)) throw new IllegalStateException("货物不属于本人");
    }
}
