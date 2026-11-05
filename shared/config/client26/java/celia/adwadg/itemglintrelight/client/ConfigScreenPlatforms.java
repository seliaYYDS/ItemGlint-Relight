package celia.adwadg.itemglintrelight.client;

/** Resolves the screen platform implementation the running version provides. */
public final class ConfigScreenPlatforms {
    private static final String IMPLEMENTATION = "celia.adwadg.itemglintrelight.client.ConfigScreenPlatformImpl";
    private static ConfigScreenPlatform instance;

    private ConfigScreenPlatforms() { }

    public static void set(ConfigScreenPlatform platform) {
        instance = platform;
    }

    public static ConfigScreenPlatform get() {
        if (instance == null) {
            try {
                instance = (ConfigScreenPlatform) Class.forName(IMPLEMENTATION)
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new IllegalStateException("No config screen platform registered for this version", exception);
            }
        }
        return instance;
    }
}
