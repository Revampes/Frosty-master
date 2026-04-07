package com.revampes.Fault.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonUtilsTeammateParserTest {

    @Test
    void parsesStandardDungeonTabLine() {
        DungeonUtils.DungeonTeammate teammate = DungeonUtils.parseDungeonTeammateTabLine("[42] [MVP+] TeammateName Something (MAGE XXVIII)");

        assertNotNull(teammate);
        assertEquals("TeammateName", teammate.name());
        assertEquals(DungeonUtils.DungeonClassType.MAGE, teammate.classType());
        assertEquals(28, teammate.classLevel());
        assertFalse(teammate.dead());
    }

    @Test
    void parsesDeadTeammateWithoutLevel() {
        DungeonUtils.DungeonTeammate teammate = DungeonUtils.parseDungeonTeammateTabLine("[34] [VIP] FallenGuy Whatever (DEAD)");

        assertNotNull(teammate);
        assertEquals("FallenGuy", teammate.name());
        assertEquals(DungeonUtils.DungeonClassType.DEAD, teammate.classType());
        assertEquals(0, teammate.classLevel());
        assertTrue(teammate.dead());
    }

    @Test
    void parsesFormattedLineAndNumericClassLevel() {
        DungeonUtils.DungeonTeammate teammate = DungeonUtils.parseDungeonTeammateTabLine("\u00A76[50] \u00A7b[MVP+] \u00A7aArcherDude \u00A77stuff \u00A7r(ARCHER 35)");

        assertNotNull(teammate);
        assertEquals("ArcherDude", teammate.name());
        assertEquals(DungeonUtils.DungeonClassType.ARCHER, teammate.classType());
        assertEquals(35, teammate.classLevel());
        assertFalse(teammate.dead());
    }

    @Test
    void ignoresNonTeammateTabLine() {
        DungeonUtils.DungeonTeammate teammate = DungeonUtils.parseDungeonTeammateTabLine("Area: Catacombs");

        assertNull(teammate);
    }
}
