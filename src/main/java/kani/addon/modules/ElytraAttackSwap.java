package kani.addon.modules;

import kani.addon.KaniAddon;
import kani.addon.hud.FallDistanceHud;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import meteordevelopment.meteorclient.settings.IntSetting;

/**
 * Automatically equips a chestplate right before attacking while mid-air with an elytra equipped,
 * letting the player land swings without giving up protection. Optionally swaps back to the elytra
 * after a small delay to resume flying.
 */
public class ElytraAttackSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> minFallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fall-distance")
        .description("Minimum fall distance required to trigger when not actively gliding.")
        .defaultValue(1.5)
        .min(0.0)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back-to-elytra")
        .description("Re-equip the elytra after the attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapBackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Ticks to wait before re-equipping the elytra.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 10)
        .visible(swapBack::get)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Ticks to delay the attack after swapping, ensuring the chestplate is equipped first.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );


    private final Setting<Boolean> requireFallDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-falling")
        .description("Only trigger if you've fallen at least 1.5 blocks (uses the fall distance HUD).")
        .defaultValue(false)
        .build()
    );


    private static final float MIN_FALL_DISTANCE = 1.5f;

    private int pendingElytraSlot = -1;
    private int swapBackTimer;
    private Entity pendingAttackTarget;
    private int pendingAttackDelay;
    private boolean performingAttack;

    public ElytraAttackSwap() {
        super(KaniAddon.CATEGORY, "elytra-attack-swap", "Equips a chestplate from your hotbar before landing an aerial attack.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isActive() || performingAttack) return;
        if (!canTrigger()) return;

        int chestplateSlot = findBestChestplateSlot();
        if (chestplateSlot == -1) {
            warning("No chestplate found in hotbar.");
            return;
        }

        if (!equipChestplate(chestplateSlot)) return;

        event.cancel();

        if (swapBack.get()) {
            pendingElytraSlot = chestplateSlot;
            swapBackTimer = swapBackDelay.get();
        }

        if (attackDelay.get() > 0) {
            pendingAttackTarget = event.entity;
            pendingAttackDelay = attackDelay.get();
        } else {
            performAttack(event.entity);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handlePendingAttack();

        if (!swapBack.get()) return;
        if (pendingElytraSlot == -1) return;
        if (mc.player == null || mc.world == null) {
            pendingElytraSlot = -1;
            return;
        }

        if (!mc.player.isGliding() && mc.player.isOnGround()) {
            // If we've landed before swapping back, keep wearing the chestplate.
            pendingElytraSlot = -1;
            return;
        }

        if (swapBackTimer > 0) {
            swapBackTimer--;
            return;
        }

        ItemStack stack = mc.player.getInventory().getStack(pendingElytraSlot);
        if (!isElytra(stack)) {
            pendingElytraSlot = -1;
            return;
        }

        InvUtils.move().from(pendingElytraSlot).toArmor(EquipmentSlot.CHEST.getEntitySlotId());
        pendingElytraSlot = -1;
    }

    @Override
    public void onDeactivate() {
        pendingElytraSlot = -1;
        pendingAttackTarget = null;
        pendingAttackDelay = 0;
        performingAttack = false;
    }

    private boolean canTrigger() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return false;
        if (mc.player.isSpectator()) return false;
        if (mc.player.isOnGround()) return false;

        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!isElytra(chestStack)) return false;

        if (!mc.player.isGliding() && mc.player.fallDistance < minFallDistance.get()) return false;

        if (requireFallDistance.get()) {
            float fallDistance = FallDistanceHud.getCurrentFallDistance();
            if (fallDistance < MIN_FALL_DISTANCE) return false;
        }

        return pendingElytraSlot == -1;
    }

    private void handlePendingAttack() {
        if (pendingAttackTarget == null) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            pendingAttackTarget = null;
            pendingAttackDelay = 0;
            return;
        }

        if (pendingAttackTarget.isRemoved()) {
            pendingAttackTarget = null;
            pendingAttackDelay = 0;
            return;
        }

        if (pendingAttackDelay > 0) {
            pendingAttackDelay--;
            return;
        }

        performAttack(pendingAttackTarget);
        pendingAttackTarget = null;
        pendingAttackDelay = 0;
    }

    private void performAttack(Entity entity) {
        if (entity == null || mc.interactionManager == null || mc.player == null) return;
        performingAttack = true;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        performingAttack = false;
    }

    private boolean equipChestplate(int slot) {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (!isChestplate(stack)) return false;

        InvUtils.move().from(slot).toArmor(EquipmentSlot.CHEST.getEntitySlotId());
        return isChestplate(mc.player.getEquippedStack(EquipmentSlot.CHEST));
    }

    private int findBestChestplateSlot() {
        if (mc.player == null) return -1;

        int bestSlot = -1;
        int bestScore = -1;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!isChestplate(stack)) continue;

            int score = chestplateScore(stack.getItem());
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.contains(DataComponentTypes.GLIDER)) return false;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId();
    }

    private boolean isElytra(ItemStack stack) {
        return !stack.isEmpty() && stack.contains(DataComponentTypes.GLIDER);
    }

    private int chestplateScore(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 6;
        if (item == Items.DIAMOND_CHESTPLATE) return 5;
        if (item == Items.IRON_CHESTPLATE) return 4;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 3;
        if (item == Items.GOLDEN_CHESTPLATE) return 2;
        if (item == Items.LEATHER_CHESTPLATE) return 1;
        return 0;
    }
}
