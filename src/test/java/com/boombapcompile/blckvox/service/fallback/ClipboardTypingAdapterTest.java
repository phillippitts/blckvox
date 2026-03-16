package com.boombapcompile.blckvox.service.fallback;

import com.boombapcompile.blckvox.config.properties.TypingProperties;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClipboardTypingAdapterTest {

    static class FakeClipboard extends Clipboard {
        String contents;
        boolean throwOnSet;

        FakeClipboard() {
            super("fake");
        }
        @Override public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            if (throwOnSet) {
                throw new IllegalStateException("clipboard broken");
            }
            try {
                this.contents = (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException | IOException e) {
                this.contents = null;
            }
        }
        @Override public synchronized Transferable getContents(Object requestor) {
            String s = contents;
            return new java.awt.datatransfer.StringSelection(s == null ? "" : s);
        }
    }

    static class FakeFacade implements ClipboardTypingAdapter.ClipboardFacade {
        final FakeClipboard clipboard = new FakeClipboard();

        @Override
        public Clipboard getSystemClipboard() {
            return clipboard;
        }
    }

    @Test
    void savesAndRestoresClipboardAndNormalizesLf() throws InterruptedException {
        TypingProperties props = new TypingProperties(
                800, 0, 0, true, true,
                TypingProperties.NewlineMode.LF, true, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        // Seed prior content
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("orig"), null);

        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("hello\r\nworld\r\n");
        assertThat(ok).isTrue();
        // Clipboard-only fallback leaves normalized AND TRIMMED text (trimTrailingNewline=true)
        assertThat(facade.clipboard.contents).isEqualTo("hello\nworld");

        // With clipboardOnlyFallback=false, content should be restored to prior after paste
        TypingProperties props2 = new TypingProperties(
                800, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, false, "os-default", 200
        );
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("orig"), null);
        ClipboardTypingAdapter adapter2 = new ClipboardTypingAdapter(props2, facade);
        boolean ok2 = adapter2.type("abc\r\n");
        assertThat(ok2).isTrue();
        // Clipboard restore is async (200ms delay) to allow paste to complete
        Thread.sleep(400);
        assertThat(facade.clipboard.contents).isEqualTo("orig");
    }

    @Test
    void nullTextDefaultsToEmptyString() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type(null);
        assertThat(ok).isTrue();
        assertThat(facade.clipboard.contents).isEmpty();
    }

    @Test
    void crlfNormalizationMode() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.CRLF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("line1\nline2\rline3");
        assertThat(ok).isTrue();
        assertThat(facade.clipboard.contents).isEqualTo("line1\r\nline2\r\nline3");
    }

    @Test
    void noneNormalizationMode() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.NONE, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("line1\r\nline2\n");
        assertThat(ok).isTrue();
        // NONE mode: no normalization, text kept as-is
        assertThat(facade.clipboard.contents).isEqualTo("line1\r\nline2\n");
    }

    @Test
    void trimTrailingNewlineDisabled() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("hello\n");
        assertThat(ok).isTrue();
        // trimTrailingNewline=false → trailing newline preserved
        assertThat(facade.clipboard.contents).isEqualTo("hello\n");
    }

    @Test
    void clipboardOnlyFallbackSkipsPasteAndRestore() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, true, true,
                TypingProperties.NewlineMode.LF, true, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("prior"), null);
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("new text");
        assertThat(ok).isTrue();
        // clipboardOnlyFallback=true → no restore, text stays on clipboard
        assertThat(facade.clipboard.contents).isEqualTo("new text");
    }

    @Test
    void restoreClipboardDisabledDoesNotSavePrior() throws InterruptedException {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, false,
                TypingProperties.NewlineMode.LF, true, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("prior"), null);
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("new text");
        assertThat(ok).isTrue();
        // restoreClipboard=false → clipboard NOT restored
        Thread.sleep(300);
        assertThat(facade.clipboard.contents).isNotEqualTo("prior");
    }

    @Test
    void exceptionDuringSetContentsReturnsFalse() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        facade.clipboard.throwOnSet = true;
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("text");
        assertThat(ok).isFalse();
    }

    @Test
    void canTypeAlwaysReturnsTrue() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        assertThat(adapter.canType()).isTrue();
    }

    @Test
    void nameReturnsClipboard() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        assertThat(adapter.name()).isEqualTo("clipboard");
    }

    @Test
    void unsupportedFlavorExceptionDuringSavePriorIsIgnored() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        // Clipboard where getData throws UnsupportedFlavorException
        Clipboard failClip = new Clipboard("failing") {
            @Override
            public synchronized Transferable getContents(Object requestor) {
                return new java.awt.datatransfer.Transferable() {
                    @Override
                    public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                        return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.stringFlavor};
                    }
                    @Override
                    public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                        return true;
                    }
                    @Override
                    public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                            throws UnsupportedFlavorException {
                        throw new UnsupportedFlavorException(flavor);
                    }
                };
            }
            @Override
            public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
                // accept new contents
            }
        };
        ClipboardTypingAdapter.ClipboardFacade facade = () -> failClip;
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        // Should succeed even though saving prior clipboard threw
        boolean ok = adapter.type("test text");
        assertThat(ok).isTrue();
    }

    @Test
    void restoreClipboardExceptionInVirtualThreadIsIgnored() throws InterruptedException {
        // restoreClipboard=true, clipboardOnlyFallback=false → restore runs in virtual thread
        TypingProperties props = new TypingProperties(
                800, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, false, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("prior"), null);
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);

        boolean ok = adapter.type("new text");
        assertThat(ok).isTrue();

        // Set throwOnSet=true before the virtual thread attempts clipboard restore (200ms delay)
        facade.clipboard.throwOnSet = true;

        // Wait for the virtual thread to attempt restoration (and silently catch the Exception)
        Thread.sleep(400);
        // No exception should propagate — the catch(Exception) in the lambda handles it
    }

    @Test
    void trimTrailingNewlineRemovesMultipleTrailingNewlinesAndCr() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, false, true,
                TypingProperties.NewlineMode.LF, true, false, "os-default", 200
        );
        FakeFacade facade = new FakeFacade();
        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("hello\n\r\n\n");
        assertThat(ok).isTrue();
        assertThat(facade.clipboard.contents).isEqualTo("hello");
    }
}
