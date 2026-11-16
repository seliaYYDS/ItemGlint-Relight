package celia.adwadg.itemglintrelight.client.input;

/**
 * 1.21.1 counterpart of {@code net.minecraft.client.input.CharacterEvent}. 1.21.1 delivers
 * {@code charTyped(char, int)}; the two helpers below match the 1.21.11 accessors the screen uses.
 */
public record CharacterEvent(char codepoint, int modifiers) {
    /** 1.21.1 has no {@code SharedConstants#isAllowedChatCharacter}; this is the same rule. */
    public boolean isAllowedChatCharacter() {
        return codepoint != 167 && codepoint >= ' ' && codepoint != 127;
    }

    public String codepointAsString() {
        return String.valueOf(codepoint);
    }
}
