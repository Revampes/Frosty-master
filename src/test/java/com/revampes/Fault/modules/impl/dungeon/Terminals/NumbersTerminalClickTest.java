package com.revampes.Fault.modules.impl.dungeon.Terminals;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumbersTerminalClickTest {

    private static final class RecordingNumbersTerminal extends NumbersTerminal {
        int sendCount;
        int clickedSlot = -1;
        int clickedButton = -1;

        @Override
        protected boolean sendWindowClickNoPickup(int slot, int button) {
            sendCount++;
            clickedSlot = slot;
            clickedButton = button;
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void seedFirstSolutionSlot(NumbersTerminal terminal, int slot) {
        try {
            Field orderedSolutionField = NumbersTerminal.class.getDeclaredField("orderedSolutionSlots");
            orderedSolutionField.setAccessible(true);
            List<Integer> ordered = (List<Integer>) orderedSolutionField.get(terminal);
            ordered.clear();
            ordered.add(slot);
            terminal.getSolution().add(slot);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to seed solution slot for test", e);
        }
    }

    @Test
    void leftClickOnRedPaneUsesNoPickupClickPath() {
        RecordingNumbersTerminal terminal = new RecordingNumbersTerminal();
        // Emulate solved state where red pane slot is next clickable slot.
        seedFirstSolutionSlot(terminal, 10);

        terminal.onSlotClick(10, 0);

        assertEquals(1, terminal.sendCount, "Expected exactly one click packet to be sent");
        assertEquals(10, terminal.clickedSlot, "Expected click to target the red pane slot");
        assertEquals(0, terminal.clickedButton, "Expected left click button normalization to stay 0");
    }

    @Test
    void leftClickOnWrongSlotDoesNotSendNoPickupPacket() {
        RecordingNumbersTerminal terminal = new RecordingNumbersTerminal();
        seedFirstSolutionSlot(terminal, 10);

        terminal.onSlotClick(11, 0);

        assertEquals(0, terminal.sendCount, "Expected no click packet when clicking wrong slot");
    }
}
