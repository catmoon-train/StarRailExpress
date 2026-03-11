package org.agmas.noellesroles.utils;

/**
 * 数据对
 */
public class Pair<T, U> {
    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }
    public T first;
    public U second;
}
