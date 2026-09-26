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
    private static long nextComponentPathDiagnosticMillis;
    private static List<DisplayRule> orderedRuleCache;
    private static int orderedRuleCacheVersion = -1;
    private static int rulesVersion;

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

    /**
     * The ordered list only changes when the rule set changes. Caching it removes a stream sort
     * and two list allocations from every item resolution, which runs once per GUI slot per frame.
     */
    public static synchronized List<DisplayRule> rulesByPriority() {
        ensureRegistered();
        if (orderedRuleCache == null || orderedRuleCacheVersion != rulesVersion) {
            orderedRuleCache = RULES.stream().sorted(Comparator.comparingInt(DisplayRule::priority).reversed()
                    .thenComparing(Comparator.comparingInt((DisplayRule rule) -> rule.mode() == DisplayRule.Mode.BLACKLIST ? 1 : 0).reversed())).toList();
            orderedRuleCacheVersion = rulesVersion;
        }
        return orderedRuleCache;
    }

    public static synchronized ItemGlintRelightConfig resolve(ItemStack stack, ItemGlintRelightConfig fallback) {
        ensureRegistered();
        List<DisplayRule> orderedRules = rulesByPriority();
        if (orderedRules.isEmpty()) return fallback;
        // Resolved once per call rather than once per rule; getKey(...).toString() allocates.
        String itemId = stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (DisplayRule rule : orderedRules) {
            if (!matches(rule, stack, itemId)) continue;
            logMatch(stack, rule);
            if (rule.mode() == DisplayRule.Mode.BLACKLIST) {
                ItemGlintRelightConfig disabled = fallback.copy();
                disabled.setOutlineEnabled(false);
                return disabled;
            }
            return rule.outlineConfig() == null ? fallback : rule.outlineConfig().copy();
        }
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

    private static boolean matches(DisplayRule rule, ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty()) return false;
        if (rule.mode() == DisplayRule.Mode.NBT_MATCH) {
            return rule.nbtConditions().stream().allMatch(condition -> matchesNbtCondition(stack, condition));
        }
        if (itemId == null) return false;
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
        String text = component == null ? "" : component.toString();
        boolean contains = condition.value().isBlank() ? empty : component != null && text.contains(condition.value());
        // The mirror of "contains", and a mode of its own in the rule editor on both supported
        // versions. Folding it into "contains" made four of the ten match modes indistinguishable.
        boolean containedBy = !condition.value().isBlank() && condition.value().contains(text);
        return switch (condition.matchMode()) {
            case "!=", "!~", "!in", "!⊃" -> !contains;
            case "!⊂" -> !containedBy;
            case "⊂" -> containedBy;
            case ">", "<", ">=", "<=" -> matchesNumericCondition(component, condition, contains);
            default -> contains;
        };
    }

    /**
     * Resolves the rule editor's "NBT path" against the item's data.
     *
     * <p>Both supported versions store item data as <em>components</em>, so the field takes a component
     * id ({@code minecraft:enchantments}, {@code minecraft:damage}, ...) and not a legacy NBT path --
     * the label is a leftover from the NBT era. A bare name is accepted as {@code minecraft:<name>}
     * because that is what the field invites people to type, and a path that resolves to no component
     * at all is reported (rate limited) instead of silently never matching.
     */
    private static Object componentValue(ItemStack stack, String path) {
        DataComponentType<?> type = componentType(path);
        return type == null ? null : stack.get(type);
    }

    private static DataComponentType<?> componentType(String path) {
        if (path == null || path.isBlank()) return null;
        DataComponentType<?> type = lookupComponent(path);
        if (type == null) type = lookupComponent("minecraft:" + path);
        if (type == null) reportUnresolvedComponentPath(path);
        return type;
    }

    private static DataComponentType<?> lookupComponent(String id) {
        try {
            return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void reportUnresolvedComponentPath(String path) {
        long now = System.currentTimeMillis();
        if (now < nextComponentPathDiagnosticMillis) return;
        nextComponentPathDiagnosticMillis = now + 5000L;
        ItemGlintRelight.LOGGER.warn("[DisplayRule] no data component matches NBT path '{}'; the field takes a component id such as minecraft:enchantments",
                path);
    }

    private static boolean isEmptyComponent(Object component) {
        if (component == null) return true;
        if (component instanceof ItemEnchantments enchantments) return enchantments.isEmpty();
        if (component instanceof java.util.Collection<?> collection) return collection.isEmpty();
        if (component instanceof java.util.Map<?, ?> map) return map.isEmpty();
        String value = component.toString();
        return value.isBlank() || value.equals("{}") || value.equals("[]") || value.equals("\"\"") || value.equals("null");
    }

    /**
     * Compares a component numerically.
     *
     * <p>A scalar component prints as the number itself ({@code 5} for {@code minecraft:damage}), while a
     * map-like one prints {@code key=value} entries; the entry the path names is tried first and the
     * plain value is the fallback, so both shapes compare. The previous version required the path to
     * appear inside the component's own text and otherwise failed outright, which made every numeric
     * condition on a scalar component silently never match.
     */
    private static boolean matchesNumericCondition(Object component, DisplayRule.NbtCondition condition, boolean fallback) {
        if (component == null) return false;
        String text = component.toString();
        int pathIndex = text.indexOf(condition.path());
        Double actual = firstNumber(text, pathIndex < 0 ? 0 : pathIndex);
        if (actual == null) actual = firstNumber(text, 0);
        if (actual == null) return false;
        double expected;
        try {
            expected = Double.parseDouble(condition.value().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
        return switch (condition.matchMode()) {
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            default -> fallback;
        };
    }

    /** First number at or after {@code from}; null when the text holds none. */
    private static Double firstNumber(String text, int from) {
        for (int index = Math.max(0, from); index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '-' && character != '+' && character != '.' && !Character.isDigit(character)) continue;
            int end = index + 1;
            while (end < text.length() && "0123456789+-.eE".indexOf(text.charAt(end)) >= 0) end++;
            try {
                return Double.parseDouble(text.substring(index, end));
            } catch (NumberFormatException ignored) {
                // A lone sign or a word such as "ResourceKey" -- keep looking.
            }
        }
        return null;
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
        rulesVersion++;
        save();
    }

    public static synchronized void replace(DisplayRule rule) {
        ensureRegistered();
        for (int index = 0; index < RULES.size(); index++) {
            if (RULES.get(index).id().equals(rule.id())) {
                RULES.set(index, Objects.requireNonNull(rule, "rule"));
                rulesVersion++;
                save();
                return;
            }
        }
        add(rule);
    }

    public static synchronized void remove(String id) {
        ensureRegistered();
        if (RULES.removeIf(rule -> rule.id().equals(id))) {
            rulesVersion++;
            save();
        }
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
        } finally {
            rulesVersion++;
        }
    }

    private static void ensureRegistered() {
        if (rulesFile == null) throw new IllegalStateException("Display rule storage has not been registered");
    }
}
