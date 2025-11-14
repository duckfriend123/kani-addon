package kani.addon.utils;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Centralized helper that briefly presses the jump key on behalf of modules while preserving the player's original
 * key state. Multiple modules can request holds; each request simply extends the remaining hold duration.
 */
public final class JumpSpoofer {
    private static boolean initialized;
    private static boolean active;
    private static boolean storedJumpPressed;
    private static int ticksRemaining;

    private JumpSpoofer() {
    }

    public static void init() {
        if (initialized) return;
        EVENT_BUS.subscribe(Listener.INSTANCE);
        initialized = true;
    }

    /**
     * Request that the jump key be held for at least the given number of ticks. Additional requests extend the hold.
     */
    public static void requestHold(int ticks) {
        if (ticks <= 0 || mc.options == null) return;

        if (!active) {
            storedJumpPressed = mc.options.jumpKey.isPressed();
            active = true;
        }

        mc.options.jumpKey.setPressed(true);
        ticksRemaining = Math.max(ticksRemaining, ticks);
    }

    private static void tick() {
        if (!active) return;
        if (mc.options == null) {
            stop();
            return;
        }

        mc.options.jumpKey.setPressed(true);

        if (ticksRemaining > 0) ticksRemaining--;
        if (ticksRemaining <= 0) stop();
    }

    private static void stop() {
        if (!active) return;
        if (mc.options != null) mc.options.jumpKey.setPressed(storedJumpPressed);
        active = false;
        ticksRemaining = 0;
    }

    private enum Listener {
        INSTANCE;

        @EventHandler
        private void onTick(TickEvent.Pre event) {
            tick();
        }
    }
}
