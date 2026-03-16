package com.revampes.Fault.events.impl;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

public class SimpleChatEventHandler implements ClientReceiveMessageEvents.Game {
    public static final SimpleChatEventHandler INSTANCE = new SimpleChatEventHandler();

    @Override
    public void onReceiveGameMessage(Text message, boolean isOverlay) {
        String text = message.getString();
        if (isOverlay) {
            for (com.revampes.Fault.modules.Module m : com.revampes.Fault.modules.ModuleManager.getModules()) {
                if (m.isEnabled() && m instanceof Overlay) {
                    ((Overlay) m).onReceiveOverlay(text);
                }
            }
        } else {
            // AutoPetNotification handles this via Orbit events, no need to duplicate logic here
        }
    }

    public interface NonOverlay {
        void onReceiveChat(String message);
    }

    public interface Overlay {
        void onReceiveOverlay(String message);
    }
}
