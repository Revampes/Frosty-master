package com.revampes.Fault.modules.impl.dungeon;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.UUID;

public enum WitherDragonsEnum {
    RED(new BlockPos(27, 14, 59), new BlockPos(32, 22, 59), new Box(14.5, 13.0, 45.5, 39.5, 28.0, 70.5), 'c', 0xFF5555, 24.0, 30.0, 56.0, 62.0, 50),
    ORANGE(new BlockPos(85, 14, 56), new BlockPos(80, 23, 56), new Box(72.0, 8.0, 47.0, 102.0, 28.0, 77.0), '6', 0xFFAA00, 82.0, 88.0, 53.0, 59.0, 62),
    GREEN(new BlockPos(27, 14, 94), new BlockPos(32, 23, 94), new Box(7.0, 8.0, 80.0, 37.0, 28.0, 110.0), 'a', 0x55FF55, 23.0, 29.0, 91.0, 97.0, 52),
    BLUE(new BlockPos(84, 14, 94), new BlockPos(79, 23, 94), new Box(71.5, 13.0, 82.5, 96.5, 26.0, 107.5), 'b', 0x55FFFF, 82.0, 88.0, 91.0, 97.0, 47),
    PURPLE(new BlockPos(56, 14, 125), new BlockPos(56, 22, 120), new Box(45.5, 13.0, 113.5, 68.5, 23.0, 136.5), '5', 0xAA00AA, 53.0, 59.0, 122.0, 128.0, 38);

    public final BlockPos spawnPos;
    public final BlockPos statuePos;
    public final Box aabbDimensions;
    public final char colorCode;
    public final int color;
    public final double minX, maxX, minZ, maxZ;
    public final int skipKillTime;

    public int timeToSpawn = 100;
    public WitherDragonState state = WitherDragonState.DEAD;
    public int timesSpawned = 0;
    public UUID entityUUID = null;
    public boolean isSprayed = false;
    public long spawnedTime = 0;

    WitherDragonsEnum(BlockPos spawnPos, BlockPos statuePos, Box aabbDimensions, char colorCode, int color, double minX, double maxX, double minZ, double maxZ, int skipKillTime) {
        this.spawnPos = spawnPos;
        this.statuePos = statuePos;
        this.aabbDimensions = aabbDimensions;
        this.colorCode = colorCode;
        this.color = color;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.skipKillTime = skipKillTime;
    }

    public enum WitherDragonState {
        SPAWNING, ALIVE, DEAD
    }

    public void setAlive(UUID entityId, long currentTick) {
        if (entityId != null) this.entityUUID = entityId;
        if (state == WitherDragonState.ALIVE) return;
        state = WitherDragonState.ALIVE;
        timesSpawned++;
        spawnedTime = currentTick;
        isSprayed = false;
    }

    public void setDead() {
        state = WitherDragonState.DEAD;
        entityUUID = null;
    }

    public static void reset() {
        for (WitherDragonsEnum dragon : values()) {
            dragon.timeToSpawn = 0;
            dragon.timesSpawned = 0;
            dragon.state = WitherDragonState.DEAD;
            dragon.entityUUID = null;
            dragon.isSprayed = false;
            dragon.spawnedTime = 0;
        }
    }
}

