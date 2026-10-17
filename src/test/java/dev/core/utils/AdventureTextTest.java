package dev.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

class AdventureTextTest {

    @Test
    void itemLoreLineDisablesImplicitItalic() {
        Component line = AdventureText.itemLoreLine("§7Plain lore");
        Component content = line.children().get(0);

        assertEquals(TextDecoration.State.FALSE, content.decoration(TextDecoration.ITALIC));
    }

    @Test
    void itemLoreLinePreservesExplicitItalic() {
        Component line = AdventureText.itemLoreLine("§oItalic lore");
        Component content = line.children().get(0);

        assertEquals(TextDecoration.State.TRUE, content.decoration(TextDecoration.ITALIC));
    }

    @Test
    void withoutItalicPreservesExplicitItalic() {
        Component line = AdventureText.withoutItalic(AdventureText.legacy("§oItalic"));

        assertEquals(TextDecoration.State.TRUE, line.decoration(TextDecoration.ITALIC));
    }

    @Test
    void itemLoreLineRoundTripsThroughLegacyText() {
        String text = "§7Requires Level §c5";

        assertEquals(text, AdventureText.legacyString(AdventureText.itemLoreLine(text)));
    }
}
