package com.nadaess.notaloneanymore;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notaloneanymore implements ModInitializer {
    public static final String MOD_ID = "notaloneanymore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static boolean showThoughtsInChat = true;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Notaloneanymore ИИ-Модификации...");
        loadConfig();

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
                    java.util.List<Villager> villagers = world.getEntitiesOfClass(Villager.class, area);

                    for (Villager v : villagers) {
                        if (v instanceof com.nadaess.notaloneanymore.util.DialogAgent agent) {
                            java.util.Optional<net.minecraft.core.GlobalPos> homePosOpt = v.getBrain().getMemory(MemoryModuleType.HOME);
                            java.util.Optional<net.minecraft.core.GlobalPos> jobPosOpt = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);

                            boolean eventSent = false;

                            if (homePosOpt.isPresent()) {
                                BlockPos homePos = homePosOpt.get().pos();
                                if (pos.closerThan(homePos, 8.0)) {
                                    agent.notAlone$triggerReactiveEvent("VANDALISM_HOME", "Игрок " + player.getName().getString() + " уничтожает блоки моего дома рядом с моей кроватью! Это погром!");
                                    eventSent = true;
                                }
                            }

                            if (!eventSent && jobPosOpt.isPresent()) {
                                BlockPos jobPos = jobPosOpt.get().pos();
                                if (pos.closerThan(jobPos, 6.0)) {
                                    agent.notAlone$triggerReactiveEvent("VANDALISM_WORK", "Игрок " + player.getName().getString() + " ломает блоки рядом с моим рабочим местом! Мой станок в опасности!");
                                    eventSent = true;
                                }
                            }

                            if (!eventSent) {
                                agent.notAlone$triggerReactiveEvent("MINING_INTEREST", "Игрок " + player.getName().getString() + " сломал блок " + blockName + " в пределах видимости. Просто работает.");
                            }
                        }
                    }
                }
            }
        });

        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            net.minecraft.world.level.Level world = entity.level();
            if (!world.isClientSide()) {
                net.minecraft.world.phys.AABB area = entity.getBoundingBox().inflate(12.0);
                java.util.List<Villager> villagers = world.getEntitiesOfClass(Villager.class, area);
                String victimName = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

                for (Villager v : villagers) {
                    if (v != entity && v instanceof com.nadaess.notaloneanymore.util.DialogAgent agent) {
                        agent.notAlone$triggerReactiveEvent("DEATH_NEARBY", "Рядом погиб объект: " + victimName);
                    }
                }
            }
        });

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> com.nadaess.notaloneanymore.MindCommand.register(dispatcher)
        );
    }

    private void loadConfig() {
        LOGGER.info("Конфигурация ИИ успешно загружена.");
    }
}