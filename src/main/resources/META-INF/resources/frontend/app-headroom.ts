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
 */

import { LitElement, css, nothing } from 'lit';

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
const GLOBAL_STYLES = new CSSStyleSheet();
GLOBAL_STYLES.replaceSync(`
    /* Body-scrolling mode: touch devices only, and only on a page that actually
       has headroom attached to its AppLayout — :has() lets us gate a rule on
       <html> by an attribute that only ever lives on a descendant element.
       Content padding lives inside the scroll container so scrolled content
       naturally fills the space vacated by the chrome. Desktop keeps the
       default Vaadin content-scrolling mode. "pointer: coarse" identifies
       touch (finger) input devices. */
    @media (pointer: coarse) {
        html:has(vaadin-app-layout[headroom-enabled]) {
            height: auto;
        }
    }

    /* Headroom-style chrome hide/show.
       ::part(navbar-top/bottom) reaches INTO vaadin-app-layout's shadow DOM
       to animate its internal bar slots. will-change promotes the layers to
       the GPU so the slide is smooth even at 60fps on mobile. */
    vaadin-app-layout[headroom-enabled]::part(navbar-top),
    vaadin-app-layout[headroom-enabled]::part(navbar-bottom) {
        transition: transform 600ms ease;
        will-change: transform;
    }

    /* translateY(-100%) slides the top bar upward by its own height (off-screen).
       Gated on headroom-hide-top rather than headroom-unpinned directly: our own
       JS only sets this marker when navbar-top isn't a pinned rail (see
       looksLikeAPinnedRail() in connectedCallback). */
    vaadin-app-layout[headroom-hide-top]::part(navbar-top) {
        transform: translateY(-100%);
    }

    /* translateY(100%) slides the bottom bar downward by its own height. Same
       pinned-rail deferral as navbar-top above. */
    vaadin-app-layout[headroom-hide-bottom]::part(navbar-bottom) {
        transform: translateY(100%);
    }

    /* Animate the layout's padding so content expands smoothly into the vacated space. */
    vaadin-app-layout[headroom-enabled] {
        transition: padding-top 600ms ease, padding-bottom 600ms ease;
    }

    /* Tighten the bottom bar padding so it hugs its content. */
    vaadin-app-layout[headroom-enabled]::part(navbar-bottom) {
        padding-top: var(--lumo-space-xs);
        padding-bottom: var(--lumo-space-xs);
    }

    /* In PWA standalone mode (installed to the home screen), extend the bottom
       bar into the safe-area so content doesn't sit behind the home indicator.
       env(safe-area-inset-bottom) is a CSS variable the browser provides only
       in standalone mode on notch/gesture-bar devices (e.g. iPhone). */
    @media (display-mode: standalone) {
        vaadin-app-layout[headroom-enabled]::part(navbar-bottom) {
            padding-bottom: env(safe-area-inset-bottom, var(--lumo-space-xs));
        }
    }

    /* Landscape on touch: pin bottom bar to viewport bottom.
       will-change: auto clears the stacking context that confines position: fixed.
       Note: this makes navbar-bottom position:fixed for our own layout reasons,
       unrelated to any pinned-rail concept — it stays full-width/short (a bar,
       not a rail), so looksLikeAPinnedRail() below still correctly hides it. */
    @media (orientation: landscape) and (pointer: coarse) {
        vaadin-app-layout[headroom-enabled]::part(navbar-bottom) {
            position: fixed !important;
            inset-block-end: 0;
            inset-inline-start: 0;
            inset-inline-end: 0;
            width: 100%;
            height: auto;
            z-index: 200;
            will-change: auto;
        }
    }
`);
document.adoptedStyleSheets = [...document.adoptedStyleSheets, GLOBAL_STYLES];

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

// ── Component ─────────────────────────────────────────────────────────────────

@customElement('app-headroom')
export class AppHeadroom extends LitElement {

    // This element is purely behavioral — no visible output.
    static override styles = css`:host { display: none; }`;

    // Pixels from the top below which chrome is always shown (never hidden at the top of the page).
    @property({ attribute: 'top-offset',     type: Number }) topOffset     = 100;
    // Minimum downward scroll from the last show-point before chrome hides (avoids hiding on tiny scrolls).
    @property({ attribute: 'hide-tolerance', type: Number }) hideTolerance = 30;
    // Minimum upward scroll from the last hide-point before chrome re-appears.
    @property({ attribute: 'show-tolerance', type: Number }) showTolerance = 30;

    // Explicit per-bar overrides (see AppHeadroom.java's setTopBarPinned/
    // setBottomBarPinned) — take precedence over looksLikeAPinnedRail() below.
    @property({ attribute: 'top-bar-pinned',    type: Boolean }) topBarPinned    = false;
    @property({ attribute: 'bottom-bar-pinned', type: Boolean }) bottomBarPinned = false;

    // Server-visible pinned/unpinned state (see AppHeadroom.isPinned() / addPinnedChangeListener).
    // attribute: false — Flow's @Synchronize reads the client JS property via the
    // property-sync RPC, not a DOM attribute; this is only ever set programmatically.
    @property({ type: Boolean, attribute: false }) pinned = true;

    private _target: HTMLElement | null = null;
    private _cleanup: (() => void) | null = null;

    // Updates `pinned` and notifies the server, but only on an actual state change —
    // avoids firing on every scroll-driven rAF tick.
    private _setPinned(value: boolean) {
        if (this.pinned === value) return;
        this.pinned = value;
        this.dispatchEvent(new CustomEvent('pinned-changed', { detail: { pinned: value } }));
    }

    // connectedCallback — browser lifecycle hook equivalent to Vaadin's onAttach().
    // Called when this element is inserted into the DOM.
    override connectedCallback() {
        super.connectedCallback();

        const target = this.closest('vaadin-app-layout') as HTMLElement | null;
        if (!target) {
            console.warn(
                '<app-headroom>: no <vaadin-app-layout> ancestor found. Place ' +
                '<app-headroom> as a child of a <vaadin-app-layout> (or use ' +
                'AppHeadroom.applyTo(appLayout) from Java) for scroll-hide behavior to work.'
            );
            return;
        }
        if (target.hasAttribute('headroom-enabled')) {
            console.warn(
                '<app-headroom>: this <vaadin-app-layout> already has headroom behavior ' +
                'attached from another <app-headroom> instance; ignoring this duplicate. ' +
                'Only one <app-headroom> per AppLayout is supported.'
            );
            return;
        }

        this._target = target;
        target.setAttribute('headroom-enabled', '');  // activates CSS transitions above

        const OFFSET         = this.topOffset;
        const HIDE_TOLERANCE = this.hideTolerance;
        const SHOW_TOLERANCE = this.showTolerance;

        // Defer scroll-listener setup: vaadin-app-layout renders its shadow DOM
        // asynchronously, so [part="content"] doesn't exist until the next frame.
        // requestAnimationFrame schedules the callback just before the next browser repaint.
        requestAnimationFrame(() => {
            if (!this._target) return;  // disconnected before rAF fired

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

            let lastY   = getY();
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
                    if (y > maxY) { lastY = y; ticking = false; return; }

                    const pinned = !target.hasAttribute('headroom-unpinned');

                    if (y <= OFFSET) {
                        // Always show near the top of the page.
                        if (!pinned) {
                            target.removeAttribute('headroom-unpinned');
                            target.removeAttribute('headroom-hide-top');
                            target.removeAttribute('headroom-hide-bottom');
                            target.style.paddingTop = '';
                            target.style.paddingBottom = '';
                            pinY = y;
                            this._setPinned(true);
                        }
                    } else if (pinned) {
                        if ((y - pinY) > HIDE_TOLERANCE) {
                            // Scrolled down far enough from most recent upward position → hide.
                            target.setAttribute('headroom-unpinned', '');
                            if (!this.topBarPinned && !looksLikeAPinnedRail(topEl)) {
                                target.setAttribute('headroom-hide-top', '');
                            }
                            if (!this.bottomBarPinned && !looksLikeAPinnedRail(bottomEl)) {
                                target.setAttribute('headroom-hide-bottom', '');
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
                            target.removeAttribute('headroom-unpinned');
                            target.removeAttribute('headroom-hide-top');
                            target.removeAttribute('headroom-hide-bottom');
                            target.style.paddingTop = '';
                            target.style.paddingBottom = '';
                            pinY = y;
                            this._setPinned(true);
                        } else if (y > unpinY) {
                            // Cap unpinY below the true bottom so rubber-band deceleration
                            // (which can bounce SHOW_TOLERANCE+ px) isn't mistaken for
                            // intentional upward scrolling.
                            unpinY = Math.min(y, maxY - SHOW_TOLERANCE * 2);
                        }
                    }

                    lastY   = y;
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

    // disconnectedCallback — equivalent to Vaadin's onDetach() / @PreDestroy.
    // Removes scroll listeners and restores the layout to its default state.
    override disconnectedCallback() {
        super.disconnectedCallback();
        if (this._cleanup) { this._cleanup(); this._cleanup = null; }
        if (this._target) {
            this._target.removeAttribute('headroom-enabled');
            this._target.removeAttribute('headroom-unpinned');
            this._target.removeAttribute('headroom-hide-top');
            this._target.removeAttribute('headroom-hide-bottom');
            this._target.style.paddingTop = '';
            this._target.style.paddingBottom = '';
            this._target = null;
        }
        this._setPinned(true);
    }

    /** No visual output — this element is purely behavioural. */
    override render() { return nothing; }
}
