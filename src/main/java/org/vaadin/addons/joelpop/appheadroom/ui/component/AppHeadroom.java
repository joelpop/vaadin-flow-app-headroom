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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.function.SerializableBiPredicate;
import com.vaadin.flow.function.SerializableRunnable;
import com.vaadin.flow.function.SerializableSupplier;
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
    /** Wire name of the {@link #setTransitionDuration} attribute. */
    public static final String ATTR_TRANSITION_DURATION = "transition-duration";
    /** {@code slot} name the top condensed view's element is attached under. */
    public static final String SLOT_CONDENSED_TOP = "condensed-top";
    /** {@code slot} name the bottom condensed view's element is attached under. */
    public static final String SLOT_CONDENSED_BOTTOM = "condensed-bottom";
    /** Wire name of the attribute telling app-headroom.ts which shape (if any) the top condensed view is. */
    public static final String ATTR_CONDENSED_TOP_SHAPE = "condensed-top-shape";
    /** Wire name of the attribute telling app-headroom.ts which shape (if any) the bottom condensed view is. */
    public static final String ATTR_CONDENSED_BOTTOM_SHAPE = "condensed-bottom-shape";
    /** {@link #ATTR_CONDENSED_TOP_SHAPE}/{@link #ATTR_CONDENSED_BOTTOM_SHAPE} value for {@link CondensedBar#asFloating()}. */
    public static final String SHAPE_FLOATING = "floating";
    /** {@link #ATTR_CONDENSED_TOP_SHAPE}/{@link #ATTR_CONDENSED_BOTTOM_SHAPE} value for {@link CondensedBar#asRibbon()}. */
    public static final String SHAPE_RIBBON = "ribbon";

    /**
     * Default physical screen shorter-side threshold, in CSS pixels, used by
     * {@link #detectDeviceType} to distinguish {@link DeviceType#TABLET} from
     * {@link DeviceType#PHONE} among touch devices. Matches the threshold
     * used by {@code vaadin-flow-app-nav-layout}'s equivalent device
     * detection, so the two independently-implemented add-ons classify
     * devices consistently unless overridden via {@link #setTabletMinShortSidePx}.
     */
    private static final int DEFAULT_TABLET_MIN_SHORT_SIDE_PX = 768;

    // Known once detectDeviceType() runs (once, at first attach); never changes thereafter.
    private DeviceType deviceType;
    // Known once the Signal.effect wired in applyTo() first fires; updated on every resize/rotation.
    private Orientation orientation;
    // Re-evaluated against deviceType/orientation whenever either becomes known or changes.
    private SerializableBiPredicate<DeviceType, Orientation> activationPredicate = (device, currentOrientation) -> true;
    // See setTabletMinShortSidePx() - must be set before the target layout attaches to take
    // effect, since detectDeviceType() only ever runs once, at first attach.
    private int tabletMinShortSidePx = DEFAULT_TABLET_MIN_SHORT_SIDE_PX;
    // See getCondensedTop()/getCondensedBottom() - constructed once, eagerly, since every
    // AppHeadroom instance has exactly one of each, always.
    private final CondensedBar condensedTop =
            new CondensedBar(this, SLOT_CONDENSED_TOP, ATTR_CONDENSED_TOP_SHAPE, true);
    private final CondensedBar condensedBottom =
            new CondensedBar(this, SLOT_CONDENSED_BOTTOM, ATTR_CONDENSED_BOTTOM_SHAPE, false);

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

        SerializableRunnable onTargetAttach = () -> bindToTarget(h, layoutElement);
        layoutElement.addAttachListener(e -> onTargetAttach.run());
        if (layoutElement.getNode().isAttached()) {
            onTargetAttach.run();
        }
        layoutElement.addDetachListener(e -> h.getElement().removeFromParent());

        return h;
    }

    // Re-parents h under the current UI's root and re-points the client at layoutElement.
    // Runs once immediately if layoutElement is already attached at applyTo() call time,
    // and again on every subsequent re-attach (see the attach listener in applyTo()).
    private static void bindToTarget(AppHeadroom h, Element layoutElement) {
        h.getElement().removeFromParent();
        var ui = UI.getCurrentOrThrow();
        ui.getElement().appendChild(h.getElement());
        h.getElement().executeJs("this.target = $0;", layoutElement);

        if (h.deviceType == null) {
            wireDeviceAndOrientationDetection(h, ui);
        }
    }

    // Device type and orientation tracking are both wired once, at first attach — Signal.effect
    // requires a live UI to even validate itself (it reads a Signal synchronously on creation),
    // so it can't be set up any earlier than this, unlike bindToTarget()'s peer-relocation/
    // executeJs steps, which merely need to re-run per attach.
    private static void wireDeviceAndOrientationDetection(AppHeadroom h, UI ui) {
        h.deviceType = detectDeviceType(ui.getPage().getExtendedClientDetails(), h.tabletMinShortSidePx);
        h.reevaluateActive();

        // Tracks orientation reactively for as long as h itself is attached — Signal.effect
        // ties its own enabled/disabled state to h's attach state automatically, so this
        // needs no manual re-registration on later re-attaches.
        Signal.effect(h, () -> {
            var size = UI.getCurrentOrThrow().getPage().windowSizeSignal().get();
            h.orientation = size.width() >= size.height() ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
            h.reevaluateActive();
        });
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

    /**
     * Sets how long, in milliseconds, the show/hide slide and padding
     * transitions take (default {@code 600}). Returns {@code this} for
     * chaining.
     *
     * @throws IllegalArgumentException if {@code ms} is negative
     */
    public AppHeadroom setTransitionDuration(int ms) {
        requireNonNegative(ms, "transitionDuration");
        getElement().setAttribute(ATTR_TRANSITION_DURATION, String.valueOf(ms));
        return this;
    }

    private static void requireNonNegative(int px, String paramName) {
        if (px < 0) {
            throw new IllegalArgumentException(paramName + " must not be negative, was " + px);
        }
    }

    /**
     * The top bar's condensed-view accessor — a small alternate view that can
     * cross-fade into place while the real top bar is scroll-hidden. Shape it
     * with {@link CondensedBar#asFloating()} or {@link CondensedBar#asRibbon()}
     * before supplying content; neither is configured by default, so nothing
     * shows while hidden, same as before this feature existed.
     *
     * <p>Fades on scroll position alone, the same as the real bar. The one
     * thing that suppresses it: an explicit {@link #setTopBarPinned}{@code
     * (true)} — a deliberate "never hide this" declaration from application
     * code. The automatic pinned-rail geometry check that can similarly keep
     * the real bar from ever hiding does <em>not</em> also suppress this —
     * that check has no way to know whether a permanently-visible real bar
     * and an app-supplied condensed view are related at all, so it only ever
     * affects the real bar's own slide-away.
     */
    public CondensedBar getCondensedTop() {
        return condensedTop;
    }

    /** Same as {@link #getCondensedTop()}, for the bottom bar. */
    public CondensedBar getCondensedBottom() {
        return condensedBottom;
    }

    /**
     * One bar's condensed-view accessor (see {@link #getCondensedTop()}/
     * {@link #getCondensedBottom()}) — pick a shape via {@link #asFloating()}
     * or {@link #asRibbon()} to get a shape-specific accessor exposing only
     * the methods that make sense for it, the same pattern {@code Grid}'s
     * {@code asSingleSelect()}/{@code asMultiSelect()} already use for an
     * analogous "pick one of a few mutually-exclusive modes" choice.
     *
     * <p>Deliberately owns none of the app's own content, only which shape
     * is currently configured — the app is never asked to get positioning or
     * safe-area handling right itself; whichever shape it picks owns that
     * entirely, so the app only ever supplies content.
     */
    public static final class CondensedBar {
        private final AppHeadroom owner;
        private final String slotName;
        private final String shapeAttribute;
        private final boolean top;
        // Whatever's currently the direct light-DOM slot content - the app's own
        // Component for asFloating(), or the AppHeadroom-owned frame Div wrapping it
        // for asRibbon(). Null means nothing built yet (or torn down), same
        // permanent-value reasoning as this file's other nullable fields.
        private Component slotComponent;

        private CondensedBar(AppHeadroom owner, String slotName, String shapeAttribute, boolean top) {
            this.owner = owner;
            this.slotName = slotName;
            this.shapeAttribute = shapeAttribute;
            this.top = top;
        }

        /**
         * Shapes this bar's condensed view as a small, floating, auto-centered
         * Component. {@code AppHeadroom} owns all of its positioning and
         * safe-area clearance (reconciled against a sensible built-in default
         * gap, never simply added on top of whatever padding the Component
         * itself has) — supply only content; there's nothing else to configure.
         *
         * <p>Calling this again, or {@link #asRibbon()}, tears down whatever
         * this bar previously had first.
         */
        public FloatingCondensedBar asFloating() {
            tearDown();
            return new FloatingCondensedBar(this);
        }

        /**
         * Shapes this bar's condensed view as a full-width, opaque ribbon —
         * background flush to the true screen edge, extending into the safe
         * area exactly like the real bar's own landscape treatment, with
         * only the content inset from the unsafe zone. Defaults to {@code
         * var(--vaadin-background-container)} (a base-theme property present
         * under any theme, light/dark-aware automatically); override via the
         * returned accessor's {@link RibbonCondensedBar#getStyle()} if needed
         * — expected to be rare, since the default already fits any theme.
         *
         * <p>Calling this again, or {@link #asFloating()}, tears down
         * whatever this bar previously had first.
         */
        public RibbonCondensedBar asRibbon() {
            tearDown();
            return new RibbonCondensedBar(this);
        }

        private void tearDown() {
            if (slotComponent != null) {
                owner.getElement().removeChild(slotComponent.getElement());
                slotComponent = null;
            }
            owner.getElement().removeAttribute(shapeAttribute);
        }

        // Rebuilds this bar's direct slot content immediately - deliberately
        // unconditional on this instance's own attach state, same as this file's
        // other Element-subtree-assembly methods: an Element subtree can be
        // assembled before it's attached; whatever's already attached to this peer
        // element when it does attach comes along with it. Also sets/clears
        // shapeAttribute at this same moment - app-headroom.ts's render() only
        // renders a bar's wrapper <div> when the matching shape property says so,
        // rather than inferring presence by observing slot/DOM content itself.
        // Java already knows for certain; it just says so.
        private void setSlotComponent(String shape, Component newSlotComponent) {
            tearDown();
            if (newSlotComponent == null) {
                return;
            }
            newSlotComponent.getElement().setAttribute("slot", slotName);
            owner.getElement().appendChild(newSlotComponent.getElement());
            owner.getElement().setAttribute(shapeAttribute, shape);
            slotComponent = newSlotComponent;
        }

        private boolean isTop() {
            return top;
        }
    }

    /** {@link CondensedBar#asFloating()}'s shape-specific accessor. */
    public static final class FloatingCondensedBar {
        private final CondensedBar bar;

        private FloatingCondensedBar(CondensedBar bar) {
            this.bar = bar;
        }

        /**
         * Sets the Component to show, built and attached immediately. {@code
         * null} (the default) shows nothing while the real bar is hidden.
         * Calling this again (including with {@code null}) tears down
         * whatever was previously built first. Returns {@code this} for
         * chaining.
         */
        public FloatingCondensedBar setRenderer(SerializableSupplier<Component> renderer) {
            bar.setSlotComponent(SHAPE_FLOATING, renderer == null ? null : renderer.get());
            return this;
        }
    }

    /** {@link CondensedBar#asRibbon()}'s shape-specific accessor. */
    public static final class RibbonCondensedBar {
        private final CondensedBar bar;
        // The AppHeadroom-owned frame the app's rendered Component is nested inside -
        // see setRenderer(). Null until the first non-null setRenderer() call.
        private Div frame;

        private RibbonCondensedBar(CondensedBar bar) {
            this.bar = bar;
        }

        /**
         * Sets the Component to show, nested inside an {@code AppHeadroom}-owned
         * frame built and attached immediately. {@code null} (the default)
         * shows nothing while the real bar is hidden. Calling this again
         * (including with {@code null}) tears down whatever was previously
         * built first. Returns {@code this} for chaining.
         */
        public RibbonCondensedBar setRenderer(SerializableSupplier<Component> renderer) {
            if (renderer == null) {
                bar.setSlotComponent(SHAPE_RIBBON, null);
                frame = null;
                return this;
            }
            var newFrame = new Div(renderer.get());
            newFrame.setWidthFull();
            newFrame.getStyle().setBackground("var(--vaadin-background-container)");
            if (bar.isTop()) {
                newFrame.getStyle().setPaddingTop("env(safe-area-inset-top, 0px)");
            } else {
                newFrame.getStyle().setPaddingBottom("env(safe-area-inset-bottom, 0px)");
            }
            bar.setSlotComponent(SHAPE_RIBBON, newFrame);
            frame = newFrame;
            return this;
        }

        /**
         * The frame's own style — overrides the default {@code
         * var(--vaadin-background-container)} background, or anything else
         * about the frame. A last resort, not the primary way to configure
         * this: most apps never need it, since the default already fits any
         * theme.
         *
         * @throws IllegalStateException if {@link #setRenderer} hasn't been
         *         called with a non-{@code null} argument yet
         */
        public Style getStyle() {
            if (frame == null) {
                throw new IllegalStateException(
                        "setRenderer(...) must be called with a non-null renderer before getStyle()");
            }
            return frame.getStyle();
        }
    }

    /**
     * Explicitly overrides whether the top bar is treated as pinned (never
     * hidden), regardless of the automatic position/shape inference. Also
     * suppresses {@link #getCondensedTop()}'s condensed view, unlike the
     * automatic inference, which only ever affects the real bar.
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
     * Overrides the physical-screen-shorter-side threshold, in CSS pixels,
     * used to distinguish {@link DeviceType#TABLET} from {@link
     * DeviceType#PHONE} among touch devices (default {@code 768}, matching
     * {@code vaadin-flow-app-nav-layout}'s equivalent device detection).
     *
     * <p>Must be called before the target {@link AppLayout} attaches to take
     * effect — device type is detected once, at first attach, same as
     * {@link #setActivationPredicate}'s predicate is evaluated against
     * whatever's known at the time.
     *
     * @return this, for chaining
     * @throws IllegalArgumentException if {@code px} is negative
     */
    public AppHeadroom setTabletMinShortSidePx(int px) {
        requireNonNegative(px, "tabletMinShortSidePx");
        this.tabletMinShortSidePx = px;
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
    static DeviceType detectDeviceType(ExtendedClientDetails details, int tabletMinShortSidePx) {
        if (details == null || !details.isTouchDevice()) {
            return DeviceType.DESKTOP;
        }
        int minDim = Math.min(details.getScreenWidth(), details.getScreenHeight());
        return minDim >= tabletMinShortSidePx ? DeviceType.TABLET : DeviceType.PHONE;
    }

    /** Coarse device category, used by {@link #setActivationPredicate}. */
    public enum DeviceType { PHONE, TABLET, DESKTOP }

    /** Viewport orientation, used by {@link #setActivationPredicate}. */
    public enum Orientation { PORTRAIT, LANDSCAPE }
}
