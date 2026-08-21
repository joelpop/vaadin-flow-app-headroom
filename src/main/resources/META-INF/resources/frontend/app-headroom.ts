/*
 * Browser-side half of AppHeadroom.java (loaded via @JsModule).
 *
 * Hides the top/bottom navigation bars when the user scrolls down and reveals
 * them when scrolling up ("Headroom.js" pattern). Also injects global CSS for
 * the mobile layout: body-scrolling on touch devices, safe-area padding, and
 * fixed landscape bottom bar.
 *
 * The CSS must live here (not in a Java @StyleSheet) because some rules target
 * vaadin-app-layout's internal shadow DOM via ::part() — a CSS selector that
 * can cross shadow-DOM boundaries from outside the component.
 *
 * Every rule below is scoped to the [headroom-enabled] attribute (only set once
 * an AppHeadroom instance actually attaches to a given vaadin-app-layout) —
 * importing this module has zero visual effect on any AppLayout that doesn't
 * use AppHeadroom. The one rule that targets <html> rather than
 * vaadin-app-layout uses :has(vaadin-app-layout[headroom-enabled]) instead,
 * since the attribute itself lives on a descendant, not on <html>.
 *
 * This file intentionally has zero knowledge of any AppLayout-extending add-on
 * (e.g. one that adds a persistent side rail). It only ever knows about
 * AppLayout's own standard, public contract: the navbar-top/navbar-bottom
 * shadow-DOM parts every vaadin-app-layout instance exposes, regardless of
 * subclass. Two independent, additive mechanisms let a bar avoid being hidden:
 *
 *  1. topBarPinned / bottomBarPinned (see AppHeadroom.java's setTopBarPinned/
 *     setBottomBarPinned) — an explicit override, set via a plain Java method
 *     call on the AppHeadroom instance itself. No AppLayout extension ever
 *     calls this directly or needs to know it exists; it's meant to be wired
 *     up from application code that already explicitly combines a specific
 *     AppLayout extension with AppHeadroom.
 *  2. looksLikeAPinnedRail() — an automatic fallback for the common case where
 *     no explicit override is set: a bar that's already pinned to the viewport
 *     (position: fixed) *and* shaped like a vertical rail rather than a
 *     horizontal bar (taller than wide) is left alone too. This is a plain,
 *     observable geometry fact, not a name any extension has to agree on — but
 *     being inferred rather than declared, it can occasionally be wrong (e.g. a
 *     bar in an unusually narrow embedded viewport might look rail-shaped by
 *     coincidence), which is exactly what (1) exists to override.
 *
 * Only (1) also suppresses a condensed view (see AppHeadroom.java's
 * getCondensedTop()/getCondensedBottom()) — (2) only ever affects
 * the real bar's own slide-away. A condensed Component is a peer element
 * positioned relative to the viewport, not a descendant of the target layout,
 * with no structural relationship to whatever made the real bar rail-shaped;
 * an automatic geometry guess about the real bar has no way to know whether
 * the two are even related, so it stays out of the condensed view's decision
 * entirely. An explicit pin, by contrast, is a deliberate declaration from
 * application code, so it suppresses both.
 */

import { LitElement, css, html, nothing } from 'lit';
import type { PropertyValues } from 'lit';

// Decorators (TypeScript annotations applied at class/field definition time):
// @customElement — registers this class as the <app-headroom> HTML tag.
// @property     — declares a reactive field; Vaadin sets it from Java via
//                 getElement().setAttribute(). `attribute` maps the kebab-case
//                 HTML attribute name to the camelCase TypeScript field name.
import { customElement, property } from 'lit/decorators.js';

// ── Global stylesheet ─────────────────────────────────────────────────────────
//
// CSSStyleSheet is a browser API for creating a stylesheet object in
// JavaScript. document.adoptedStyleSheets attaches it to the document without
// adding a <style> tag — applied once per page load regardless of how many
// AppHeadroom instances are created.
// Adopted at module-evaluation time (not lazily in connectedCallback) so the CSS
// custom properties are live before vaadin-app-layout's own connectedCallback fires.
//
// Guarded by a marker on `document` itself (not a module-level flag) against
// this module's top-level code running more than once — Vite HMR re-executing
// it, or two independent bundles on the same page both including it — which
// would otherwise append a duplicate copy of this stylesheet each time. A
// module-level flag wouldn't help: each re-evaluation gets its own fresh module
// scope, same as GLOBAL_STYLES itself would. `document` is the one thing that
// actually persists across re-evaluations.
const GLOBAL_STYLES_INSTALLED_MARKER = '__appHeadroomGlobalStylesInstalled';
if (!(document as unknown as Record<string, boolean>)[GLOBAL_STYLES_INSTALLED_MARKER]) {
    const GLOBAL_STYLES = new CSSStyleSheet();
    GLOBAL_STYLES.replaceSync(`
    /* Body-scrolling mode: touch devices only, and only on a page that actually
       has headroom attached to and active on its AppLayout — :has() lets us
       gate a rule on <html> by an attribute that only ever lives on a
       descendant element. Gated on [headroom-active] (not just
       [headroom-enabled]) since this is only needed while scroll-tracking is
       actually running - see setActivationPredicate in AppHeadroom.java.
       Content padding lives inside the scroll container so scrolled content
       naturally fills the space vacated by the chrome. Desktop keeps the
       default Vaadin content-scrolling mode. "pointer: coarse" identifies
       touch (finger) input devices. */
    @media (pointer: coarse) {
        html:has(vaadin-app-layout[headroom-enabled][headroom-active]) {
            height: auto;
        }
    }

    /* Headroom-style chrome hide/show.
       ::part(navbar-top/bottom) reaches INTO vaadin-app-layout's shadow DOM
       to animate its internal bar slots. will-change promotes the layers to
       the GPU so the slide is smooth even at 60fps on mobile.
       --headroom-transition-duration is set directly on this vaadin-app-layout
       by _attachToTarget() (see AppHeadroom.java's setTransitionDuration) - the
       600ms fallback here is defensive/documentation only, since that JS always
       sets it the same moment it sets [headroom-enabled] below.
       opacity and visibility are transitioned alongside transform, all three
       at the same duration, so they start and finish together in *both*
       directions - opacity is what actually produces the visible fade;
       visibility (a "discrete" property - nothing to interpolate between
       visible/hidden) is what makes a bar genuinely disappear once that fade
       finishes, rather than just going transparent while still occupying
       hit-testing/accessibility-tree space. The CSS Transitions spec defines
       discrete properties to flip only at the very end of a transition toward
       the non-default value, and at the very start toward the default value -
       visibility is the canonical property that rule was written for, so this
       needs no JS timing of its own, just listing it alongside the others.
       Only declared once, here, on the shared base rule - the two
       [headroom-hide-top/bottom] rules below only need to set the target
       values (transform/opacity/visibility), not redeclare this same
       transition list, since they match the same element simultaneously and
       don't override it.
       visibility being inherited (unlike transform/opacity, which only affect
       painted layers) is also what makes this reach a bar's own content that
       doesn't respond to transform at all - e.g. an AppLayout extension's own
       popover/overlay-based UI escaping the normal paint hierarchy. */
    vaadin-app-layout[headroom-enabled]::part(navbar-top),
    vaadin-app-layout[headroom-enabled]::part(navbar-bottom) {
        transition: transform var(--headroom-transition-duration, 600ms) ease,
                    opacity var(--headroom-transition-duration, 600ms) ease,
                    visibility var(--headroom-transition-duration, 600ms) ease;
        will-change: transform, opacity;
        opacity: 1;
    }

    /* translateY(-100%) slides the top bar upward by its own height (off-screen).
       Gated on headroom-hide-top rather than headroom-unpinned directly: our own
       JS only sets this marker when navbar-top isn't a pinned rail (see
       looksLikeAPinnedRail() in connectedCallback). */
    vaadin-app-layout[headroom-hide-top]::part(navbar-top) {
        transform: translateY(-100%);
        opacity: 0;
        visibility: hidden;
    }

    /* translateY(100%) slides the bottom bar downward by its own height. Same
       pinned-rail deferral as navbar-top above. */
    vaadin-app-layout[headroom-hide-bottom]::part(navbar-bottom) {
        transform: translateY(100%);
        opacity: 0;
        visibility: hidden;
    }

    /* Animate the layout's padding so content expands smoothly into the vacated space. */
    vaadin-app-layout[headroom-enabled] {
        transition: padding-top var(--headroom-transition-duration, 600ms) ease,
                    padding-bottom var(--headroom-transition-duration, 600ms) ease;
    }

    /* Landscape on touch: pin bottom bar to viewport bottom. Only needed while
       body-scrolling mode (above) is on, so gated on [headroom-active] too -
       otherwise this bar would just scroll away with the page once body-
       scrolling kicks in, with nothing pinning it back to the viewport.
       will-change: auto clears the stacking context that confines position: fixed.
       Note: this makes navbar-bottom position:fixed for our own layout reasons,
       unrelated to any pinned-rail concept — it stays full-width/short (a bar,
       not a rail), so looksLikeAPinnedRail() below still correctly hides it.
       --headroom-landscape-bottom-bar-z-index is a plain CSS override point -
       nothing in this library ever sets it; an app that needs a different
       stacking value (e.g. to sit above/below its own fixed-position chrome)
       sets it directly on its vaadin-app-layout.
       inset-inline-start/end stay at 0 (the bar's own background/hit-area
       still spans the full width) - safe-area clearance is added as
       padding-inline below instead, so content is pushed away from the
       unsafe corner area on a notched/rounded-corner phone in landscape
       without leaving a gap that reveals page content on either side of the
       bar. Confirmed against a real iPhone simulator screenshot; not
       reproducible via Playwright's flat-rectangle viewport emulation, which
       always reports env(safe-area-inset-*) as 0.
       padding-inline uses max(), not calc()/addition: 9px is this part's own
       already-present default touch-bar padding (Vaadin's own theme, not
       set by this rule) - max() preserves that exact look on ordinary
       (non-notched) devices where the env() value is 0, while still
       guaranteeing at least the real safe-area amount where it's larger.
       width is intentionally omitted again here too (not width: 100%), for
       a second, distinct reason from the position:fixed/inset case above:
       this part's box-sizing is content-box (Vaadin's own default), so an
       explicit width: 100% plus this rule's own padding-inline would add
       the padding on top of the full width instead of fitting inside it -
       measured overflowing the viewport by exactly the padding amount on
       each side. Leaving width auto lets the browser account for padding
       when filling the space between the (0/0) insets, regardless of
       box-sizing, since box-sizing only changes how an *explicit* width is
       interpreted. */
    @media (orientation: landscape) and (pointer: coarse) {
        vaadin-app-layout[headroom-enabled][headroom-active]::part(navbar-bottom) {
            position: fixed !important;
            inset-block-end: 0;
            inset-inline-start: 0;
            inset-inline-end: 0;
            height: auto;
            padding-inline-start: max(9px, env(safe-area-inset-left, 0px));
            padding-inline-end: max(9px, env(safe-area-inset-right, 0px));
            z-index: var(--headroom-landscape-bottom-bar-z-index, 200);
            will-change: auto;
        }
    }
`);
    document.adoptedStyleSheets = [...document.adoptedStyleSheets, GLOBAL_STYLES];
    (document as unknown as Record<string, boolean>)[GLOBAL_STYLES_INSTALLED_MARKER] = true;
}

// A bar that's already pinned to the viewport (position: fixed) AND shaped like a
// vertical rail (taller than wide) is being used as a persistent side rail by
// *some* other layout mechanism — headroom's translateY slide gesture only makes
// sense for a horizontal top/bottom bar, so it's skipped for anything shaped and
// positioned like this. This needs no cooperation from whatever made it a rail:
// it's a plain, observable geometric fact, not a name either side has to agree on.
function looksLikeAPinnedRail(el: HTMLElement | null): boolean {
    if (!el) return false;
    if (getComputedStyle(el).position !== 'fixed') return false;
    const rect = el.getBoundingClientRect();
    return rect.height > rect.width;
}

// Restores an AppLayout to its default (chrome fully shown, no bottom-bar
// padding override) state — shared by the "near top" / "scrolled back up past
// show tolerance" scroll transitions and by disconnectedCallback's teardown.
// `host` is the <app-headroom> instance itself: headroom-hide-top/bottom are
// mirrored onto it (see the hide branch in _startTracking's onScroll) so its
// own shadow-scoped CSS can drive condensed-view visibility independently of
// target, which it's a peer of, not a descendant of.
function resetToShownState(el: HTMLElement, host: HTMLElement): void {
    el.removeAttribute('headroom-unpinned');
    el.removeAttribute('headroom-hide-top');
    el.removeAttribute('headroom-hide-bottom');
    el.style.paddingTop = '';
    el.style.paddingBottom = '';
    host.removeAttribute('headroom-hide-top');
    host.removeAttribute('headroom-hide-bottom');
}

// ── Component ─────────────────────────────────────────────────────────────────

@customElement('app-headroom')
export class AppHeadroom extends LitElement {

    // display: contents removes this host's own box from layout/paint entirely - only
    // the condensed-top/condensed-bottom wrapper elements below (rendered only when a
    // renderer is actually configured - see render()) actually take up any space.
    static override styles = css`
        :host {
            display: contents;
        }

        /* AppHeadroom-owned wrapper around each condensed slot - handles positioning,
           centering, and the show/hide transition, so an app-supplied condensed
           Component needs zero layout code of its own to be positioned correctly.
           display: flex + justify-content: center centers a narrower-than-full-width
           child for free; a width: 100% child (the ribbon shape's own frame - see
           AppHeadroom.java's RibbonCondensedBar) fills it edge-to-edge instead.
           Positioned at its final resting spot from the start - the transition is a
           plain cross-fade ("replace"), not a directional slide chasing the real bar's
           translateY, so there's no slide-direction/timing to keep in sync with it beyond
           starting in the same paint frame (see the hide branch in _startTracking's
           onScroll, which sets the target and host attributes together, synchronously).
           pointer-events: none while hidden so an invisible-but-still-in-the-DOM wrapper
           never intercepts clicks meant for whatever's underneath it.
           Safe-area clearance (inset-inline/inset-block below) is shape-specific, not
           set here - see the two shape-scoped rule pairs that follow this one:
           asFloating() reconciles it via max() since AppHeadroom owns the whole gap for
           that shape; asRibbon() stays fully flush here since its own frame handles
           clearance internally instead (its background needs to reach the true edge,
           which an inset on this wrapper would prevent - the bug this whole shape-aware
           redesign exists to fix). */
        .condensed-top-wrapper,
        .condensed-bottom-wrapper {
            position: fixed;
            display: flex;
            justify-content: center;
            opacity: 0;
            pointer-events: none;
            transition: opacity var(--headroom-transition-duration, 600ms) ease;
        }

        /* Floating: AppHeadroom owns the whole gap on every side, reconciled - not
           simply added - against the safe area via max(), the same pattern the real
           landscape bottom bar's own padding-inline already uses.
           --headroom-condensed-gap is a plain CSS override point, same convention as
           the z-index custom properties below - nothing in this library ever sets it
           itself, an app that wants a different default gap sets it directly. */
        :host([condensed-top-shape='floating']) .condensed-top-wrapper {
            inset-inline-start: max(var(--headroom-condensed-gap, 8px), env(safe-area-inset-left, 0px));
            inset-inline-end: max(var(--headroom-condensed-gap, 8px), env(safe-area-inset-right, 0px));
            inset-block-start: max(var(--headroom-condensed-gap, 8px), env(safe-area-inset-top, 0px));
            z-index: var(--headroom-condensed-top-z-index, 200);
        }

        :host([condensed-bottom-shape='floating']) .condensed-bottom-wrapper {
            inset-inline-start: max(var(--headroom-condensed-gap, 8px), env(safe-area-inset-left, 0px));
            inset-inline-end: max(var(--headroom-condensed-gap, 8px), env(safe-area-inset-right, 0px));
            inset-block-end: max(var(--headroom-condensed-gap, 8px), env(safe-area-inset-bottom, 0px));
            z-index: var(--headroom-condensed-bottom-z-index, 200);
        }

        /* Ribbon: wrapper stays fully flush to the true edge on every side - the
           frame (AppHeadroom.java's RibbonCondensedBar) handles its own safe-area
           clearance internally via padding, so its background can still reach the
           true edge while only its content is inset. */
        :host([condensed-top-shape='ribbon']) .condensed-top-wrapper {
            inset-inline: 0;
            inset-block-start: 0;
            z-index: var(--headroom-condensed-top-z-index, 200);
        }

        :host([condensed-bottom-shape='ribbon']) .condensed-bottom-wrapper {
            inset-inline: 0;
            inset-block-end: 0;
            z-index: var(--headroom-condensed-bottom-z-index, 200);
        }

        /* headroom-hide-top/bottom mirrored onto this host (see the hide branch in
           _startTracking's onScroll, and resetToShownState) - this selector is what
           makes a condensed view fade in exactly when the user has scrolled past the
           hide threshold, purely on scroll position: unlike the real bar's own
           headroom-hide-top/bottom (set on target, gated additionally by the
           pinned-rail geometry check), the host's copy is only ever also gated by an
           explicit setTopBarPinned/setBottomBarPinned - see the file header comment
           above for why the automatic geometry check doesn't apply here too. */
        :host([headroom-hide-top]) .condensed-top-wrapper,
        :host([headroom-hide-bottom]) .condensed-bottom-wrapper {
            opacity: 1;
            pointer-events: auto;
        }
    `;

    // Pixels from the top below which chrome is always shown (never hidden at the top of the page).
    @property({ attribute: 'top-offset',     type: Number }) topOffset     = 100;
    // Minimum downward scroll from the last show-point before chrome hides (avoids hiding on tiny scrolls).
    @property({ attribute: 'hide-tolerance', type: Number }) hideTolerance = 30;
    // Minimum upward scroll from the last hide-point before chrome re-appears.
    @property({ attribute: 'show-tolerance', type: Number }) showTolerance = 30;
    // Milliseconds for the show/hide slide + padding transitions (see AppHeadroom.java's
    // setTransitionDuration) — applied to the target as a CSS custom property in
    // _attachToTarget(), since the transition rules themselves live in the shared
    // global stylesheet, not per-instance.
    @property({ attribute: 'transition-duration', type: Number }) transitionDuration = 600;

    // Explicit per-bar overrides (see AppHeadroom.java's setTopBarPinned/
    // setBottomBarPinned) — take precedence over looksLikeAPinnedRail() below.
    @property({ attribute: 'top-bar-pinned',    type: Boolean }) topBarPinned    = false;
    @property({ attribute: 'bottom-bar-pinned', type: Boolean }) bottomBarPinned = false;

    // Which shape (if any) each bar's condensed view is - see AppHeadroom.java's
    // CondensedBar#asFloating()/asRibbon(). null means "none configured". Java
    // sets/clears this directly, at the exact moment it adds/removes a bar's
    // content - this side never infers presence by observing slot/DOM content
    // itself. Drives both whether a bar's wrapper <div> is rendered at all (see
    // render() below) and which shape-scoped CSS rule applies (see the styles above).
    @property({ attribute: 'condensed-top-shape' })    condensedTopShape: string | null    = null;
    @property({ attribute: 'condensed-bottom-shape' }) condensedBottomShape: string | null = null;

    // Server-visible pinned/unpinned state (see AppHeadroom.isPinned() / addPinnedChangeListener).
    // attribute: false — Flow's @Synchronize reads the client JS property via the
    // property-sync RPC, not a DOM attribute; this is only ever set programmatically.
    @property({ type: Boolean, attribute: false }) pinned = true;

    // Whether the scroll-tracking gesture itself runs at all (see AppHeadroom.java's
    // setActivationPredicate/isActive) — computed server-side from device type/orientation,
    // pushed here via property sync (not an attribute, same reasoning as `pinned`: needs to
    // reliably round-trip a default-true value back to explicit false, and back again).
    @property({ type: Boolean, attribute: false }) active = true;

    // The vaadin-app-layout this instance affects, set via executeJs("this.target = $0", ...)
    // from AppHeadroom.java's applyTo() — not a DOM attribute (it's a live element reference,
    // not a serializable value), and deliberately not discovered via this.closest(...): this
    // element is a peer under the UI's root, never a light-DOM child of its target, so DOM
    // ancestry can't be used to find it.
    @property({ attribute: false }) target: HTMLElement | null = null;

    private _target: HTMLElement | null = null;
    private _cleanup: (() => void) | null = null;

    // Updates `pinned` and notifies the server, but only on an actual state change —
    // avoids firing on every scroll-driven rAF tick.
    private _setPinned(value: boolean) {
        if (this.pinned === value) return;
        this.pinned = value;
        this.dispatchEvent(new CustomEvent('pinned-changed', { detail: { pinned: value } }));
    }

    // updated — Lit lifecycle hook, called after any reactive property changes.
    // `target` arrives via executeJs("this.target = $0", ...) from AppHeadroom.java's
    // applyTo(), which runs *after* connectedCallback() already fired — so the actual
    // setup happens here, reacting to the property, rather than in connectedCallback().
    // Guarded by `!this._target` since a given instance's target is set exactly once.
    protected override updated(changedProperties: PropertyValues) {
        super.updated(changedProperties);
        // Deliberately else-if: target and active can arrive in the very same batch (the
        // initial property sync isn't guaranteed to split them across separate update
        // cycles). _attachToTarget() already starts/skips tracking based on `active` once,
        // as part of the initial bind — falling through to the active-branch below in that
        // same pass would start tracking a second time.
        if (changedProperties.has('target') && this.target && !this._target) {
            this._attachToTarget(this.target);
        } else if (changedProperties.has('active') && this._target) {
            if (this.active) {
                this._startTracking(this._target);
            } else {
                this._stopTracking();
            }
        }
    }

    private _attachToTarget(target: HTMLElement) {
        if (target.hasAttribute('headroom-enabled')) {
            console.warn(
                '<app-headroom>: this <vaadin-app-layout> already has headroom behavior ' +
                'attached from another <app-headroom> instance; ignoring this duplicate. ' +
                'Only one <app-headroom> per AppLayout is supported.'
            );
            return;
        }

        this._target = target;
        target.style.setProperty('--headroom-transition-duration', `${this.transitionDuration}ms`);
        // Also set on this (the host): condensed-view transitions live in this element's
        // own shadow-scoped styles, not target's, so they can't inherit target's copy.
        this.style.setProperty('--headroom-transition-duration', `${this.transitionDuration}ms`);
        target.setAttribute('headroom-enabled', '');  // activates CSS transitions above

        if (this.active) {
            this._startTracking(target);
        }
    }

    // Wires up the scroll listener. Paired with _stopTracking(); re-entrant across
    // pause/resume cycles driven by the `active` property, not just the initial bind.
    private _startTracking(target: HTMLElement) {
        // Reflects `active` onto the target as a real DOM attribute (the property
        // itself is attribute:false) so the CSS rules that depend on scroll-tracking
        // actually running - body-scrolling mode and the landscape-fixed bottom bar -
        // can gate on it too, not just [headroom-enabled]. Set synchronously, before
        // the rAF-deferred setup below, so the CSS takes effect immediately.
        target.setAttribute('headroom-active', '');

        const OFFSET         = this.topOffset;
        const HIDE_TOLERANCE = this.hideTolerance;
        const SHOW_TOLERANCE = this.showTolerance;

        // Defer scroll-listener setup: vaadin-app-layout renders its shadow DOM
        // asynchronously, so [part="content"] doesn't exist until the next frame.
        // requestAnimationFrame schedules the callback just before the next browser repaint.
        requestAnimationFrame(() => {
            if (!this._target || !this.active) return;  // disconnected/deactivated before rAF fired

            // [content] is the inner scroll container inside vaadin-app-layout's shadow DOM.
            const contentEl = target.shadowRoot?.querySelector('[content]') as HTMLElement | null;
            // The bar elements themselves, cached once — same DOM nodes for the component's
            // lifetime, though their computed position/shape can change dynamically (e.g. a
            // companion layout switching a nav bar in/out of rail mode on viewport resize),
            // which is why looksLikeAPinnedRail() re-checks live style at each transition.
            const topEl    = target.shadowRoot?.querySelector('[part~="navbar-top"]') as HTMLElement | null;
            const bottomEl = target.shadowRoot?.querySelector('[part~="navbar-bottom"]') as HTMLElement | null;

            // Combine both scroll sources: window.scrollY (mobile page-scroll) and
            // contentEl.scrollTop (desktop content-scroll). Only one is non-zero at a time.
            const getY    = () => window.scrollY + (contentEl?.scrollTop ?? 0);
            const getMaxY = () => (contentEl && contentEl.scrollTop > 0)
                ? contentEl.scrollHeight - contentEl.clientHeight
                : document.documentElement.scrollHeight - window.innerHeight;

            let pinY    = getY();  // y where chrome was last shown
            let unpinY  = 0;       // y where chrome was last hidden
            let ticking = false;   // rAF debounce: only one frame callback queued at a time

            const onScroll = () => {
                if (ticking) return;
                ticking = true;
                requestAnimationFrame(() => {
                    const y    = getY();
                    const maxY = getMaxY();

                    // Ignore bottom overscroll/bounce (iOS rubber-band effect).
                    if (y > maxY) { ticking = false; return; }

                    const currentlyPinned = !target.hasAttribute('headroom-unpinned');

                    if (y <= OFFSET) {
                        // Always show near the top of the page.
                        if (!currentlyPinned) {
                            resetToShownState(target, this);
                            pinY = y;
                            this._setPinned(true);
                        }
                    } else if (currentlyPinned) {
                        if ((y - pinY) > HIDE_TOLERANCE) {
                            // Scrolled down far enough from most recent upward position → hide.
                            target.setAttribute('headroom-unpinned', '');
                            // The real bar's own attribute: gated by both the explicit pin and
                            // the automatic pinned-rail geometry guess, same as always.
                            if (!this.topBarPinned && !looksLikeAPinnedRail(topEl)) {
                                target.setAttribute('headroom-hide-top', '');
                            }
                            if (!this.bottomBarPinned && !looksLikeAPinnedRail(bottomEl)) {
                                target.setAttribute('headroom-hide-bottom', '');
                            }
                            // Mirrored onto this (the <app-headroom> host) too - this element is
                            // a peer of target, not a descendant, so its own shadow-scoped CSS
                            // (condensed-view visibility) can't react to an attribute set only on
                            // target. Deliberately NOT also gated by looksLikeAPinnedRail() here
                            // (see the file header comment for why): only the explicit pin
                            // suppresses a condensed view, since the automatic geometry guess has
                            // no way to know whether it's even related to one.
                            if (!this.topBarPinned) {
                                this.setAttribute('headroom-hide-top', '');
                            }
                            if (!this.bottomBarPinned) {
                                this.setAttribute('headroom-hide-bottom', '');
                            }
                            if (contentEl && contentEl.scrollTop > 0) {
                                target.style.paddingTop = '0';    // desktop: fill the top gap
                            } else {
                                target.style.paddingBottom = '0'; // mobile: collapse bottom bar space
                            }
                            unpinY = y;
                            this._setPinned(false);
                        } else if (y < pinY) {
                            pinY = y;
                        }
                    } else {
                        if ((unpinY - y) > SHOW_TOLERANCE) {
                            // Scrolled up enough from most recent downward position → show.
                            resetToShownState(target, this);
                            pinY = y;
                            this._setPinned(true);
                        } else if (y > unpinY) {
                            // Cap unpinY below the true bottom so rubber-band deceleration
                            // (which can bounce SHOW_TOLERANCE+ px) isn't mistaken for
                            // intentional upward scrolling.
                            unpinY = Math.min(y, maxY - SHOW_TOLERANCE * 2);
                        }
                    }

                    ticking = false;
                });
            };

            // { passive: true } tells the browser this listener never calls
            // preventDefault() — allows the browser to scroll immediately without
            // waiting for our callback, keeping scrolling smooth on mobile.
            window.addEventListener('scroll', onScroll, { passive: true });
            contentEl?.addEventListener('scroll', onScroll, { passive: true });
            this._cleanup = () => {
                window.removeEventListener('scroll', onScroll);
                contentEl?.removeEventListener('scroll', onScroll);
            };
        });
    }

    // Tears down the scroll listener and restores the target to its default shown
    // state, without clearing `headroom-enabled`/_target — a pause, not a detach.
    // Paired with _startTracking(); also reused by disconnectedCallback's full teardown.
    private _stopTracking() {
        if (this._cleanup) { this._cleanup(); this._cleanup = null; }
        if (this._target) {
            resetToShownState(this._target, this);
            this._target.removeAttribute('headroom-active');
        }
        this._setPinned(true);
    }

    // disconnectedCallback — equivalent to Vaadin's onDetach() / @PreDestroy.
    // Removes scroll listeners and restores the layout to its default state.
    override disconnectedCallback() {
        super.disconnectedCallback();
        this._stopTracking();
        if (this._target) {
            this._target.removeAttribute('headroom-enabled');
            this._target = null;
        }
    }

    // Each wrapper (see the styles above) is only rendered at all when the matching
    // condensedTopShape/condensedBottomShape property is non-null (AppHeadroom.java's
    // CondensedBar#asFloating()/asRibbon() with a non-null renderer) - with neither
    // configured, render() produces nothing, same as before this feature existed.
    override render() {
        return html`
            ${this.condensedTopShape
                ? html`<div class="condensed-top-wrapper"><slot name="condensed-top"></slot></div>`
                : nothing}
            ${this.condensedBottomShape
                ? html`<div class="condensed-bottom-wrapper"><slot name="condensed-bottom"></slot></div>`
                : nothing}
        `;
    }
}
