package dev.bukkit.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudCooldownFeedbackTest {

    private static final char S = '\u00A7';

    private static HudConfig cfg(String onCooldown) {
        return new HudConfig(true, 4, 1.85, -0.35, 0.28, 0.65f, 0.9f, 300,
                null, 15, 1, 2, true, false,
                HudConfig.defaultTracking(), null, null,
                new HudConfig.Messages(null, onCooldown, 1600, 600));
    }

    @Test
    void defaultTemplateReplacesPlaceholders() {
        String out = HudCooldownFeedback.format(HudConfig.defaults(), "Fireball", 2500);
        assertEquals(S + "7Fireball " + S + "8» " + S + "cOn cooldown " + S + "7(2.5s)", out);
    }

    @Test
    void secondsRoundedToTenth() {
        String out = HudCooldownFeedback.format(HudConfig.defaults(), "X", 999);
        assertTrue(out.contains("1.0s"), "999ms renders as 1.0s, got: " + out);
    }

    @Test
    void cdsAliasPlaceholderStillWorks() {
        String out = HudCooldownFeedback.format(cfg("&e<ability>/%cds"), "Dash", 1500);
        assertEquals(S + "eDash/1.5", out);
    }

    @Test
    void hexAndRgbColorSyntaxSupported() {
        String out = HudCooldownFeedback.format(
                cfg("{#FF8000}<ability> &8» rgb(255,85,85)<seconds>"), "Blink", 400);
        String expected = dev.core.utils.ColorCodes.toLegacy("FF8000") + "Blink " + S + "8» "
                + dev.core.utils.ColorCodes.toLegacy("FF5555") + "0.4";
        assertEquals(expected, out);
    }

    @Test
    void ampHexColorSyntaxSupported() {
        String out = HudCooldownFeedback.format(cfg("&#FF0000<ability>"), "Nova", 1000);
        assertEquals(dev.core.utils.ColorCodes.toLegacy("FF0000") + "Nova", out);
    }

    @Test
    void displayNamePrefersAbilityName() {
        StubAbility ability = new StubAbility("my_cool_ability");
        ability.setName("My Cool Ability");
        assertEquals("My Cool Ability", HudCooldownFeedback.displayName(ability));
    }

    @Test
    void displayNamePrettifiesIdWhenNameMissing() {
        assertEquals("My Cool Ability", HudCooldownFeedback.displayName(new StubAbility("my_cool_ability")));
        assertEquals("Dash", HudCooldownFeedback.displayName(new StubAbility("dash")));
    }

    /** Minimal concrete Ability for formatting tests (no Bukkit runtime). */
    private static final class StubAbility extends dev.core.ability.Ability {
        StubAbility(String id) {
            super(id);
        }
    }
}
