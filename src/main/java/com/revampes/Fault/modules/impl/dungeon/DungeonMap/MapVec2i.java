package com.revampes.Fault.modules.impl.dungeon.DungeonMap;

public final class MapVec2i {
    public final int x;
    public final int z;

    public MapVec2i(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public MapVec2i add(MapVec2i other) {
        return new MapVec2i(this.x + other.x, this.z + other.z);
    }

    public int mapIndex() {
        return z * 128 + x;
    }
}