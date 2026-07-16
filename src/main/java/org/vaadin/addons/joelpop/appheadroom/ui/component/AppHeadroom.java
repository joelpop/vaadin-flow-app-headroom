package org.vaadin.addons.joelpop.appheadroom.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Synchronize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;

/**
 * Hides/shows an {@link AppLayout}'s top and bottom navigation bars on scroll.
 *
 * <p>This component only ever knows about {@link AppLayout}'s own standard,
 * public contract — specifically the {@code navbar-top} and {@code navbar-bottom}
 * shadow-DOM parts every {@code vaadin-app-layout} instance exposes, regardless
 * of subclass. It has no knowledge of, and no dependency on, any specific
 * {@code AppLayout} extension (e.g. one that adds a persistent side rail):
 * whatever a subclass does with those two standard parts, {@code AppHeadroom}
 * finds and manages them the same way every time.
 *
 * <p>By default, a bar that's already pinned to the viewport
 * ({@code position: fixed}) and shaped like a vertical rail rather than a
 * horizontal bar (taller than wide) is left alone automatically — a plain,
 * observable geometry fact, not something any extension has to declare. For
 * cases where that inference isn't right (or an extension's own behavior can't
 * be reliably inferred that way), {@link #setTopBarPinned} / {@link
 * #setBottomBarPinned} let calling code state it explicitly instead. See those
 * methods for how this is meant to be wired up from application code.
 */
@Tag("app-headroom")
@JsModule("./app-headroom.ts")
public class AppHeadroom extends Component {

    // Attribute/property/event names shared with app-headroom.ts (the browser-side
    // half of this component). There's no compiler to enforce agreement across that
    // Java/TypeScript boundary, so a rename on either side would still desync
    // silently — but these constants at least remove the *intra-Java* duplication
    // (`"pinned"`/`"pinned-changed"` previously appeared twice each in this file),
    // and let AppHeadroomIT's cross-boundary test compare against a single,
    // authoritative value rather than a value hardcoded a third time in the test.
    public static final String ATTR_TOP_OFFSET = "top-offset";
    public static final String ATTR_HIDE_TOLERANCE = "hide-tolerance";
    public static final String ATTR_SHOW_TOLERANCE = "show-tolerance";
    public static final String ATTR_TOP_BAR_PINNED = "top-bar-pinned";
    public static final String ATTR_BOTTOM_BAR_PINNED = "bottom-bar-pinned";
    public static final String PROPERTY_PINNED = "pinned";
    public static final String EVENT_PINNED_CHANGED = "pinned-changed";

    private AppHeadroom() {}

    public static AppHeadroom create() {
        return new AppHeadroom();
    }

    /**
     * Applies headroom scroll-hide/show behavior to the given AppLayout and
     * returns the component for optional further configuration.
     */
    public static AppHeadroom applyTo(AppLayout layout) {
        var tag = layout.getElement().getTag();
        if (!"vaadin-app-layout".equals(tag)) {
            throw new IllegalArgumentException(
                    "AppHeadroom.applyTo(...) expects the AppLayout's element tag "
                    + "to be 'vaadin-app-layout', but was '" + tag + "'. This usually "
                    + "means an AppLayout subclass overrode @Tag; headroom behavior "
                    + "targets the vaadin-app-layout web component specifically.");
        }
        var h = new AppHeadroom();
        layout.getElement().appendChild(h.getElement());
        return h;
    }

    public AppHeadroom setTopOffset(int px) {
        requireNonNegative(px, "topOffset");
        getElement().setAttribute(ATTR_TOP_OFFSET, String.valueOf(px));
        return this;
    }

    public AppHeadroom setHideTolerance(int px) {
        requireNonNegative(px, "hideTolerance");
        getElement().setAttribute(ATTR_HIDE_TOLERANCE, String.valueOf(px));
        return this;
    }

    public AppHeadroom setShowTolerance(int px) {
        requireNonNegative(px, "showTolerance");
        getElement().setAttribute(ATTR_SHOW_TOLERANCE, String.valueOf(px));
        return this;
    }

    private static void requireNonNegative(int px, String paramName) {
        if (px < 0) {
            throw new IllegalArgumentException(paramName + " must not be negative, was " + px);
        }
    }

    /**
     * Explicitly overrides whether the top bar is treated as pinned (never
     * hidden), regardless of the automatic position/shape inference.
     *
     * <p>Not called directly by any {@link AppLayout} extension — extensions
     * have no reason to know {@code AppHeadroom} exists. Instead, this is meant
     * to be wired up from application code that already explicitly combines an
     * {@code AppLayout} extension with {@code AppHeadroom} (for example, code
     * overriding an extension's own hook for observing layout-mode changes, if
     * it has one), reacting to whatever that extension's own public API exposes
     * about its current state and calling this method accordingly.
     */
    public AppHeadroom setTopBarPinned(boolean pinned) {
        getElement().setAttribute(ATTR_TOP_BAR_PINNED, pinned);
        return this;
    }

    /** Same as {@link #setTopBarPinned}, for the bottom bar. */
    public AppHeadroom setBottomBarPinned(boolean pinned) {
        getElement().setAttribute(ATTR_BOTTOM_BAR_PINNED, pinned);
        return this;
    }

    @Synchronize(EVENT_PINNED_CHANGED)
    public boolean isPinned() {
        return getElement().getProperty(PROPERTY_PINNED, true);
    }

    @DomEvent(EVENT_PINNED_CHANGED)
    public static class PinnedChangeEvent extends ComponentEvent<AppHeadroom> {
        private final boolean pinned;

        public PinnedChangeEvent(AppHeadroom source, boolean fromClient,
                @EventData("event.detail.pinned") boolean pinned) {
            super(source, fromClient);
            this.pinned = pinned;
        }

        public boolean isPinned() {
            return pinned;
        }
    }

    public Registration addPinnedChangeListener(ComponentEventListener<PinnedChangeEvent> listener) {
        return addListener(PinnedChangeEvent.class, listener);
    }

    /**
     * Resets the server-visible pinned state directly, rather than relying on the
     * client's own {@code pinned-changed} event during teardown: when detachment is
     * server-initiated (e.g. {@code layout.remove(headroom)}), Flow stops routing
     * further client events for this component the moment removal begins, so the
     * client-fired event from {@code disconnectedCallback()} never reaches here.
     */
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        getElement().setProperty(PROPERTY_PINNED, true);
        ComponentUtil.fireEvent(this, new PinnedChangeEvent(this, false, true));
    }
}
