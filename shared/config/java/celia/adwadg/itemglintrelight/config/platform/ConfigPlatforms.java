package celia.adwadg.itemglintrelight.config.platform;

/**
 * Holds the platform implementation for the running version.
 *
 * <p>Versions whose resource-location type is {@code Identifier} need no registration: the shared
 * {@link IdentifierConfigPlatform} is discovered lazily by name. 1.21.1 registers its own
 * {@code ResourceLocationConfigPlatform} from the mod initializer before the configuration is read.
 */
public final class ConfigPlatforms {
    private static final String DEFAULT_IMPLEMENTATION =
            "celia.adwadg.itemglintrelight.config.platform.IdentifierConfigPlatform";
    private static volatile ConfigPlatform instance;

    private ConfigPlatforms() { }

    public static void set(ConfigPlatform platform) {
        instance = platform;
    }

    public static ConfigPlatform get() {
        ConfigPlatform current = instance;
        if (current == null) {
            current = loadDefault();
            instance = current;
        }
        if (current == null) {
            throw new IllegalStateException(
                    "No configuration platform is registered for this version; call ConfigPlatforms.set(...) "
                            + "from the mod initializer");
        }
        return current;
    }

    private static ConfigPlatform loadDefault() {
        try {
            Class<?> type = Class.forName(DEFAULT_IMPLEMENTATION);
            return (ConfigPlatform) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
