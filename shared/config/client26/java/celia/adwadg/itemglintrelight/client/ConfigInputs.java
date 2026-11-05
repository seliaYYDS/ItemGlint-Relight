package celia.adwadg.itemglintrelight.client;

/** Resolves the input platform implementation the running version provides. */
public final class ConfigInputs {
    private static final String IMPLEMENTATION = "celia.adwadg.itemglintrelight.client.ConfigInputImpl";
    private static ConfigInput instance;

    private ConfigInputs() { }

    public static void set(ConfigInput input) {
        instance = input;
    }

    public static ConfigInput get() {
        if (instance == null) {
            try {
                instance = (ConfigInput) Class.forName(IMPLEMENTATION).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError exception) {
                throw new IllegalStateException("No config input platform registered for this version", exception);
            }
        }
        return instance;
    }
}
