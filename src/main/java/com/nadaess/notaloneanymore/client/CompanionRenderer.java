package com.nadaess.notaloneanymore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nadaess.notaloneanymore.entity.CompanionEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;

/**
 * Рендерер компаньона — прямоугольный блок с текстурой TNT.
 * Временный, пока не готовы кастомные модели (Geckolib и т.д. убраны).
 * Копирует логику TntRenderer, но без фитиля/вздутия.
 */
public class CompanionRenderer extends EntityRenderer<CompanionEntity, CompanionRenderState> {

    public static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blockModelResolver;

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.shadowStrength = 0.8F;
        this.blockModelResolver = context.getBlockModelResolver();
    }

    @Override
    public CompanionRenderState createRenderState() {
        return new CompanionRenderState();
    }

    @Override
    public void extractRenderState(CompanionEntity entity, CompanionRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // Обновляем модель TNT-блока
        this.blockModelResolver.update(state.blockState, Blocks.TNT.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
    }

    @Override
    public void submit(CompanionRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        poseStack.pushPose();
        // Центрируем как TNT: поднять на 0.5, повернуть и отцентровать блок 1x1
        poseStack.translate(0.0F, 0.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));

        if (!state.blockState.isEmpty()) {
            // Рендер без оверлея и с обычным светом
            state.blockState.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
    }
}
