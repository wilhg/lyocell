package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataModule implements LyocellModule {
    private static final Map<String, Object> SHARED_ARRAYS = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "lyocell/data";
    }

    @Override
    public String getJsSource() {
        return """
            const Data = globalThis.LyocellData;
            export class SharedArray {
                constructor(name, fn) {
                    return Data.getOrCreateSharedArray(name, fn);
                }
            }
            export default { SharedArray };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellData", this);
    }

    @HostAccess.Export
    public Object getOrCreateSharedArray(String name, Value factory) {
        return SHARED_ARRAYS.computeIfAbsent(name, k -> {
            if (factory.canExecute()) {
                Value result = factory.execute();
                // To share across contexts, we must ensure it's not a Graal proxy tied to a context.
                // We'll use a simple recursive conversion for common types.
                return toJava(result);
            }
            return null;
        });
    }

    private Object toJava(Value value) {
        if (value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            return value.asDouble();
        }
        if (value.isString()) return value.asString();
        if (value.hasArrayElements()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(toJava(value.getArrayElement(i)));
            }
            return java.util.Collections.unmodifiableList(list);
        }
        if (value.hasMembers()) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, toJava(value.getMember(key)));
            }
            return java.util.Collections.unmodifiableMap(map);
        }
        return value.as(Object.class);
    }
}
