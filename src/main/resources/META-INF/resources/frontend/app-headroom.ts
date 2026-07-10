/*
 * Browser-side half of AppHeadroom.java (loaded via @JsModule).
 *
 * Hides the top/bottom navigation bars when the user scrolls down and reveals
 * them when scrolling up ("Headroom.js" pattern). Also injects global CSS for
 * the mobile layout: body-scrolling on touch devices, safe-area padding, fixed
 * landscape bottom bar, and nav-item active-state colours.
 *
 * The CSS must live here (not in a Java @StyleSheet) because some rules target
 * vaadin-app-layout's internal shadow DOM via ::part() — a CSS selector that
 * can cross shadow-DOM boundaries from outside the component.
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
    /* Body-scrolling mode: touch devices only. Content padding lives inside the
       scroll container so scrolled content naturally fills the space vacated by
       the chrome. Desktop keeps the default Vaadin content-scrolling mode.
       "pointer: coarse" identifies touch (finger) input devices. */
    @media (pointer: coarse) {
        html {
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

    /* translateY(-100%) slides the top bar upward by its own height (off-screen). */
    vaadin-app-layout[headroom-enabled][headroom-unpinned]::part(navbar-top) {
        transform: translateY(-100%);
    }

    /* translateY(100%) slides the bottom bar downward by its own height. */
    vaadin-app-layout[headroom-enabled][headroom-unpinned]::part(navbar-bottom) {
        transform: translateY(100%);
    }

    /* Animate the layout's padding so content expands smoothly into the vacated space. */
    vaadin-app-layout[headroom-enabled] {
        transition: padding-top 600ms ease, padding-bottom 600ms ease;
    }

    /* Tighten the bottom bar padding so it hugs its content. */
    vaadin-app-layout::part(navbar-bottom) {
        padding-top: var(--lumo-space-xs);
        padding-bottom: var(--lumo-space-xs);
    }

    /* In PWA standalone mode (installed to the home screen), extend the bottom
       bar into the safe-area so content doesn't sit behind the home indicator.
       env(safe-area-inset-bottom) is a CSS variable the browser provides only
       in standalone mode on notch/gesture-bar devices (e.g. iPhone). */
    @media (display-mode: standalone) {
        vaadin-app-layout::part(navbar-bottom) {
            padding-bottom: env(safe-area-inset-bottom, var(--lumo-space-xs));
        }
    }

    /* Active state for bottom nav items */
    .touch-nav-item.active {
        color: var(--lumo-primary-color);
    }

    /* Overflow popover buttons: secondary by default, primary when active.
       ::part(label/prefix) reaches into vaadin-button's shadow DOM. */
    vaadin-button.overflow-nav-item:not(.active)::part(label),
    vaadin-button.overflow-nav-item:not(.active)::part(prefix) {
        color: var(--lumo-secondary-text-color);
    }

    /* Landscape on touch: pin bottom bar to viewport bottom.
       will-change: auto clears the stacking context that confines position: fixed. */
    @media (orientation: landscape) and (pointer: coarse) {
        vaadin-app-layout::part(navbar-bottom) {
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

    /* Force overlay drawer mode on rail devices (portrait tablet exceeds the 800px media query). */
    vaadin-app-layout[nav-rail] {
        --vaadin-app-layout-drawer-overlay: true;
    }

    /* Rail: pin navbar-bottom slot to the left edge, below the top bar. */
    vaadin-app-layout[nav-rail]::part(navbar-bottom) {
        position: fixed !important;
        inset-block-start: var(--vaadin-app-layout-navbar-offset-top, 3.5rem);
        inset-block-end: 0;
        inset-inline-start: 0;
        width: var(--nav-rail-width, 5rem);
        z-index: 200;
        will-change: auto;
        padding-block-start: var(--lumo-space-s);
        padding-block-end: 0;
        background: var(--lumo-contrast-5pct);
        border-inline-end: 1px solid var(--lumo-contrast-10pct);
    }

    /* Drawer slides over the rail when opened. */
    vaadin-app-layout[nav-rail]::part(drawer) {
        z-index: 201;
    }

    /* Rail items: centered, with vertical padding for comfortable tap targets. */
    vaadin-app-layout[nav-rail] .touch-nav-item {
        padding-block: var(--lumo-space-s);
    }

    /* Prevent headroom from sliding the rail off-screen on scroll. */
    vaadin-app-layout[nav-rail][headroom-unpinned]::part(navbar-bottom) {
        transform: none;
    }
`);
document.adoptedStyleSheets = [...document.adoptedStyleSheets, GLOBAL_STYLES];

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

    private _target: HTMLElement | null = null;
    private _cleanup: (() => void) | null = null;

    // connectedCallback — browser lifecycle hook equivalent to Vaadin's onAttach().
    // Called when this element is inserted into the DOM.
    override connectedCallback() {
        super.connectedCallback();

        const target = this.closest('vaadin-app-layout') as HTMLElement | null;
        if (!target || target.hasAttribute('headroom-enabled')) return;  // guard: attach once

        // vaadin-app-layout reads --vaadin-app-layout-drawer-overlay in its own
        // connectedCallback(), which fires before this child element connects.
        // A synthetic resize re-runs _updateOverlayMode() now that GLOBAL_STYLES is live.
        if (target.hasAttribute('nav-rail')) {
            requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
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
                            target.style.paddingTop = '';
                            target.style.paddingBottom = '';
                            pinY = y;
                        }
                    } else if (pinned) {
                        if ((y - pinY) > HIDE_TOLERANCE) {
                            // Scrolled down far enough from most recent upward position → hide.
                            target.setAttribute('headroom-unpinned', '');
                            if (contentEl && contentEl.scrollTop > 0) {
                                target.style.paddingTop = '0';    // desktop: fill the top gap
                            } else {
                                target.style.paddingBottom = '0'; // mobile: collapse bottom bar space
                            }
                            unpinY = y;
                        } else if (y < pinY) {
                            pinY = y;
                        }
                    } else {
                        if ((unpinY - y) > SHOW_TOLERANCE) {
                            // Scrolled up enough from most recent downward position → show.
                            target.removeAttribute('headroom-unpinned');
                            target.style.paddingTop = '';
                            target.style.paddingBottom = '';
                            pinY = y;
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
            this._target.style.paddingTop = '';
            this._target.style.paddingBottom = '';
            this._target = null;
        }
    }

    /** No visual output — this element is purely behavioural. */
    override render() { return nothing; }
}
