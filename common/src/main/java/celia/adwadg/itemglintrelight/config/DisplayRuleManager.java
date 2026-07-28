package celia.adwadg.itemglintrelight.config;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class DisplayRuleManager {
    public static final String FILE_NAME = "display_rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<DisplayRule> RULES = new ArrayList<>();
    private static Path rulesFile;
    private static long nextMatchDiagnosticMillis;

    private DisplayRuleManager() {
    }

    public static synchronized void register(Path configDirectory) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        if (rulesFile != null) return;
        rulesFile = configDirectory.resolve(FILE_NAME);
        load();
    }

    public static synchronized List<DisplayRule> rules() {
        ensureRegistered();
        return List.copyOf(RULES);
    }

    public static synchronized List<DisplayRule> rulesByPriority() {
        ensureRegistered();
        return RULES.stream().sorted(Comparator.comparingInt(DisplayRule::priority).reversed()
                .thenComparing(Comparator.comparingInt((DisplayRule rule) -> rule.mode() == DisplayRule.Mode.BLACKLIST ? 1 : 0).reversed())).toList();
    }

    public static synchronized ItemGlintRelightConfig resolve(ItemStack stack, ItemGlintRelightConfig fallback) {
        ensureRegistered();
        List<DisplayRule> orderedRules = rulesByPriority();
        for (DisplayRule rule : orderedRules) {
            if (!matches(rule, stack)) continue;
            logMatch(stack, rule);
            if (rule.mode() == DisplayRule.Mode.BLACKLIST) {
                ItemGlintRelightConfig disabled = fallback.copy();
                disabled.setOutlineEnabled(false);
                return disabled;
            }
            return rule.outlineConfig() == null ? fallback : rule.outlineConfig().copy();
        }
        if (orderedRules.isEmpty()) return fallback;
        DisplayRule highestPriorityRule = orderedRules.get(0);
        if (highestPriorityRule.mode() == DisplayRule.Mode.BLACKLIST) {
            return highestPriorityRule.outlineConfig() == null ? fallback : highestPriorityRule.outlineConfig().copy();
        }
        if (highestPriorityRule.mode() != DisplayRule.Mode.BLACKLIST) {
            ItemGlintRelightConfig disabled = fallback.copy();
            disabled.setOutlineEnabled(false);
            return disabled;
        }
        return fallback;
    }

    private static boolean matches(DisplayRule rule, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (rule.mode() == DisplayRule.Mode.NBT_MATCH) {
            return rule.nbtConditions().stream().allMatch(condition -> matchesNbtCondition(stack, condition));
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (rule.target().startsWith("@")) return itemId.startsWith(rule.target().substring(1) + ":");
        if (rule.target().startsWith("#")) {
            try {
                return stack.is(TagKey.create(Registries.ITEM, Identifier.parse(rule.target().substring(1))));
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return itemId.equals(rule.target());
    }

    private static boolean matchesNbtCondition(ItemStack stack, DisplayRule.NbtCondition condition) {
        Object component = componentValue(stack, condition.path());
        boolean empty = isEmptyComponent(component);
        boolean contains = condition.value().isBlank() ? empty : component != null && component.toString().contains(condition.value());
        return switch (condition.matchMode()) {
            case "!=", "!~", "!in", "!⊃", "!⊂" -> !contains;
            case ">", "<", ">=", "<=" -> matchesNumericCondition(component == null ? "" : component.toString(), condition, contains);
            default -> contains;
        };
    }

    private static Object componentValue(ItemStack stack, String path) {
        try {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(path));
            return type == null ? null : stack.get(type);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isEmptyComponent(Object component) {
        if (component == null) return true;
        if (component instanceof ItemEnchantments enchantments) return enchantments.isEmpty();
        if (component instanceof java.util.Collection<?> collection) return collection.isEmpty();
        if (component instanceof java.util.Map<?, ?> map) return map.isEmpty();
        String value = component.toString();
        return value.isBlank() || value.equals("{}") || value.equals("[]") || value.equals("\"\"") || value.equals("null");
    }

    private static boolean matchesNumericCondition(String components, DisplayRule.NbtCondition condition, boolean fallback) {
        int pathIndex = components.indexOf(condition.path());
        if (pathIndex < 0) return false;
        int valueStart = components.indexOf('=', pathIndex);
        if (valueStart < 0) valueStart = components.indexOf(':', pathIndex);
        if (valueStart < 0) return fallback;
        int valueEnd = valueStart + 1;
        while (valueEnd < components.length() && "0123456789+-.".indexOf(components.charAt(valueEnd)) >= 0) valueEnd++;
        try {
            double actual = Double.parseDouble(components.substring(valueStart + 1, valueEnd));
            double expected = Double.parseDouble(condition.value());
            return switch (condition.matchMode()) {
                case ">" -> actual > expected;
                case "<" -> actual < expected;
                case ">=" -> actual >= expected;
                case "<=" -> actual <= expected;
                default -> fallback;
            };
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void logMatch(ItemStack stack, DisplayRule rule) {
        long now = System.currentTimeMillis();
        if (now < nextMatchDiagnosticMillis) return;
        nextMatchDiagnosticMillis = now + 250L;
        ItemGlintRelight.LOGGER.info("[DisplayRule] matched item={} rule={} mode={} priority={} custom={}",
                BuiltInRegistries.ITEM.getKey(stack.getItem()), rule.id(), rule.mode(), rule.priority(), rule.outlineConfig() != null);
    }

    public static synchronized void add(DisplayRule rule) {
        ensureRegistered();
        RULES.add(Objects.requireNonNull(rule, "rule"));
        save();
    }

    public static synchronized void replace(DisplayRule rule) {
        ensureRegistered();
        for (int index = 0; index < RULES.size(); index++) {
            if (RULES.get(index).id().equals(rule.id())) {
                RULES.set(index, Objects.requireNonNull(rule, "rule"));
                save();
                return;
            }
        }
        add(rule);
    }

    public static synchronized void remove(String id) {
        ensureRegistered();
        if (RULES.removeIf(rule -> rule.id().equals(id))) save();
    }

    public static synchronized void save() {
        ensureRegistered();
        try {
            Files.createDirectories(rulesFile.getParent());
            try (Writer writer = Files.newBufferedWriter(rulesFile)) {
                GSON.toJson(RULES, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save display rules to " + rulesFile, exception);
        }
    }

    private static void load() {
        RULES.clear();
        if (!Files.isRegularFile(rulesFile)) return;
        try (Reader reader = Files.newBufferedReader(rulesFile)) {
            DisplayRule[] loaded = GSON.fromJson(reader, DisplayRule[].class);
            if (loaded != null) {
                for (DisplayRule rule : loaded) {
                    if (rule != null) RULES.add(rule);
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to load display rules from " + rulesFile, exception);
        }
    }

    private static void ensureRegistered() {
        if (rulesFile == null) throw new IllegalStateException("Display rule storage has not been registered");
    }
}
