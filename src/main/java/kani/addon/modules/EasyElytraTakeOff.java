package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.utils.JumpSpoofer;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;

public class EasyElytraTakeOff extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> retryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("retry-delay")
        .description("Ticks between glide packet retries if the server doesn't respond immediately.")
        .defaultValue(5)
        .min(2)
        .sliderRange(2, 20)
        .build()
    );

    private final Setting<Integer> airTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout")
        .description("Maximum ticks to wait for gliding before cancelling the assist.")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 60)
        .build()
    );

    private enum Phase { IDLE, WAIT_FOR_GLIDE }

    private Phase phase = Phase.IDLE;
    private Hand pendingHand;
    private int waitTicks;
    private int retryTimer;
    private boolean skipInteractEvent;
    private int attempts;
    private boolean spoofingJumpRelease;
    private boolean storedJumpState;
    private boolean pendingJumpRestore;
    private int jumpReleaseTicks;
    private static final int JUMP_RELEASE_DURATION = 2;
    private static final int POST_JUMP_TICKS = 2;
    private static final int MAX_ATTEMPTS = 2;

    public EasyElytraTakeOff() {
        super(KaniAddon.CATEGORY, "easy-elytra-takeoff", "Deploys your elytra mid-air when you tap a rocket, then fires it.");
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (!isActive() || skipInteractEvent) return;
        if (!canTrigger()) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.MISS) return;

        ItemStack stack = mc.player.getStackInHand(event.hand);
        if (!stack.isOf(Items.FIREWORK_ROCKET)) return;

        event.toReturn = ActionResult.FAIL;
        startSequence(event.hand);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive() || !isPlayerValid()) {
            resetState();
            return;
        }

        handleJumpRelease();

        if (phase != Phase.WAIT_FOR_GLIDE) return;

        waitTicks++;

        if (mc.player.isGliding()) {
            useRocket();
            resetState();
            return;
        }

        if (mc.player.isOnGround()) {
            resetState();
            return;
        }

        if (retryTimer > 0) retryTimer--;

        if (attempts == 0 || (retryTimer <= 0 && attempts < MAX_ATTEMPTS)) {
            sendStartFlying();
        }

        if (waitTicks > airTimeout.get()) resetState();
    }

    private void startSequence(Hand hand) {
        pendingHand = hand;
        waitTicks = 0;
        retryTimer = 0;
        attempts = 0;
        pendingJumpRestore = false;
        storedJumpState = mc.options != null && mc.options.jumpKey.isPressed();
        phase = Phase.WAIT_FOR_GLIDE;
        beginJumpReleaseSpoof();
        if (!spoofingJumpRelease) sendStartFlying();
    }

    private void sendStartFlying() {
        if (mc.player.isOnGround() || spoofingJumpRelease) return;
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        attempts++;
        retryTimer = retryDelay.get();
        restoreJumpIfNeeded();
        JumpSpoofer.requestHold(POST_JUMP_TICKS);
    }

    private void useRocket() {
        if (pendingHand == null || mc.interactionManager == null) return;
        ItemStack stack = mc.player.getStackInHand(pendingHand);
        if (stack.isEmpty() || !stack.isOf(Items.FIREWORK_ROCKET)) return;

        skipInteractEvent = true;
        mc.interactionManager.interactItem(mc.player, pendingHand);
        mc.player.swingHand(pendingHand);
        skipInteractEvent = false;
    }

    private boolean canTrigger() {
        if (!isPlayerValid()) return false;
        if (mc.player.isGliding()) return false;
        if (mc.player.isOnGround()) return false;
        if (phase != Phase.IDLE) return false;

        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return !chest.isEmpty() && chest.contains(DataComponentTypes.GLIDER);
    }

    private boolean isPlayerValid() {
        return mc.player != null && mc.world != null && mc.interactionManager != null;
    }

    private void resetState() {
        phase = Phase.IDLE;
        pendingHand = null;
        waitTicks = 0;
        retryTimer = 0;
        attempts = 0;
        skipInteractEvent = false;
        if ((spoofingJumpRelease || pendingJumpRestore) && mc.options != null) mc.options.jumpKey.setPressed(storedJumpState);
        spoofingJumpRelease = false;
        pendingJumpRestore = false;
    }

    private void beginJumpReleaseSpoof() {
        if (mc.options == null) {
            spoofingJumpRelease = false;
            return;
        }

        if (!storedJumpState) {
            spoofingJumpRelease = false;
            return;
        }

        jumpReleaseTicks = JUMP_RELEASE_DURATION;
        spoofingJumpRelease = true;
        pendingJumpRestore = true;
        mc.options.jumpKey.setPressed(false);
    }

    private void handleJumpRelease() {
        if (!spoofingJumpRelease || mc.options == null) return;

        mc.options.jumpKey.setPressed(false);
        if (jumpReleaseTicks > 0) jumpReleaseTicks--;

        if (jumpReleaseTicks <= 0) {
            spoofingJumpRelease = false;
            sendStartFlying();
        }
    }

    private void restoreJumpIfNeeded() {
        if (!pendingJumpRestore || mc.options == null) return;
        mc.options.jumpKey.setPressed(true);
        pendingJumpRestore = false;
    }
}
