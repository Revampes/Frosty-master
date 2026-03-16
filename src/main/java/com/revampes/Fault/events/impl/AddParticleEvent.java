package com.revampes.Fault.events.impl;

import net.minecraft.client.particle.Particle;

public class AddParticleEvent {
    public Particle particle;

    public AddParticleEvent(Particle particle) {
        this.particle = particle;
    }
}
