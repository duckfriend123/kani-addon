package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class Module360 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> spinTicks = sgGeneral.add(new IntSetting.Builder()
        .name("ticks")
        .description("How many ticks the 360 takes.")
        .defaultValue(20)
        .min(1)
        .sliderMax(80)
        .build()
    );

    private final Setting<Boolean> silentAim = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-aim")
        .description("Spoofs your rotations so you don't see the spin.")
        .defaultValue(false)
        .build()
    );

    private float startingYaw;
    private float simulatedYaw;
    private int elapsedTicks;
    private boolean spinning;

    private boolean spoofing;
    private float savedClientYaw;

    public Module360() {
        super(KaniAddon.CATEGORY, "360", "Performs a configurable 360 spin.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        startingYaw = mc.player.getYaw();
        simulatedYaw = startingYaw;
        elapsedTicks = 0;
        spinning = true;
    }

    @Override
    public void onDeactivate() {
        spinning = false;
        spoofing = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) {
            spinning = false;
            if (isActive()) toggle();
            return;
        }
        if (!spinning) return;

        elapsedTicks++;
        int totalTicks = Math.max(1, spinTicks.get());
        float progress = Math.min(1.0f, elapsedTicks / (float) totalTicks);
        simulatedYaw = startingYaw + 360.0f * progress;

        if (!silentAim.get()) mc.player.setYaw(simulatedYaw);

        if (progress >= 1.0f) {
            spinning = false;
            if (isActive()) toggle();
        }
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (!shouldSpoof()) return;

        savedClientYaw = mc.player.getYaw();
        mc.player.setYaw(simulatedYaw);
        spoofing = true;
    }

    @EventHandler
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (!spoofing || mc.player == null) return;

        mc.player.setYaw(savedClientYaw);
        spoofing = false;
    }

    private boolean shouldSpoof() {
        return silentAim.get() && spinning && mc.player != null;
    }
}
