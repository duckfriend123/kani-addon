package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.KaniSounds;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;

public class HeleneFischer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> loop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .description("Automatically restarts the song when it finishes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("Nightcore")
        .description("Playback volume multiplier.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(3.0)
        .build()
    );

    private SoundInstance currentSound;
    private int ticksSincePlay;

    public HeleneFischer() {
        super(KaniAddon.CATEGORY, "helene-fischer", "Plays Atemlos for as long as the module is toggled.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }
        playSong();
    }

    @Override
    public void onDeactivate() {
        stopSong();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) {
            if (isActive()) toggle();
            return;
        }
        if (!isActive() || currentSound == null) return;

        ticksSincePlay++;
        if (ticksSincePlay < 5) return;

        if (!mc.getSoundManager().isPlaying(currentSound)) {
            if (loop.get()) playSong();
            else {
                if (isActive()) toggle();
            }
        }
    }

    private void playSong() {
        stopSong();
        currentSound = PositionedSoundInstance.master(KaniSounds.ATEMLOS, volume.get().floatValue(), 1.0f);
        mc.getSoundManager().play(currentSound);
        ticksSincePlay = 0;
    }

    private void stopSong() {
        if (currentSound == null) return;
        mc.getSoundManager().stop(currentSound);
        currentSound = null;
        ticksSincePlay = 0;
    }
}
