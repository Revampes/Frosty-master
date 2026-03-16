package com.revampes.Fault.modules.impl.other;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import com.revampes.Fault.events.impl.ServerConnectBeginEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SliderSetting;

public class AutoReconnect extends Module {

    public SliderSetting delay;

    public Pair<ServerAddress, ServerInfo> lastServerConnection;

    public AutoReconnect() {
        super("AutoReconnect", category.Other);

        this.registerSetting(delay = new SliderSetting("Delay", 1000, 500, 5000, 100));
    }

    @EventHandler
    private void onServerConnectBegin(ServerConnectBeginEvent event) {
        lastServerConnection = new ObjectObjectImmutablePair<>(event.address, event.info);
    }
}
