package kani.addon.mixin;

import java.util.ArrayDeque;
import java.util.Deque;
import kani.addon.modules.HeadSize;
import meteordevelopment.meteorclient.mixininterface.IEntityRenderState;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelCommandRenderer.class)
public abstract class ModelCommandRendererMixin {
    @Unique
    private final Deque<HeadScaleContext> kani$headScaleStack = new ArrayDeque<>();
    @Unique
    private static final HeadScaleContext KANI$NO_SCALE = new HeadScaleContext(null, 0.0f, 0.0f, 0.0f);

    @Inject(
        method = "render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setAngles(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
        )
    )
    private <S> void kani$applyHeadScaling(
        OrderedRenderCommandQueueImpl.ModelCommand<S> modelCommand,
        RenderLayer renderLayer,
        VertexConsumer vertexConsumer,
        OutlineVertexConsumerProvider outlineProvider,
        VertexConsumerProvider.Immediate crumblingOverlayProvider,
        CallbackInfo ci
    ) {
        HeadScaleContext context = KANI$NO_SCALE;

        if (modelCommand.model() instanceof PlayerEntityModel playerModel) {
            Entity entity = modelCommand.state() instanceof IEntityRenderState accessor ? accessor.meteor$getEntity() : null;
            if (entity instanceof AbstractClientPlayerEntity player) {
                HeadSize headSize = Modules.get().get(HeadSize.class);
                if (headSize != null) {
                    float headScale = headSize.scaleFor(player);
                    if (Math.abs(headScale - 1.0f) >= 1e-3f) {
                        ModelPart head = playerModel.getHead();
                        context = new HeadScaleContext(playerModel, head.xScale, head.yScale, head.zScale);
                        head.xScale *= headScale;
                        head.yScale *= headScale;
                        head.zScale *= headScale;
                    }
                }
            }
        }

        kani$headScaleStack.push(context);
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
        at = @At("RETURN")
    )
    private <S> void kani$resetHeadScaling(
        OrderedRenderCommandQueueImpl.ModelCommand<S> modelCommand,
        RenderLayer renderLayer,
        VertexConsumer vertexConsumer,
        OutlineVertexConsumerProvider outlineProvider,
        VertexConsumerProvider.Immediate crumblingOverlayProvider,
        CallbackInfo ci
    ) {
        HeadScaleContext context = kani$headScaleStack.poll();
        if (context == null || context == KANI$NO_SCALE || context.model() == null) return;

        ModelPart head = context.model().getHead();
        head.xScale = context.prevX();
        head.yScale = context.prevY();
        head.zScale = context.prevZ();
    }

    @Unique
    private record HeadScaleContext(PlayerEntityModel model, float prevX, float prevY, float prevZ) {
    }
}
