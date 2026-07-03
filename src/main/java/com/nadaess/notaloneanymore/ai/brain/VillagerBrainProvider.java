package com.nadaess.notaloneanymore.ai.brain;

import com.nadaess.notaloneanymore.ai.brain.tasks.AutonomousThoughtTask;
import com.nadaess.notaloneanymore.ai.brain.tasks.ExecuteAiOrderTask;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.tslat.smartbrainlib.api.core.ActivityBuilder;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.custom.NearbyBlocksSensor;
import net.tslat.smartbrainlib.api.core.sensor.custom.NearbyItemsSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyPlayersSensor;

import java.util.List;
import java.util.Set;

/**
 * Провайдер конфигурации мозга жителя для SmartBrainLib.
 * Содержит фабричные методы для сборки SBL-мозга,
 * полностью замещающего ванильные активности жителей.
 */
public class VillagerBrainProvider {

    /**
     * Возвращает пустой набор сенсоров — мы не используем
     * сенсорную систему SBL для автономного ИИ.
     */
    public static List<? extends ExtendedSensor<?>> getSensors() {
        return List.of();
    }

    /**
     * Always-running поведения: наши кастомные SBL-задачи,
     * которые работают вне зависимости от текущей активности.
     */
    public static List<? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<?>> getAlwaysRunningBehaviours() {
        return List.of(
                new AutonomousThoughtTask() // Оставляем только мысленный таск, он работает отлично
        );
    }

    /**
     * Группа CORE — пустая, т.к. вся логика в always-running.
     * Очищаем все ванильные CORE-активности.
     */
    public static ActivityBuilder<?> getCoreBehaviourGroup() {
        return ActivityBuilder.create(Activity.CORE);
    }

    /**
     * Группа IDLE — пустая, отключаем ванильное безделье.
     */
    public static ActivityBuilder<?> getIdleBehaviourGroup() {
        return ActivityBuilder.create(Activity.IDLE);
    }

    /**
     * Группа WORK — пустая, отключаем ванильную работу.
     */
    public static ActivityBuilder<?> getWorkBehaviourGroup() {
        return ActivityBuilder.create(Activity.WORK);
    }

    /**
     * Группа MEET — пустая, отключаем ванильные встречи.
     */
    public static ActivityBuilder<?> getMeetBehaviourGroup() {
        return ActivityBuilder.create(Activity.MEET);
    }

    /**
     * Группа REST — пустая, отключаем ванильный отдых.
     */
    public static ActivityBuilder<?> getRestBehaviourGroup() {
        return ActivityBuilder.create(Activity.REST);
    }

    /**
     * Группа FIGHT — пустая, отключаем ванильный бой.
     */
    public static ActivityBuilder<?> getFightBehaviourGroup() {
        return ActivityBuilder.create(Activity.FIGHT);
    }

    /**
     * Группа PANIC — пустая, отключаем ванильную панику.
     */
    public static ActivityBuilder<?> getPanicBehaviourGroup() {
        return ActivityBuilder.create(Activity.PANIC);
    }

    /**
     * Группа AVOID — пустая.
     */
    public static ActivityBuilder<?> getAvoidBehaviourGroup() {
        return ActivityBuilder.create(Activity.AVOID);
    }

    /**
     * Группа HIDE — пустая.
     */
    public static ActivityBuilder<?> getHideBehaviourGroup() {
        return ActivityBuilder.create(Activity.HIDE);
    }

    /**
     * Всегда запущенные активности (только CORE, хотя он пустой).
     */
    public static Set<Activity> getAlwaysRunningActivities() {
        return Set.of(Activity.CORE);
    }

    /**
     * Активность по умолчанию — CORE.
     */
    public static Activity getDefaultActivity() {
        return Activity.CORE;
    }

    /**
     * Список активностей, которые не должны активироваться
     * через расписание. Отключаем всё ванильное.
     */
    public static Activity[] getActivityActivationPriority() {
        return new Activity[]{
                Activity.CORE,
                Activity.IDLE,
                Activity.WORK,
                Activity.MEET,
                Activity.REST,
                Activity.FIGHT,
                Activity.PANIC,
                Activity.AVOID,
                Activity.HIDE
        };
    }
}
