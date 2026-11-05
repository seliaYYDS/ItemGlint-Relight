package celia.adwadg.itemglintrelight.client.ui;

/** Resolves the render platform implementation the running version provides. */
public final class ConfigRenderPlatforms {
    private static final String IMPLEMENTATION =
            "celia.adwadg.itemglintrelight.client.ui.ConfigRenderPlatformImpl";
    private static ConfigRenderPlatform instance;

    private ConfigRenderPlatforms() { }

    public static void set(ConfigRenderPlatform platform) {
        instance = platform;
    }

    public static ConfigRenderPlatform get() {
        if (instance == null) {
            try {
                instance = (ConfigRenderPlatform) Class.forName(IMPLEMENTATION)
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new IllegalStateException("No config render platform registered for this version", exception);
            }
        }
        return instance;
    }
}
