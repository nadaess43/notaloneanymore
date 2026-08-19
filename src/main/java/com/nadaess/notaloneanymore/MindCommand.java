package com.nadaess.notaloneanymore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nadaess.notaloneanymore.ai.brain.tasks.AtomicAction;
import com.nadaess.notaloneanymore.entity.CompanionAgent;
import com.nadaess.notaloneanymore.entity.CompanionAiState;
import com.nadaess.notaloneanymore.entity.CompanionEntity;
import com.nadaess.notaloneanymore.entity.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MindCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> mindCommand = Commands.literal("mind")
                .requires(source -> true);

        // Базовый рейкаст взгляда на компаньона
        mindCommand.executes(context -> {
            if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return 0;

            CompanionEntity target = null;
            double closestDist = 36.0;
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 viewVec = player.getViewVector(1.0F);

            List<CompanionEntity> local = player.level().getEntitiesOfClass(
                    CompanionEntity.class,
                    player.getBoundingBox().inflate(6.0)
            );

            for (CompanionEntity entity : local) {
                Vec3 entityPos = entity.position().add(0, entity.getEyeHeight(), 0);
                Vec3 toEntity = entityPos.subtract(eyePos);
                Vec3 normalizedToEntity = toEntity.normalize();

                if (viewVec.dot(normalizedToEntity) > 0.82) {
                    double dist = player.distanceToSqr(entity);
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = entity;
                    }
                }
            }

            if (target instanceof CompanionAgent agent) {
                player.sendSystemMessage(Component.literal("§6=== СОСТОЯНИЕ КОМПАНЬОНА ==="));
                player.sendSystemMessage(Component.literal("§eИмя: §f" + target.getName().getString()));
                player.sendSystemMessage(Component.literal("§eНавигация (navState): §b" + agent.companion$getNavState()));
                player.sendSystemMessage(Component.literal("§eАнимация (animState): §d" + agent.companion$getAnimState()));
                player.sendSystemMessage(Component.literal("§eЦель взгляда (target): §a" + agent.companion$getTargetName()));
                player.sendSystemMessage(Component.literal("§eГлубинная память: §7" + agent.companion$getLongTermMemory()));
                player.sendSystemMessage(Component.literal("§6============================="));
            } else {
                context.getSource().sendFailure(Component.literal("Подойди ближе и посмотри на компаньона (TNT-блок)!"));
            }
            return 1;
        });

        // Переключатель вывода мыслей в чат
        mindCommand.then(Commands.literal("toggle")
                .executes(context -> {
                    Notaloneanymore.showThoughtsInChat = !Notaloneanymore.showThoughtsInChat;
                    String status = Notaloneanymore.showThoughtsInChat ? "§aВКЛЮЧЕНЫ§f" : "§cВЫКЛЮЧЕНЫ§f";
                    context.getSource().sendSuccess(() -> Component.literal("Мысли компаньонов теперь: " + status), true);
                    return 1;
                })
        );

        // Спавн компаньона
        mindCommand.then(Commands.literal("spawn")
                .executes(ctx -> executeSpawn(ctx.getSource(), null))
                .then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))
        );

        // НАСТРОЙКА КОНФИГА ИЗ ИГРЫ ПРЯМЫМИ КОМАНДАМИ
        mindCommand.then(Commands.literal("config")
                .then(Commands.literal("apikey").then(Commands.argument("key", StringArgumentType.greedyString()).executes(ctx -> {
                    Notaloneanymore.config.apiKey = StringArgumentType.getString(ctx, "key");
                    Notaloneanymore.config.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Config] API-ключ успешно обновлен и сохранен!"), true);
                    return 1;
                })))
                .then(Commands.literal("apiurl").then(Commands.argument("url", StringArgumentType.greedyString()).executes(ctx -> {
                    Notaloneanymore.config.apiUrl = StringArgumentType.getString(ctx, "url");
                    Notaloneanymore.config.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Config] API URL успешно изменен!"), true);
                    return 1;
                })))
                .then(Commands.literal("apimodel").then(Commands.argument("model", StringArgumentType.greedyString()).executes(ctx -> {
                    Notaloneanymore.config.modelName = StringArgumentType.getString(ctx, "model");
                    Notaloneanymore.config.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Config] Используемая модель изменена на: " + Notaloneanymore.config.modelName), true);
                    return 1;
                })))
                .then(Commands.literal("apitemp").then(Commands.argument("temp", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                    Notaloneanymore.config.aiTemperature = DoubleArgumentType.getDouble(ctx, "temp");
                    Notaloneanymore.config.save();
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Config] Температура ИИ установлена на: " + Notaloneanymore.config.aiTemperature), true);
                    return 1;
                })))
        );

        // Генерация алиасов для чтения (read) и изменения (change/force)
        for (String readAlias : new String[]{"read", "re"}) {
            LiteralArgumentBuilder<CommandSourceStack> readNode = Commands.literal(readAlias);
            for (String memAlias : new String[]{"memory", "me"}) {
                readNode.then(Commands.literal(memAlias).then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeRead(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "me"))));
            }
            for (String statsAlias : new String[]{"stats", "st"}) {
                readNode.then(Commands.literal(statsAlias).then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeRead(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "st"))));
            }
            for (String schedAlias : new String[]{"schedule", "sc"}) {
                readNode.then(Commands.literal(schedAlias).then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeRead(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "sc"))));
            }
            for (String invAlias : new String[]{"inventory", "in"}) {
                readNode.then(Commands.literal(invAlias).then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeRead(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "in"))));
            }
            for (String histAlias : new String[]{"history", "hi"}) {
                readNode.then(Commands.literal(histAlias).then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeRead(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "hi"))));
            }
            mindCommand.then(readNode);
        }

        for (String changeAlias : new String[]{"change", "ch"}) {
            LiteralArgumentBuilder<CommandSourceStack> changeNode = Commands.literal(changeAlias);
            for (String statsAlias : new String[]{"stats", "st"}) {
                changeNode.then(Commands.literal(statsAlias).then(Commands.argument("name", StringArgumentType.string()).then(Commands.argument("mod", StringArgumentType.greedyString()).executes(ctx -> executeChange(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "mod"), "st")))));
            }
            for (String needsAlias : new String[]{"needs", "ne"}) {
                changeNode.then(Commands.literal(needsAlias).then(Commands.argument("name", StringArgumentType.string()).then(Commands.argument("mod", StringArgumentType.greedyString()).executes(ctx -> executeChange(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "mod"), "ne")))));
            }
            for (String memAlias : new String[]{"memory", "me"}) {
                changeNode.then(Commands.literal(memAlias).then(Commands.argument("name", StringArgumentType.string()).then(Commands.literal("clear").executes(ctx -> executeClearMemory(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
            }
            mindCommand.then(changeNode);
        }

        for (String forceAlias : new String[]{"force", "fo"}) {
            LiteralArgumentBuilder<CommandSourceStack> forceNode = Commands.literal(forceAlias);
            for (String navAlias : new String[]{"navigation", "na"}) {
                forceNode.then(Commands.literal(navAlias).then(Commands.argument("name", StringArgumentType.string()).then(Commands.argument("pos", Vec3Argument.vec3()).executes(ctx -> executeForceNav(ctx.getSource(), StringArgumentType.getString(ctx, "name"), Vec3Argument.getVec3(ctx, "pos"))))));
            }
            for (String actAlias : new String[]{"action", "ac"}) {
                forceNode.then(Commands.literal(actAlias).then(Commands.argument("name", StringArgumentType.string()).then(Commands.argument("action", StringArgumentType.greedyString()).executes(ctx -> executeForceAction(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "action"))))));
            }
            for (String evtAlias : new String[]{"event", "ev"}) {
                forceNode.then(Commands.literal(evtAlias).then(Commands.argument("name", StringArgumentType.string()).then(Commands.argument("event", StringArgumentType.greedyString()).executes(ctx -> executeTriggerEvent(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "event"))))));
            }
            forceNode.then(Commands.literal("test").then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> executeTestSequence(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
            mindCommand.then(forceNode);
        }

        dispatcher.register(mindCommand);
    }

    private static CompanionEntity findCompanionByName(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        AABB area = new AABB(pos.x - 50, pos.y - 50, pos.z - 50, pos.x + 50, pos.y + 50, pos.z + 50);
        List<CompanionEntity> entities = level.getEntitiesOfClass(CompanionEntity.class, area);
        for (CompanionEntity c : entities) {
            if (c.getCustomName() != null && c.getCustomName().getString().equalsIgnoreCase(name)) return c;
            // также поиск по id без учета customName: если имя == тип + uuid substring? Для удобства — если entity без имени, но запрос == "companion" вернем первого
            if (name.equalsIgnoreCase("companion") && entities.size() == 1) return c;
        }
        // Fallback: если точного совпадения нет, вернем ближайшего с похожим префиксом
        for (CompanionEntity c : entities) {
            String cname = c.hasCustomName() ? c.getCustomName().getString() : "companion";
            if (cname.toLowerCase().contains(name.toLowerCase())) return c;
        }
        return null;
    }

    private static int executeSpawn(CommandSourceStack source, String customName) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        CompanionEntity companion = new CompanionEntity(ModEntities.COMPANION, level);
        companion.setPos(pos.x, pos.y, pos.z);
        if (customName != null && !customName.isBlank()) {
            companion.setCustomName(Component.literal(customName));
            companion.setCustomNameVisible(true);
        } else {
            companion.setCustomName(Component.literal("Компаньон"));
            companion.setCustomNameVisible(true);
        }
        level.addFreshEntity(companion);
        source.sendSuccess(() -> Component.literal("§a[Спавн] Компаньон " + (customName != null ? customName : "Компаньон") + " создан на " + pos), true);
        return 1;
    }

    private static int executeRead(CommandSourceStack source, String name, String type) {
        CompanionEntity companion = findCompanionByName(source, name);
        if (companion == null) {
            source.sendFailure(Component.literal("Компаньон " + name + " не найден в радиусе 50 блоков. Используй /mind spawn " + name + " для создания."));
            return 0;
        }
        CompanionAgent agent = companion;

        switch (type) {
            case "me" -> {
                source.sendSuccess(() -> Component.literal("§6--- ПАМЯТЬ КОМПАНЬОНА " + name + " ---"), false);
                source.sendSuccess(() -> Component.literal("§7RAM: §e" + agent.companion$getRamMemory()), false);
                source.sendSuccess(() -> Component.literal("§7Глубинная Память: §e" + agent.companion$getLongTermMemory()), false);
                source.sendSuccess(() -> Component.literal("§7Когнитивная Инерция: §b" + agent.companion$getInertia() + "/100"), false);
            }
            case "st" -> {
                source.sendSuccess(() -> Component.literal("§6--- ГЕНОМ ИИ (" + name + ") ---"), false);
                agent.companion$getGenome().forEach((g, v) -> source.sendSuccess(() -> Component.literal("§7" + g + ": §e" + v + "/100"), false));
            }
            case "sc" -> {
                source.sendSuccess(() -> Component.literal("§6--- ДИНАМИЧЕСКИЕ ПОТРЕБНОСТИ (" + name + ") ---"), false);
                agent.companion$getNeeds().forEach((n, v) -> source.sendSuccess(() -> Component.literal("§b" + n + ": §f" + v + "/100"), false));
            }
            case "in" -> source.sendSuccess(() -> Component.literal("§6--- ИНВЕНТАРЬ --- \n§f" + companion.getInventory()), false);
            case "hi" -> source.sendSuccess(() -> Component.literal("§6--- ИСТОРИЯ ДИАЛОГА --- \n§7" + agent.companion$getChatHistoryForPrompt()), false);
        }
        return 1;
    }

    private static int executeChange(CommandSourceStack source, String name, String mod, String mode) {
        CompanionEntity companion = findCompanionByName(source, name);
        if (companion == null) return 0;
        CompanionAgent agent = companion;

        try {
            String[] parts = mod.split(":");
            String key = parts[0].toLowerCase().trim();
            int value = Math.max(0, Math.min(100, Integer.parseInt(parts[1].trim())));

            if (mode.equals("st") && agent.companion$getGenome().containsKey(key)) {
                agent.companion$setGene(key, value);
                source.sendSuccess(() -> Component.literal("§a[Геном] " + key + " изменен на " + value), false);
                return 1;
            } else if (mode.equals("ne") && agent.companion$getNeeds().containsKey(key)) {
                agent.companion$setNeed(key, value);
                source.sendSuccess(() -> Component.literal("§b[Потребность] " + key + " изменена на " + value), false);
                return 1;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Ошибка формата. Пример: hunger:80"));
        }
        return 0;
    }

    private static int executeClearMemory(CommandSourceStack source, String name) {
        CompanionEntity c = findCompanionByName(source, name);
        if (c != null) {
            c.companion$updateLongTermMemory("Память очищена кукловодом.");
            source.sendSuccess(() -> Component.literal("§aУ компаньона " + name + " вызвана амнезия."), false);
            return 1;
        }
        return 0;
    }

    private static int executeForceNav(CommandSourceStack source, String name, Vec3 pos) {
        CompanionEntity companion = findCompanionByName(source, name);
        if (companion != null) {
            companion.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.6);
            source.sendSuccess(() -> Component.literal("§d[Кукловод] " + name + " направлен на " + pos), false);
            return 1;
        }
        return 0;
    }

    private static int executeForceAction(CommandSourceStack source, String name, String action) {
        CompanionEntity c = findCompanionByName(source, name);
        if (c != null) {
            c.companion$getAiState().setActionSequence(java.util.List.of(
                new AtomicAction(action, java.util.Map.of())
            ));
            source.sendSuccess(() -> Component.literal("§d[Кукловод] Действие добавлено в очередь: " + action), false);
            return 1;
        }
        return 0;
    }

    private static int executeTriggerEvent(CommandSourceStack source, String name, String event) {
        CompanionEntity c = findCompanionByName(source, name);
        if (c != null) {
            c.companion$triggerReactiveEvent(event.toUpperCase(), "Кукловод спровоцировал стресс-тест.");
            source.sendSuccess(() -> Component.literal("§d[Кукловод] Вброшен триггер: " + event), false);
            return 1;
        }
        return 0;
    }

    private static int executeTestSequence(CommandSourceStack source, String name) {
        CompanionEntity companion = findCompanionByName(source, name);
        if (companion == null) {
            source.sendFailure(Component.literal("Компаньон " + name + " не найден."));
            return 0;
        }

        CompanionAiState state = companion.companion$getAiState();
        if (state == null) {
            source.sendFailure(Component.literal("У компаньона нет AiState."));
            return 0;
        }

        int doorX = companion.getBlockX() + (int) Math.round(companion.getForward().x * 2);
        int doorY = companion.getBlockY();
        int doorZ = companion.getBlockZ() + (int) Math.round(companion.getForward().z * 2);

        List<AtomicAction> fullTest = List.of(
            new AtomicAction("equip", java.util.Map.of("item", "minecraft:iron_sword", "slot", "main_hand")),
            new AtomicAction("wait", java.util.Map.of("ticks", 20)),
            new AtomicAction("jump", java.util.Map.of()),
            new AtomicAction("walk_to", java.util.Map.of("x", doorX, "y", doorY, "z", doorZ)),
            new AtomicAction("toggle_door", java.util.Map.of("x", doorX, "y", doorY, "z", doorZ)),
            new AtomicAction("wait", java.util.Map.of("ticks", 10)),
            new AtomicAction("drop_item", java.util.Map.of("item", "minecraft:iron_sword", "count", 1))
        );

        state.setActionSequence(fullTest);
        source.sendSuccess(() -> Component.literal("§d[Тест] Компаньону " + name + " загружена тестовая секвенция из 7 шагов!"), false);
        source.sendSuccess(() -> Component.literal("§7 1. equip iron_sword → 2. wait 20t → 3. jump → 4. walk_to door → 5. toggle_door → 6. wait 10t → 7. drop_item"), false);
        return 1;
    }
}
