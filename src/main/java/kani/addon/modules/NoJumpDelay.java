package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.LivingEntityAccessor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super(KaniAddon.CATEGORY, "no-jump-delay", "Removes the vanilla jump cooldown.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        ((LivingEntityAccessor) mc.player).meteor$setJumpCooldown(0);
    }
}
