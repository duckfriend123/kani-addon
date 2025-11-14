package kani.addon.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class HotbarSpoofer {
    private static boolean initialized;
    private static boolean active;
    private static int targetSlot = -1;
    private static int ticksRemaining;

    private HotbarSpoofer() {
    }

    public static void init() {
        if (initialized) return;
        EVENT_BUS.subscribe(Listener.INSTANCE);
        initialized = true;
    }

    public static void pressSlot(int slot, int ticks) {
        if (mc.options == null || slot < 0 || slot > 8) return;

        targetSlot = slot;
        ticksRemaining = Math.max(1, ticks);
        active = true;

        mc.options.hotbarKeys[slot].setPressed(true);
    }

    private static void tick() {
        if (!active) return;
        if (mc.options == null) {
            stop();
            return;
        }

        ticksRemaining--;
        if (ticksRemaining <= 0) stop();
    }

    private static void stop() {
        if (!active || mc.options == null) return;

        mc.options.hotbarKeys[targetSlot].setPressed(false);
        active = false;
        targetSlot = -1;
    }

    private enum Listener {
        INSTANCE;

        @EventHandler
        private void onTick(TickEvent.Post event) {
            tick();
        }
    }
}
