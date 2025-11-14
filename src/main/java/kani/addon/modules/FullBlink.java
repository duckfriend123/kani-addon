package kani.addon.modules;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

public class FullBlink extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> renderPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("render-player")
        .description("Renders a fake player at your starting position while blinking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Turn the module off automatically after a set amount of time.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> autoDisableSeconds = sgGeneral.add(new DoubleSetting.Builder()
        .name("duration")
        .description("How long the module stays active before turning off automatically.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 30.0)
        .visible(autoDisable::get)
        .build()
    );

    private final List<Packet<?>> storedPackets = new ArrayList<>();
    private boolean releasingPackets;
    private int activeTicks;
    private FakePlayerEntity fakePlayer;

    public FullBlink() {
        super(KaniAddon.CATEGORY, "full-blink", "Chokes every outgoing packet until released.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        storedPackets.clear();
        releasingPackets = false;
        activeTicks = 0;
        spawnFakePlayer();
    }

    @Override
    public void onDeactivate() {
        flushPackets();
        removeFakePlayer();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isActive() || releasingPackets) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        storedPackets.add(event.packet);
        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive() || !autoDisable.get()) return;
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        activeTicks++;
        if (activeTicks >= getAutoDisableTicks()) {
            toggle();
        }
    }

    private int getAutoDisableTicks() {
        return Math.max(1, (int) Math.round(autoDisableSeconds.get() * 20.0));
    }

    private void flushPackets() {
        if (storedPackets.isEmpty()) return;

        if (mc.getNetworkHandler() == null) {
            storedPackets.clear();
            return;
        }

        releasingPackets = true;
        for (Packet<?> packet : storedPackets) {
            mc.getNetworkHandler().sendPacket(packet);
        }
        releasingPackets = false;
        storedPackets.clear();
    }

    private void spawnFakePlayer() {
        if (!renderPlayer.get() || mc.player == null || mc.world == null) return;

        removeFakePlayer();
        fakePlayer = new FakePlayerEntity(mc.player, mc.player.getGameProfile().name(), 20, true);
        fakePlayer.doNotPush = true;
        fakePlayer.hideWhenInsideCamera = true;
        fakePlayer.noHit = true;
        fakePlayer.spawn();
    }

    private void removeFakePlayer() {
        if (fakePlayer != null) {
            fakePlayer.despawn();
            fakePlayer = null;
        }
    }
}
