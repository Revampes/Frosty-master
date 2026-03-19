package com.revampes.Fault.modules.impl.dungeon;

import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.DungeonUtils;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;
import java.util.List;

public class WitherDragons extends Module {

    public static WitherDragonsEnum priorityDragon = null;
    public static long currentTick = 0L;

    public WitherDragons() {
        super("Wither Dragons", category.Dungeon);
    }

    public void onTick() {
        for (WitherDragonsEnum dragon : WitherDragonsEnum.values()) {
            if (dragon.timeToSpawn > 0) dragon.timeToSpawn--;
            else if (dragon.state == WitherDragonsEnum.WitherDragonState.SPAWNING) {
                dragon.setAlive(null, currentTick);
            }
        }
        currentTick++;
    }

    public void onParticle(ParticleS2CPacket packet) {
        if (!DungeonUtils.isMasterMode() && !DungeonUtils.isDungeonFloor("F7")) return; // Mocking DungeonUtils.getF7Phase()

        if (packet.getCount() != 20 ||
            packet.getY() != 19.0 ||
            packet.getParameters().getType() != ParticleTypes.FLAME ||
            packet.getOffsetX() != 2f ||
            packet.getOffsetY() != 3f ||
            packet.getOffsetZ() != 2f ||
            packet.getSpeed() != 0f ||
            packet.getX() % 1 != 0.0 ||
            packet.getZ() % 1 != 0.0) return;

        int spawnedCount = 0;
        List<WitherDragonsEnum> dragonsList = new ArrayList<>();

        for (WitherDragonsEnum dragon : WitherDragonsEnum.values()) {
            spawnedCount += dragon.timesSpawned;

            if (dragon.state == WitherDragonsEnum.WitherDragonState.SPAWNING) {
                if (!dragonsList.contains(dragon)) dragonsList.add(dragon);
                continue;
            }

            if (packet.getX() < dragon.minX || packet.getX() > dragon.maxX ||
                packet.getZ() < dragon.minZ || packet.getZ() > dragon.maxZ) {
                continue;
            }

            dragon.state = WitherDragonsEnum.WitherDragonState.SPAWNING;
            dragon.timeToSpawn = 100;
            dragonsList.add(dragon);
        }

        if (!dragonsList.isEmpty() && (dragonsList.size() == 2 || spawnedCount >= 2) && priorityDragon == null) {
            priorityDragon = dragonsList.get(0); // Add priority logic here later
        }
    }

    public void onWorldLoad() {
        WitherDragonsEnum.reset();
    }
}

