package com.nadaess.notaloneanymore.client;

import com.nadaess.notaloneanymore.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class NotAloneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.COMPANION, CompanionRenderer::new);
    }
}
