package com.revampes.AfterTimeFault.events.impl;

import net.minecraft.client.particle.Particle;

public class AddParticleEvent {
    public Particle particle;

    public AddParticleEvent(Particle particle) {
        this.particle = particle;
    }
}
