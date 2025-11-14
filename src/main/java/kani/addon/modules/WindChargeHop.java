package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.utils.JumpSpoofer;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class WindChargeHop extends Module {
    private static final ItemStack WIND_CHARGE_STACK = new ItemStack(Items.WIND_CHARGE);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> legit = sgGeneral.add(new BoolSetting.Builder()
        .name("legit")
        .description("Visibly selects the wind charge before using it.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("How fast to rotate towards the target pitch, in degrees per tick.")
        .defaultValue(30.0)
        .min(1.0)
        .sliderMax(90.0)
        .build()
    );

    private final Setting<Boolean> silentAim = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-aim")
        .description("Spoofs the rotation packet so you do not see the rotation client-side.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxAirTime = sgGeneral.add(new IntSetting.Builder()
        .name("max-air-time")
        .description("Maximum ticks to wait in air before cancelling.")
        .defaultValue(40)
        .min(10)
        .sliderMax(200)
        .build()
    );

    private Hand plannedHand = Hand.MAIN_HAND;
    private int plannedSlot = -1;
    private boolean requiresSwap;
    private boolean swapAfterUse;
    private boolean swapAfterAnimation;
    private boolean hasPendingSwap;
    private float startingPitch;
    private float simulatedPitch;
    private float savedClientPitch;
    private boolean spoofingPackets;

    private static final float TARGET_PITCH = 82f;
    private static final int JUMP_HOLD_TICKS = 2;

    private enum Phase { IDLE, WAIT_FOR_GROUND, LOOK_DOWN, THROW, WAIT_JUMP, LOOK_UP }
    private Phase phase = Phase.IDLE;
    private int groundWaitTicks;
    private int jumpDelayTicks;

    public WindChargeHop() {
        super(KaniAddon.CATEGORY, "windchargehop", "Smoothly uses wind charges while spoofing a natural look-down motion.");
    }

    @Override
    public void onDeactivate() {
        cleanupSwap();
        resetPlan();
        phase = Phase.IDLE;
        spoofingPackets = false;
    }

    @Override
    public void onActivate() {
        if (!prepareSequence()) {
            toggle();
            return;
        }
        groundWaitTicks = 0;
        jumpDelayTicks = 0;

        if (mc.player.isOnGround()) beginLookDown();
        else phase = Phase.WAIT_FOR_GROUND;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!isActive()) return;

        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || mc.player.isSpectator()) {
            toggle();
            return;
        }

        switch (phase) {
            case WAIT_FOR_GROUND -> handleGroundWait();
            case LOOK_DOWN -> {
                if (rotateTowards(TARGET_PITCH)) phase = Phase.THROW;
            }
            case THROW -> {
                if (!mc.player.isOnGround()) {
                    phase = Phase.WAIT_FOR_GROUND;
                    groundWaitTicks = 0;
                    break;
                }
                if (executeCharge()) {
                    jumpDelayTicks = 0;
                    phase = Phase.WAIT_JUMP;
                } else {
                    stopWithFailure();
                }
            }
            case WAIT_JUMP -> handleJumpDelay();
            case LOOK_UP -> {
                if (rotateTowards(startingPitch)) finishSequence();
            }
            default -> {
                // no-op
            }
        }
    }

    private boolean prepareSequence() {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (mc.player.getItemCooldownManager().isCoolingDown(WIND_CHARGE_STACK)) return false;

        FindItemResult result = InvUtils.findInHotbar(Items.WIND_CHARGE);
        if (!result.found()) {
            warning("Wind charge not found in hotbar.");
            return false;
        }

        plannedHand = result.getHand();
        if (plannedHand == null) plannedHand = Hand.MAIN_HAND;

        plannedSlot = result.slot();
        requiresSwap = plannedHand == Hand.MAIN_HAND && !result.isMainHand();
        swapAfterUse = requiresSwap && !legit.get();
        swapAfterAnimation = requiresSwap && legit.get();
        hasPendingSwap = false;

        return true;
    }

    private boolean executeCharge() {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (mc.player.getItemCooldownManager().isCoolingDown(WIND_CHARGE_STACK)) return false;

        if (requiresSwap && !hasPendingSwap) {
            if (plannedSlot < 0) {
                warning("Unable to find the stored wind charge slot.");
                return false;
            }
            if (!InvUtils.swap(plannedSlot, true)) {
                warning("Unable to swap to wind charge.");
                return false;
            }
            hasPendingSwap = true;
        }

        float restorePitch = 0f;
        boolean restore = false;
        if (silentAim.get()) {
            restorePitch = mc.player.getPitch();
            mc.player.setPitch(simulatedPitch);
            restore = true;
        } else {
            mc.player.setPitch(simulatedPitch);
        }

        mc.interactionManager.interactItem(mc.player, plannedHand);
        mc.player.swingHand(plannedHand);

        if (restore) mc.player.setPitch(restorePitch);

        if (swapAfterUse) cleanupSwap();
        return true;
    }

    private void finishSequence() {
        if (swapAfterAnimation) cleanupSwap();
        resetPlan();
        phase = Phase.IDLE;
        if (isActive()) toggle();
    }

    private void cleanupSwap() {
        if (!hasPendingSwap) return;
        InvUtils.swapBack();
        hasPendingSwap = false;
    }

    private void stopWithFailure() {
        cleanupSwap();
        resetPlan();
        phase = Phase.IDLE;
        if (isActive()) toggle();
    }

    private void resetPlan() {
        plannedHand = Hand.MAIN_HAND;
        plannedSlot = -1;
        requiresSwap = false;
        swapAfterUse = false;
        swapAfterAnimation = false;
        simulatedPitch = mc.player != null ? mc.player.getPitch() : 0f;
        groundWaitTicks = 0;
        jumpDelayTicks = 0;
    }

    private boolean rotateTowards(float targetPitch) {
        if (mc.player == null) return false;

        float currentPitch = silentAim.get() ? simulatedPitch : mc.player.getPitch();
        double delta = MathHelper.wrapDegrees(targetPitch - currentPitch);

        double maxStep = rotationSpeed.get();
        float nextPitch;
        if (Math.abs(delta) <= maxStep) nextPitch = targetPitch;
        else nextPitch = currentPitch + (float) Math.copySign(maxStep, delta);

        simulatedPitch = nextPitch;
        if (!silentAim.get()) mc.player.setPitch(nextPitch);

        return Math.abs(nextPitch - targetPitch) < 0.01f;
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (!shouldSpoofPacketRotation()) return;
        savedClientPitch = mc.player.getPitch();
        mc.player.setPitch(simulatedPitch);
        spoofingPackets = true;
    }

    @EventHandler
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (!spoofingPackets || mc.player == null) return;
        mc.player.setPitch(savedClientPitch);
        spoofingPackets = false;
    }

    private boolean shouldSpoofPacketRotation() {
        return silentAim.get() && phase != Phase.IDLE && mc.player != null;
    }

    private void beginLookDown() {
        if (mc.player == null) return;
        startingPitch = mc.player.getPitch();
        simulatedPitch = startingPitch;
        groundWaitTicks = 0;
        phase = Phase.LOOK_DOWN;
        rotateTowards(TARGET_PITCH);
    }

    private void handleGroundWait() {
        if (mc.player == null) return;

        if (mc.player.isOnGround()) {
            beginLookDown();
            return;
        }

        groundWaitTicks++;
        if (groundWaitTicks >= maxAirTime.get()) {
            warning("Stayed airborne too long, cancelling WindChargeHop.");
            stopWithFailure();
        }
    }

    private void handleJumpDelay() {
        if (mc.player == null) return;

        jumpDelayTicks++;
        if (jumpDelayTicks >= 1) {
            if (mc.player.isOnGround()) {
                JumpSpoofer.requestHold(JUMP_HOLD_TICKS);
                phase = Phase.LOOK_UP;
            }
        }
    }
}
