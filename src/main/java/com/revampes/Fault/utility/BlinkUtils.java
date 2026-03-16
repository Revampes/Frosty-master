package com.revampes.Fault.utility;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import com.revampes.Fault.events.impl.SendPacketEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BlinkUtils {
    private static ConcurrentLinkedQueue<Packet> blinkedPackets = new ConcurrentLinkedQueue();

    @EventHandler
    public void onSendPacket(SendPacketEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
    }
}
