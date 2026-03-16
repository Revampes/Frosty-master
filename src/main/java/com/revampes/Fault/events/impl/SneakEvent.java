package com.revampes.Fault.events.impl;

import com.revampes.Fault.utility.DirectionalInput;

public class SneakEvent {
    private final DirectionalInput directionalInput;
    private boolean sneak;

    public SneakEvent(DirectionalInput directionalInput, boolean sneak) {
        this.directionalInput = directionalInput;
        this.sneak = sneak;
    }

    public DirectionalInput getDirectionalInput() {
        return directionalInput;
    }

    public boolean getSneak() {
        return sneak;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }
}
