package com.boombapcompile.blckvox.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypingPropertiesTest {

    @Test
    void allGettersReturnConstructorValues() {
        TypingProperties props = new TypingProperties(
                800, 30, 100, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default", 200);

        assertThat(props.getChunkSize()).isEqualTo(800);
        assertThat(props.getInterChunkDelayMs()).isEqualTo(30);
        assertThat(props.getFocusDelayMs()).isEqualTo(100);
        assertThat(props.isRestoreClipboard()).isTrue();
        assertThat(props.isClipboardOnlyFallback()).isFalse();
        assertThat(props.getNormalizeNewlines()).isEqualTo(TypingProperties.NewlineMode.LF);
        assertThat(props.isTrimTrailingNewline()).isTrue();
        assertThat(props.isEnableRobot()).isTrue();
        assertThat(props.getPasteShortcut()).isEqualTo("os-default");
        assertThat(props.getClipboardRestoreDelayMs()).isEqualTo(200);
    }

    @Test
    void booleanGettersReflectFalseValues() {
        TypingProperties props = new TypingProperties(
                400, 10, 50, false, true,
                TypingProperties.NewlineMode.CRLF, false, false, "META+V", 100);

        assertThat(props.isRestoreClipboard()).isFalse();
        assertThat(props.isClipboardOnlyFallback()).isTrue();
        assertThat(props.isTrimTrailingNewline()).isFalse();
        assertThat(props.isEnableRobot()).isFalse();
        assertThat(props.getNormalizeNewlines()).isEqualTo(TypingProperties.NewlineMode.CRLF);
        assertThat(props.getPasteShortcut()).isEqualTo("META+V");
    }
}
