package com.nadaess.notaloneanymore.ai.brain.tasks;

import java.util.Map;

/**
 * Описывает один атомарный примитив (кусочек пазла) из очереди действий.
 * Каждый экземпляр — это одна команда: "walk_to", "jump", "wait", "look_at" и т.д.
 *
 * @param action название примитива (например "walk_to", "jump", "wait")
 * @param params  параметры команды (координаты x/y/z, ticks, target и т.д.)
 */
public record AtomicAction(String action, Map<String, Object> params) {

    /**
     * Безопасное извлечение int-параметра из карты params.
     *
     * @param key          ключ параметра
     * @param defaultValue значение по умолчанию, если ключ отсутствует или не число
     * @return значение int или defaultValue
     */
    public int getIntParam(String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) return defaultValue;
        Object val = params.get(key);
        if (val instanceof Number num) return num.intValue();
        return defaultValue;
    }
}
