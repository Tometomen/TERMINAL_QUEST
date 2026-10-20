package jp.terminalquest.gwt;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.gwt.DefaultGwtInput;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.google.gwt.dom.client.CanvasElement;

/** Bridges modern wheel/trackpad events to Scene2D, including current Chromium.
 * libGDX 1.13.1 listens only for the deprecated mousewheel/DOMMouseScroll events.
 */
public final class ModernGwtInput extends DefaultGwtInput {
    public ModernGwtInput(CanvasElement canvas, GwtApplicationConfiguration config) {
        super(canvas, config);
        hookModernWheel(canvas);
    }

    private void scroll(float x, float y) {
        InputProcessor processor = getInputProcessor();
        if (processor != null) processor.scrolled(x, y);
    }

    private native void hookModernWheel(CanvasElement canvas) /*-{
        var self = this;
        // The backend's legacy handler is private. Capture those events before
        // they reach its canvas listener, avoiding duplicate events on Firefox
        // and Chromium versions that still emit both forms.
        var blockLegacy = $entry(function(event) {
            if (event.target === canvas) {
                event.preventDefault();
                event.stopPropagation();
            }
        });
        $doc.addEventListener('mousewheel', blockLegacy, {capture: true, passive: false});
        $doc.addEventListener('DOMMouseScroll', blockLegacy, {capture: true, passive: false});
        // Capture at document level so current Chromium reliably delivers the
        // event instead of taking its compositor's native scrolling path.
        $doc.addEventListener('wheel', $entry(function(event) {
            if (event.target !== canvas) return;
            var divisor = event.deltaMode === 1 ? 3 : event.deltaMode === 2 ? 1 : 100;
            self.@jp.terminalquest.gwt.ModernGwtInput::scroll(FF)(event.deltaX / divisor, event.deltaY / divisor);
            event.preventDefault();
        }), {capture: true, passive: false});
    }-*/;
}
