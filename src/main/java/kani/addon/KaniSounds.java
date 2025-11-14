package kani.addon;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class KaniSounds {
    private static final String NAMESPACE = "kani";

    public static final Identifier FLASHBANG_ID = Identifier.of(NAMESPACE, "flashbang");
    public static final Identifier ATEMLOS_ID = Identifier.of(NAMESPACE, "atemlos");

    public static final SoundEvent FLASHBANG = SoundEvent.of(FLASHBANG_ID);
    public static final SoundEvent ATEMLOS = SoundEvent.of(ATEMLOS_ID);

    private KaniSounds() {
    }
}
