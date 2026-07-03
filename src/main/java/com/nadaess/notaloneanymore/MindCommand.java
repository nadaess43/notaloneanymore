package com.nadaess.notaloneanymore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nadaess.notaloneanymore.util.DialogAgent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

public class MindCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> mindCommand = Commands.literal("mind")
                .requires(source -> true);

        // Базовый рейкаст взгляда на жителя
        mindCommand.executes(context -> {
            if (!(context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return 0;

            Villager target = null;
            double closestDist = 36.0;
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 viewVec = player.getViewVector(1.0F);

            List<Villager> localVillagers = player.level().getEntitiesOfClass(
                    Villager.class,
                    player.getBoundingBox().inflate(6.0)
            );

            for (Villager entity : localVillagers) {
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

            if (target instanceof DialogAgent agent) {
                player.sendSystemMessage(Component.literal("§6=== СОСТОЯНИЕ РАЗУМА ЖИТЕЛЯ ==="));
                player.sendSystemMessage(Component.literal("§eИмя: §f" + target.getName().getString()));
                player.sendSystemMessage(Component.literal("§eНавигация (navState): §b" + agent.notAlone$getNavState()));
                player.sendSystemMessage(Component.literal("§eАнимация (animState): §d" + agent.notAlone$getAnimState()));
                player.sendSystemMessage(Component.literal("§eЦель взгляда (target): §a" + agent.notAlone$getTargetName()));
                player.sendSystemMessage(Component.literal("§eГлубинная память: §7" + agent.notAlone$getLongTermMemory()));
                player.sendSystemMessage(Component.literal("§6============================="));
            } else {
                context.getSource().sendFailure(Component.literal("Подойди ближе и посмотри на жителя!"));
            }
            return 1;
        });

        // Переключатель вывода мыслей в чат
        mindCommand.then(Commands.literal("toggle")
                .executes(context -> {
                    Notaloneanymore.showThoughtsInChat = !Notaloneanymore.showThoughtsInChat;
                    String status = Notaloneanymore.showThoughtsInChat ? "§aВКЛЮЧЕНЫ§f" : "§cВЫКЛЮЧЕНЫ§f";
                    context.getSource().sendSuccess(() -> Component.literal("Мысли жителей теперь: " + status), true);
                    return 1;
                })
        );

        // НАСТРОЙКА КОНФИГА ИЗ ИГРЫ ПРЯМЫМИ КОМАНДАМИ
        // Используем greedyString() чтобы принимались URL, ключи и модели с символами / : .
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
            mindCommand.then(forceNode);
        }

        dispatcher.register(mindCommand);
    }

    private static Villager findVillagerByName(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        AABB area = new AABB(pos.x - 50, pos.y - 50, pos.z - 50, pos.x + 50, pos.y + 50, pos.z + 50);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        for (Villager v : villagers) {
            if (v.getCustomName() != null && v.getCustomName().getString().equalsIgnoreCase(name)) return v;
        }
        return null;
    }

    private static int executeRead(CommandSourceStack source, String name, String type) {
        Villager villager = findVillagerByName(source, name);
        if (villager == null || !(villager instanceof DialogAgent agent)) {
            source.sendFailure(Component.literal("Житель " + name + " не найден в радиусе 50 блоков."));
            return 0;
        }

        switch (type) {
            case "me" -> {
                source.sendSuccess(() -> Component.literal("§6--- ПАМЯТЬ ЖИТЕЛЯ " + name + " ---"), false);
                source.sendSuccess(() -> Component.literal("§7RAM: §e" + agent.notAlone$getRamMemory()), false);
                source.sendSuccess(() -> Component.literal("§7Глубинная Память: §e" + agent.notAlone$getLongTermMemory()), false);
                source.sendSuccess(() -> Component.literal("§7Когнитивная Инерция: §b" + agent.notAlone$getInertia() + "/100"), false);
            }
            case "st" -> {
                source.sendSuccess(() -> Component.literal("§6--- ГЕНОМ ИИ (" + name + ") ---"), false);
                agent.notAlone$getGenome().forEach((g, v) -> source.sendSuccess(() -> Component.literal("§7" + g + ": §e" + v + "/100"), false));
            }
            case "sc" -> {
                source.sendSuccess(() -> Component.literal("§6--- ДИНАМИЧЕСКИЕ ПОТРЕБНОСТИ (" + name + ") ---"), false);
                agent.notAlone$getNeeds().forEach((n, v) -> source.sendSuccess(() -> Component.literal("§b" + n + ": §f" + v + "/100"), false));
            }
            case "in" -> source.sendSuccess(() -> Component.literal("§6--- ИНВЕНТАРЬ --- \n§f" + villager.getInventory()), false);
            case "hi" -> source.sendSuccess(() -> Component.literal("§6--- ИСТОРИЯ ДИАЛОГА --- \n§7" + agent.notAlone$getChatHistoryForPrompt()), false);
        }
        return 1;
    }

    private static int executeChange(CommandSourceStack source, String name, String mod, String mode) {
        Villager villager = findVillagerByName(source, name);
        if (villager == null || !(villager instanceof DialogAgent agent)) return 0;

        try {
            String[] parts = mod.split(":");
            String key = parts[0].toLowerCase().trim();
            int value = Math.max(0, Math.min(100, Integer.parseInt(parts[1].trim())));

            if (mode.equals("st") && agent.notAlone$getGenome().containsKey(key)) {
                agent.notAlone$setGene(key, value);
                source.sendSuccess(() -> Component.literal("§a[Геном] " + key + " изменен на " + value), false);
                return 1;
            } else if (mode.equals("ne") && agent.notAlone$getNeeds().containsKey(key)) {
                agent.notAlone$setNeed(key, value);
                source.sendSuccess(() -> Component.literal("§b[Потребность] " + key + " изменена на " + value), false);
                return 1;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Ошибка формата. Пример: hunger:80"));
        }
        return 0;
    }

    private static int executeClearMemory(CommandSourceStack source, String name) {
        if (findVillagerByName(source, name) instanceof DialogAgent agent) {
            agent.notAlone$updateLongTermMemory("Память очищена кукловодом.");
            source.sendSuccess(() -> Component.literal("§aУ жителя " + name + " вызвана амнезия."), false);
            return 1;
        }
        return 0;
    }

    private static int executeForceNav(CommandSourceStack source, String name, Vec3 pos) {
        Villager villager = findVillagerByName(source, name);
        if (villager != null) {
            // Прямой приказ навигатору физического тела
            villager.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.6);

            source.sendSuccess(() -> Component.literal("§d[Кукловод] " + name + " направлен на " + pos), false);
            return 1;
        }
        return 0;
    }

    private static int executeForceAction(CommandSourceStack source, String name, String action) {
        if (findVillagerByName(source, name) instanceof DialogAgent agent) {
            agent.notAlone$executeComplexAction("none", "none", action, "neutral", null);
            source.sendSuccess(() -> Component.literal("§d[Кукловод] Жителю навязано действие: " + action), false);
            return 1;
        }
        return 0;
    }

    private static int executeTriggerEvent(CommandSourceStack source, String name, String event) {
        if (findVillagerByName(source, name) instanceof DialogAgent agent) {
            agent.notAlone$triggerReactiveEvent(event.toUpperCase(), "Кукловод спровоцировал стресс-тест.");
            source.sendSuccess(() -> Component.literal("§d[Кукловод] Вброшен триггер: " + event), false);
            return 1;
        }
        return 0;
    }
}