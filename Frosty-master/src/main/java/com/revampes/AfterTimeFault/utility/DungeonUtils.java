package com.revampes.AfterTimeFault.utility;

public class DungeonUtils {
    
    /**
     * Helper check to determine if the player is currently inside a dungeon instance.
     * @return true if LocationUtils detects "Dungeon" or "Catacombs".
     */
    public static boolean isInDungeon() {
        return LocationUtils.isInDungeon();
    }

}
