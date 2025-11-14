package kani.addon.hud;

import kani.addon.KaniAddon;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class FloorDistanceHud extends HudElement {
    public static final HudElementInfo<FloorDistanceHud> INFO = new HudElementInfo<>(KaniAddon.HUD_GROUP, "floor-distance", "Shows how far your feet are from the first solid block below you.", FloorDistanceHud::new);

    private static final double EPSILON = 1e-3;
    private final BlockPos.Mutable mutable = new BlockPos.Mutable();

    public FloorDistanceHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        String text = "Floor: -";

        if (Utils.canUpdate()) {
            double distance = calculateDistance();
            if (!Double.isNaN(distance)) text = String.format(Locale.US, "Floor: %.2f", distance);
        }

        setSize(renderer.textWidth(text, true), renderer.textHeight(true));
        renderer.text(text, x, y, Color.WHITE, true);
    }

    private double calculateDistance() {
        ClientWorld world = mc.world;
        if (world == null) return Double.NaN;

        PlayerEntity player = mc.player;
        if (player == null) return Double.NaN;

        Box box = player.getBoundingBox();

        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.floor(box.maxX - EPSILON);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.floor(box.maxZ - EPSILON);
        int startY = MathHelper.floor(box.minY - EPSILON);

        for (int y = startY; y >= world.getBottomY(); y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) continue;

                    VoxelShape shape = state.getCollisionShape(world, mutable);
                    if (shape.isEmpty()) continue;

                    double topY = y + shape.getMax(Direction.Axis.Y);
                    if (topY <= box.minY + EPSILON) {
                        return Math.max(0, box.minY - topY);
                    }
                }
            }
        }

        return Math.max(0, box.minY - world.getBottomY());
    }
}
