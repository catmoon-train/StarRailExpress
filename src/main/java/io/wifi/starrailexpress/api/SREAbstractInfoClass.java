package io.wifi.starrailexpress.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.HashSet;
import java.util.Set;

public abstract class SREAbstractInfoClass {

    /**
     * 显示FLAG。用于RoleIntroduceScreen的filter
     */
    public HashSet<String> flags = new HashSet<>();

    /**
     * 获取显示FLAG
     */
    public HashSet<String> getFlags() {
        return this.flags;
    }

    public Component getDisplayName() {
        return RoleUtils.getRoleOrModifierName(this);
    }

    public Component getDisplayNameWithColor() {
        return RoleUtils.getRoleOrModifierNameWithColor(this);
    }

    public abstract Component getName();

    public abstract Component getDescription();

    public abstract boolean hasSimpleDescription();

    public abstract Component getSimpleDescription();

    public abstract ResourceLocation identifier();

    public abstract int color();

    public int getColor() {
        return color();
    }

    public ResourceLocation getIdentifier() {
        return identifier();
    }

    public HashSet<SREModifier> relatedModifiers = new HashSet<>();
    public HashSet<SRERole> relatedRoles = new HashSet<>();

    /**
     * 获取与此相关的职业。用于职业介绍。
     * 
     * @return
     */
    public Set<SRERole> getRelatedRoles() {
        Set<SRERole> result = new HashSet<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (var i : relatedRoles) {
            if (seen.add(i.identifier())) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * 获取与此相关的修饰符。用于职业介绍。
     * 
     * @return
     */
    public Set<SREModifier> getRelatedModifiers() {
        Set<SREModifier> result = new HashSet<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (var r : relatedModifiers) {
            if (seen.add(r.identifier())) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * 职业/修饰符是否于此相关。用于职业介绍。
     * 
     * @return
     */
    public boolean isRelated(SREAbstractInfoClass... item) {
        for (var i : item) {
            if (i instanceof SRERole r) {
                if (!this.relatedRoles.contains(r))
                    return false;
            } else if (i instanceof SREModifier m) {
                if (!this.relatedModifiers.contains(m)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return getName().getString();
    }
}
