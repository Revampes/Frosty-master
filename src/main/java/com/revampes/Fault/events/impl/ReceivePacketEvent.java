package com.revampes.Fault.events.impl;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import com.revampes.Fault.events.Cancellable;

public class ReceivePacketEvent extends Cancellable {
    public Packet<?> packet;
    public ClientConnection connection;

    public ReceivePacketEvent(Packet<?> packet, ClientConnection connection) {
        this.setCancelled(false);
        this.packet = packet;
        this.connection = connection;
    }

    public Packet<?> getPacket() {
        return packet;
    }
}
