package com.nadaess.notaloneanymore;

import com.nadaess.notaloneanymore.entity.CompanionAgent;
import com.nadaess.notaloneanymore.entity.CompanionEntity;
import com.nadaess.notaloneanymore.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notaloneanymore implements ModInitializer {
    public static final String MOD_ID = "notaloneanymore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static boolean showThoughtsInChat = true;
    public static ModConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Notaloneanymore ИИ-Модификации (custom entity)...");
        config = ModConfig.load();

        // Регистрируем кастомную сущность и предмет-призыватель
        ModEntities.register();
        com.nadaess.notaloneanymore.item.ModItems.register();

        // === Полное удаление ванильных жителей ===
        // 1) ENTITY_LOAD — удаляем уже загруженных
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Villager) {
                entity.discard();
                LOGGER.info("Удалён ванильный житель {}", entity.getUUID());
            }
        });
        // 2) ALLOW_LOAD — запрещаем спавн новых (возвращаем false для Villager)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ALLOW_LOAD.register((entity, world, reason, loadedFromDisk) -> {
            if (entity instanceof Villager) {
                return false;
            }
            return true;
        });

        // Сенсор разрушения блоков (Вандализм) — для компаньонов, без HOME/JOB (у них нет Brain)
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!world.isClientSide()) {
                String blockName = state.getBlock().getName().getString();
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

                boolean isInterestingBlock = blockId.contains("wood") || blockId.contains("planks") ||
                        blockId.contains("log") || blockId.contains("door") ||
                        blockId.contains("glass") || blockId.contains("bed") ||
                        blockId.contains("fence") || blockId.contains("slab") ||
                        blockId.contains("stairs") || blockId.contains("stone_bricks") ||
                        blockId.contains("wool") || blockId.contains("brick");

                if (isInterestingBlock) {
                    net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(pos).inflate(15.0);
                    java.util.List<CompanionEntity> companions = world.getEntitiesOfClass(CompanionEntity.class, area);

                    for (CompanionEntity c : companions) {
                        // У компаньона нет HOME/JOB — просто MINING_INTEREST + пометка близости
                        double dist = Math.sqrt(c.blockPosition().distSqr(pos));
                        if (dist < 5.0) {
                            c.companion$triggerReactiveEvent("VANDALISM_NEARBY", "Игрок " + player.getName().getString() + " ломает " + blockName + " прямо рядом со мной! Это рядом!");
                        } else {
                            c.companion$triggerReactiveEvent("MINING_INTEREST", "Игрок " + player.getName().getString() + " сломал блок " + blockName + " в пределах видимости.");
                        }
                    }
                }
            }
        });

        // Сенсор фиксации смерти сущностей рядом — для компаньонов
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            net.minecraft.world.level.Level world = entity.level();
            if (!world.isClientSide()) {
                net.minecraft.world.phys.AABB area = entity.getBoundingBox().inflate(12.0);
                java.util.List<CompanionEntity> companions = world.getEntitiesOfClass(CompanionEntity.class, area);
                String victimName = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

                for (CompanionEntity c : companions) {
                    if (c != entity) {
                        c.companion$triggerReactiveEvent("DEATH_NEARBY", "Рядом погиб объект: " + victimName);
                    }
                }
            }
        });

        // Регистрация внутриигровых команд
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> com.nadaess.notaloneanymore.MindCommand.register(dispatcher)
        );
    }
}
