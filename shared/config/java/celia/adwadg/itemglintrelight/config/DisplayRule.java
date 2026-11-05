package celia.adwadg.itemglintrelight.config;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public record DisplayRule(String id, String name, Mode mode, String target, String nbtPath, String nbtMatchMode, String nbtValue, int priority,
                          String outlineParameters, ItemGlintRelightConfig outlineConfig, List<NbtCondition> nbtConditions) {
    public DisplayRule {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        name = Objects.requireNonNullElse(name, "");
        mode = mode == null ? Mode.WHITELIST : mode;
        target = Objects.requireNonNullElse(target, "");
        nbtPath = Objects.requireNonNullElse(nbtPath, "");
        nbtMatchMode = Objects.requireNonNullElse(nbtMatchMode, "=");
        nbtValue = Objects.requireNonNullElse(nbtValue, "");
        outlineParameters = Objects.requireNonNullElse(outlineParameters, "");
        nbtConditions = nbtConditions == null || nbtConditions.isEmpty()
                ? List.of(new NbtCondition(nbtPath, nbtMatchMode, nbtValue))
                : List.copyOf(nbtConditions);
    }

    public DisplayRule(String id, String name, Mode mode, String target, String nbtPath, String nbtMatchMode, String nbtValue, int priority,
                       String outlineParameters, ItemGlintRelightConfig outlineConfig) {
        this(id, name, mode, target, nbtPath, nbtMatchMode, nbtValue, priority, outlineParameters, outlineConfig, null);
    }

    public record NbtCondition(String path, String matchMode, String value) {
        public NbtCondition {
            path = Objects.requireNonNullElse(path, "");
            matchMode = Objects.requireNonNullElse(matchMode, "=");
            value = Objects.requireNonNullElse(value, "");
        }
    }

    public enum Mode {
        WHITELIST,
        NBT_MATCH,
        BLACKLIST
    }
}
