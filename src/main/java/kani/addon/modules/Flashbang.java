package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.KaniSounds;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;

public class Flashbang extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private static final double HOLD_DURATION_SECONDS = 2.0;

    private final Setting<Double> fadeDuration = sgGeneral.add(new DoubleSetting.Builder()
        .name("fade-duration")
        .description("How long, in seconds, it takes for the flash to disappear.")
        .defaultValue(4.5)
        .min(0.25)
        .sliderMax(15.0)
        .build()
    );

    private long flashStart = -1;
    private boolean flashing;

    public Flashbang() {
        super(KaniAddon.CATEGORY, "flashbang", "Blinds the screen white and plays a bang.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        flashStart = System.currentTimeMillis();
        flashing = true;
        mc.getSoundManager().play(PositionedSoundInstance.master(KaniSounds.FLASHBANG, 1.0f, 1.0f));
    }

    @Override
    public void onDeactivate() {
        flashing = false;
        flashStart = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!flashing) return;
        if (getFadeProgress() <= 0) {
            flashing = false;
            if (isActive()) toggle();
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!flashing) return;
        double progress = getFadeProgress();
        if (progress <= 0) return;

        int alpha = (int) Math.round(progress * 255.0);
        int color = (alpha & 0xFF) << 24 | 0xFFFFFF;
        event.drawContext.fill(0, 0, event.screenWidth, event.screenHeight, color);
    }

    private double getFadeProgress() {
        if (!flashing) return 0;
        double duration = fadeDuration.get();
        if (duration <= 0) return 0;

        double elapsedSeconds = (System.currentTimeMillis() - flashStart) / 1000.0;
        if (elapsedSeconds <= HOLD_DURATION_SECONDS) return 1.0;

        double fadeTime = Math.max(0.0, duration);
        if (fadeTime <= 0) return 0;

        double fadeProgress = 1.0 - ((elapsedSeconds - HOLD_DURATION_SECONDS) / fadeTime);
        return Math.max(0.0, fadeProgress);
    }
}
