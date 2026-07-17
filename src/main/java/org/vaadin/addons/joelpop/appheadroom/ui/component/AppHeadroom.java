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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.function.SerializableBiPredicate;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.signals.Signal;

import java.util.Objects;

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

    /** Wire name of the {@link #setTopOffset} attribute. */
    public static final String ATTR_TOP_OFFSET = "top-offset";
    /** Wire name of the {@link #setHideTolerance} attribute. */
    public static final String ATTR_HIDE_TOLERANCE = "hide-tolerance";
    /** Wire name of the {@link #setShowTolerance} attribute. */
    public static final String ATTR_SHOW_TOLERANCE = "show-tolerance";
    /** Wire name of the {@link #setTopBarPinned} attribute. */
    public static final String ATTR_TOP_BAR_PINNED = "top-bar-pinned";
    /** Wire name of the {@link #setBottomBarPinned} attribute. */
    public static final String ATTR_BOTTOM_BAR_PINNED = "bottom-bar-pinned";
    /** Wire name of the {@link #isPinned} element property. */
    public static final String PROPERTY_PINNED = "pinned";
    /** Wire name of the {@link #isActive} element property. */
    public static final String PROPERTY_ACTIVE = "active";
    /** Wire name of the {@link #addPinnedChangeListener} DOM event. */
    public static final String EVENT_PINNED_CHANGED = "pinned-changed";

    /**
     * Physical screen shorter-side threshold, in CSS pixels, used by {@link
     * #detectDeviceType} to distinguish {@link DeviceType#TABLET} from {@link
     * DeviceType#PHONE} among touch devices. Matches the threshold used by
     * {@code vaadin-flow-app-nav-layout}'s equivalent device detection, so the
     * two independently-implemented add-ons classify devices consistently.
     */
    private static final int TABLET_MIN_SHORT_SIDE_PX = 768;

    // Known once detectDeviceType() runs (once, at first attach); never changes thereafter.
    private DeviceType deviceType;
    // Known once the Signal.effect wired in applyTo() first fires; updated on every resize/rotation.
    private Orientation orientation;
    // Re-evaluated against deviceType/orientation whenever either becomes known or changes.
    private SerializableBiPredicate<DeviceType, Orientation> activationPredicate = (device, currentOrientation) -> true;

    private AppHeadroom() {}

    /**
     * Applies headroom scroll-hide/show behavior to the given AppLayout and
     * returns the component for optional further configuration.
     *
     * <p>The returned instance attaches itself as a peer under the current
     * {@link UI}'s root element, never as a light-DOM child of {@code layout}
     * — so it never shows up in {@code layout.getChildren()}. This happens
     * automatically whenever {@code layout} itself is attached (immediately,
     * if it already is), and follows {@code layout} across any subsequent
     * detach/re-attach. Call {@link #remove()} to detach headroom behavior
     * from {@code layout} without affecting the layout itself.
     *
     * <p>The effect is active on every device by default; see {@link
     * #setActivationPredicate} to restrict it by device type/orientation.
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
        var layoutElement = layout.getElement();

        SerializableRunnable onTargetAttach = () -> {
            h.getElement().removeFromParent();
            var ui = UI.getCurrentOrThrow();
            ui.getElement().appendChild(h.getElement());
            h.getElement().executeJs("this.target = $0;", layoutElement);

            // Device type and orientation tracking are both wired once, at first attach —
            // Signal.effect requires a live UI to even validate itself (it reads a Signal
            // synchronously on creation), so it can't be set up any earlier than this, unlike
            // the peer-relocation/executeJs steps above, which merely need to re-run per attach.
            if (h.deviceType == null) {
                h.deviceType = detectDeviceType(ui.getPage().getExtendedClientDetails());
                h.reevaluateActive();

                // Tracks orientation reactively for as long as h itself is attached —
                // Signal.effect ties its own enabled/disabled state to h's attach state
                // automatically, so this needs no manual re-registration on later re-attaches.
                Signal.effect(h, () -> {
                    var size = UI.getCurrentOrThrow().getPage().windowSizeSignal().get();
                    h.orientation = size.width() >= size.height() ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
                    h.reevaluateActive();
                });
            }
        };
        layoutElement.addAttachListener(e -> onTargetAttach.run());
        if (layoutElement.getNode().isAttached()) {
            onTargetAttach.run();
        }
        layoutElement.addDetachListener(e -> h.getElement().removeFromParent());

        return h;
    }

    /**
     * Detaches this instance, removing its scroll-hide/show behavior from the
     * {@link AppLayout} it was applied to. Has no effect if already detached.
     */
    public void remove() {
        getElement().removeFromParent();
    }

    /**
     * Sets the distance from the top of the page, in pixels, within which the
     * chrome is always shown (default {@code 100}). Returns {@code this} for
     * chaining.
     *
     * @throws IllegalArgumentException if {@code px} is negative
     */
    public AppHeadroom setTopOffset(int px) {
        requireNonNegative(px, "topOffset");
        getElement().setAttribute(ATTR_TOP_OFFSET, String.valueOf(px));
        return this;
    }

    /**
     * Sets how far, in pixels, the user must scroll down past the last
     * shown-position high-water mark before the chrome hides (default
     * {@code 30}). Returns {@code this} for chaining.
     *
     * @throws IllegalArgumentException if {@code px} is negative
     */
    public AppHeadroom setHideTolerance(int px) {
        requireNonNegative(px, "hideTolerance");
        getElement().setAttribute(ATTR_HIDE_TOLERANCE, String.valueOf(px));
        return this;
    }

    /**
     * Sets how far, in pixels, the user must scroll up past the last
     * hidden-position low-water mark before the chrome is restored (default
     * {@code 30}). Returns {@code this} for chaining.
     *
     * @throws IllegalArgumentException if {@code px} is negative
     */
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

    /**
     * Whether the chrome is currently shown ({@code true}, the initial and
     * default state) or hidden ({@code false}). Kept in sync with the client's
     * own scroll-driven pin/unpin state; see {@link #addPinnedChangeListener}
     * to be notified of changes instead of polling this.
     */
    @Synchronize(EVENT_PINNED_CHANGED)
    public boolean isPinned() {
        return getElement().getProperty(PROPERTY_PINNED, true);
    }

    /**
     * Fired whenever the chrome's pinned/unpinned state changes, whether
     * driven by scrolling on the client or by a server-initiated reset (e.g.
     * detaching this instance via {@link #remove()}).
     */
    @DomEvent(EVENT_PINNED_CHANGED)
    public static class PinnedChangeEvent extends ComponentEvent<AppHeadroom> {
        private final boolean pinned;

        /**
         * Constructed by Flow when the client fires {@code pinned-changed}, or
         * directly by {@link AppHeadroom} itself for a server-initiated reset;
         * not meant to be constructed by application code.
         */
        public PinnedChangeEvent(AppHeadroom source, boolean fromClient,
                @EventData("event.detail.pinned") boolean pinned) {
            super(source, fromClient);
            this.pinned = pinned;
        }

        /** Same value as {@link AppHeadroom#isPinned()} at the time this event fired. */
        public boolean isPinned() {
            return pinned;
        }
    }

    /** Registers a listener to be notified whenever {@link #isPinned()} changes. */
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

    /**
     * Sets the predicate that decides whether the headroom effect should be
     * active, evaluated against this session's detected {@link DeviceType}
     * and current {@link Orientation}. Re-evaluated automatically whenever
     * either becomes known or changes (e.g. on device rotation) — there's
     * nothing further to wire up.
     *
     * <p>The default predicate always returns {@code true}: the effect is
     * active on every device unless this is called. There's no separate
     * plain on/off switch — force it fully on or off yourself with {@code
     * (deviceType, orientation) -> true} / {@code -> false}, or restrict it
     * by device, e.g. to restore the original phone-always/tablet-landscape-only
     * behavior this had before being extracted into this library:
     * <pre>{@code
     * AppHeadroom.applyTo(layout).setActivationPredicate((deviceType, orientation) ->
     *     deviceType == AppHeadroom.DeviceType.PHONE
     *         || (deviceType == AppHeadroom.DeviceType.TABLET && orientation == AppHeadroom.Orientation.LANDSCAPE));
     * }</pre>
     *
     * @return this, for chaining
     */
    public AppHeadroom setActivationPredicate(SerializableBiPredicate<DeviceType, Orientation> predicate) {
        this.activationPredicate = Objects.requireNonNull(predicate);
        reevaluateActive();
        return this;
    }

    /**
     * Whether the headroom effect is currently active for this session, per
     * the last evaluation of the {@link #setActivationPredicate activation
     * predicate}. Defaults to {@code true} until device type and orientation
     * are both known (shortly after the target {@link AppLayout} attaches).
     */
    public boolean isActive() {
        return getElement().getProperty(PROPERTY_ACTIVE, true);
    }

    private void reevaluateActive() {
        if (deviceType == null || orientation == null) {
            return;
        }
        getElement().setProperty(PROPERTY_ACTIVE, activationPredicate.test(deviceType, orientation));
    }

    // Classifies off physical screen size and touch capability, not viewport/window width
    // (which fluctuates as a desktop user resizes their browser) and not User-Agent string
    // sniffing (fragile against browsers progressively reducing/freezing UA strings).
    // Matches vaadin-flow-app-nav-layout's equivalent device detection.
    static DeviceType detectDeviceType(ExtendedClientDetails details) {
        if (details == null || !details.isTouchDevice()) {
            return DeviceType.DESKTOP;
        }
        int minDim = Math.min(details.getScreenWidth(), details.getScreenHeight());
        return minDim >= TABLET_MIN_SHORT_SIDE_PX ? DeviceType.TABLET : DeviceType.PHONE;
    }

    /** Coarse device category, used by {@link #setActivationPredicate}. */
    public enum DeviceType { PHONE, TABLET, DESKTOP }

    /** Viewport orientation, used by {@link #setActivationPredicate}. */
    public enum Orientation { PORTRAIT, LANDSCAPE }
}
