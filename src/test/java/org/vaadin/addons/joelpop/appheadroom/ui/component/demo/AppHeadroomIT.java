package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppHeadroomIT {

    private static final String BASE_URL = "http://localhost:8099";

    private static Playwright playwright;
    private static Browser browser;
    private static boolean headed;
    private static int pauseMs;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();

        headed = Boolean.parseBoolean(System.getProperty("playwright.headed", "false"));
        pauseMs = Integer.parseInt(System.getProperty("playwright.pause", "1500"));
        var options = new BrowserType.LaunchOptions().setHeadless(!headed);
        if (headed) {
            options.setSlowMo(250); // otherwise the scroll steps flash by too fast to see
        }
        browser = playwright.chromium().launch(options);
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void newPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        context.close();
    }

    /** CSS transition duration for the navbar-top/bottom slide (see app-headroom.ts's
     *  injected `transition: transform 600ms ease`) plus margin, so bounding-box checks
     *  read the settled position rather than a mid-animation frame. */
    private static final int TRANSITION_SETTLE_MS = 700;

    /** Extra pause after a settled transition, only when running headed
     *  (-Dplaywright.headed=true). The real CSS transition (~600ms) completes well
     *  within TRANSITION_SETTLE_MS, but that's easy to miss with a human eye given
     *  each test opens/closes its own browser window in quick succession — this
     *  gives a person watching an actual window enough time to see the result.
     *  Length configurable: -Dplaywright.pause=<ms> (default 1500). */
    private void pauseForHumanIfHeaded() {
        if (headed) {
            page.waitForTimeout(pauseMs);
        }
    }

    @Test
    void scrollingDownPastHideTolerance_unpinsLayout_andNotifiesServer() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator pinnedState = page.locator("#" + HeadroomDemoView.PINNED_STATE_ID);
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");

        assertThat(pinnedState).hasText("pinned");
        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertTrue(navbarTop.boundingBox().y >= 0,
                "navbar-top should be on-screen while pinned, was y=" + navbarTop.boundingBox().y);

        scrollTo(contentEl, 500); // past topOffset(100) + hideTolerance(40)

        assertThat(layout).hasAttribute("headroom-unpinned", "");
        assertThat(pinnedState).hasText("unpinned"); // proves the full client -> @Synchronize -> listener -> DOM round trip
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y < 0,
                "navbar-top should be translated off-screen once unpinned, was y=" + navbarTop.boundingBox().y);
        pauseForHumanIfHeaded();
    }

    @Test
    void scrollingBackUpPastShowTolerance_repinsLayout_andNotifiesServer() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator pinnedState = page.locator("#" + HeadroomDemoView.PINNED_STATE_ID);
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");

        scrollTo(contentEl, 500);
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y < 0,
                "navbar-top should be translated off-screen once unpinned, was y=" + navbarTop.boundingBox().y);
        pauseForHumanIfHeaded();

        scrollTo(contentEl, 500 - 41); // > showTolerance(40) upward from the unpin point

        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertThat(pinnedState).hasText("pinned");
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y >= 0,
                "navbar-top should be back on-screen once re-pinned, was y=" + navbarTop.boundingBox().y);
        pauseForHumanIfHeaded();
    }

    @Test
    void scrollingWithinShowTolerance_staysUnpinned() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator contentEl = page.locator("vaadin-app-layout div[content]");

        scrollTo(contentEl, 500);
        assertThat(layout).hasAttribute("headroom-unpinned", "");

        scrollTo(contentEl, 500 - 30); // < showTolerance(40) upward from the unpin point: should NOT re-pin
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertThat(layout).hasAttribute("headroom-unpinned", "");
    }

    @Test
    void scrollingWithinHideTolerance_afterRepinning_staysPinned() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator contentEl = page.locator("vaadin-app-layout div[content]");

        // pinY starts at ~0 on page load and only advances while scrolling up while
        // pinned, so a fresh page load can't reach "past topOffset(100) but within
        // hideTolerance(40)" directly (100 > 40 means crossing topOffset from y=0
        // already exceeds hideTolerance too). To reach a realistic baseline where
        // hideTolerance is actually a buffer, first hide then re-pin, which
        // re-establishes pinY at the re-pin position.
        scrollTo(contentEl, 500);
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        scrollTo(contentEl, 500 - 45); // > showTolerance(40): re-pins, pinY becomes ~455
        assertThat(layout).not().hasAttribute("headroom-unpinned", "");

        scrollTo(contentEl, 500 - 45 + 30); // +30 from new pinY: < hideTolerance(40), should stay pinned
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
    }

    @Test
    void removingHeadroomFromLayout_resetsTargetToDefaultState() {
        page.navigate(BASE_URL + "/headroom-demo-detach");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator pinnedState = page.locator("#" + DetachResetDemoView.PINNED_STATE_ID);
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        // dispatchEvent (not click()) bypasses Playwright's scroll-into-view actionability
        // check, which would otherwise reset our deliberately-unpinned scroll position.
        Locator removeButton = page.locator("#" + DetachResetDemoView.REMOVE_BUTTON_ID);

        scrollTo(contentEl, 500);
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        assertThat(pinnedState).hasText("unpinned");

        removeButton.dispatchEvent("click");

        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertThat(layout).not().hasAttribute("headroom-enabled", "");
        assertThat(pinnedState).hasText("pinned"); // proves the reset reaches the server, not just the client attribute
    }

    @Test
    void explicitlyPinnedBottomBar_isNeverTransformHidden() {
        page.navigate(BASE_URL + "/headroom-demo-explicit-pin");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator layout = page.locator("vaadin-app-layout");
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator bottomPart = page.locator("vaadin-app-layout div[part~='navbar-bottom']");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        // Overall scroll state still tracks correctly...
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        // ...but the explicitly-pinned bar is never transform-hidden, even though
        // it's an ordinary bar (not fixed, not rail-shaped) that looksLikeAPinnedRail()
        // alone would NOT have exempted.
        String transform = (String) bottomPart.evaluate("el => getComputedStyle(el).transform");
        assertTrue(transform.equals("none"),
                "explicitly-pinned navbar-bottom should NOT be transformed/hidden, was: " + transform);
    }

    @Test
    void withinTopOffset_neverUnpins() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator contentEl = page.locator("vaadin-app-layout div[content]");

        scrollTo(contentEl, 60); // < topOffset(100): always shown, regardless of tolerance
        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
    }

    @Test
    void missingAppLayoutAncestor_logsConsoleWarning() {
        List<String> messages = new CopyOnWriteArrayList<>();
        page.onConsoleMessage(msg -> messages.add(msg.text())); // must attach before navigate()

        page.navigate(BASE_URL + "/headroom-demo-no-ancestor");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertTrue(messages.stream().anyMatch(m -> m.contains("no <vaadin-app-layout> ancestor found")),
                "expected ancestor-missing console.warn; got: " + messages);
    }

    @Test
    void duplicateAttachToSameLayout_logsConsoleWarning() {
        List<String> messages = new CopyOnWriteArrayList<>();
        page.onConsoleMessage(msg -> messages.add(msg.text()));

        page.navigate(BASE_URL + "/headroom-demo-duplicate");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertTrue(messages.stream().anyMatch(m -> m.contains("already has headroom behavior attached")),
                "expected duplicate-attach console.warn; got: " + messages);
    }

    @Test
    void unattachedAppLayout_navbarBottomPaddingNotTightened() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        String attachedPadding = (String) page.locator("vaadin-app-layout div[part~='navbar-bottom']")
                .evaluate("el => getComputedStyle(el).paddingTop");

        page.navigate(BASE_URL + "/headroom-demo-ungated-css");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        String unattachedPadding = (String) page.locator("vaadin-app-layout div[part~='navbar-bottom']")
                .evaluate("el => getComputedStyle(el).paddingTop");

        assertTrue(!attachedPadding.equals(unattachedPadding),
                "expected navbar-bottom padding to differ between an AppLayout with headroom "
                + "attached (" + attachedPadding + ") and one without (" + unattachedPadding
                + ") — the [headroom-enabled] gate on the padding-tightening rule isn't working");
    }

    @Test
    void injectedCssMediaRules_areGatedOnHeadroomEnabled() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> rules = (List<Map<String, String>>) page.evaluate(
            "() => { const out = []; " +
            "for (const sheet of document.adoptedStyleSheets) { " +
            "  for (const rule of sheet.cssRules) { " +
            "    if (rule instanceof CSSMediaRule) { " +
            "      for (const inner of rule.cssRules) { " +
            "        out.push({ media: rule.media.mediaText, selector: inner.selectorText || '' }); " +
            "      } " +
            "    } " +
            "  } " +
            "} " +
            "return out; }"
        );

        var standalone = rules.stream()
                .filter(r -> r.get("media").contains("standalone"))
                .findFirst();
        assertTrue(standalone.isPresent() && standalone.get().get("selector").contains("headroom-enabled"),
                "expected the display-mode:standalone navbar-bottom rule to be gated on "
                + "[headroom-enabled]; found: " + standalone);

        var landscapeTouch = rules.stream()
                .filter(r -> r.get("media").contains("landscape"))
                .findFirst();
        assertTrue(landscapeTouch.isPresent() && landscapeTouch.get().get("selector").contains("headroom-enabled"),
                "expected the landscape+touch navbar-bottom rule to be gated on "
                + "[headroom-enabled]; found: " + landscapeTouch);

        var touchOnly = rules.stream()
                .filter(r -> r.get("media").contains("pointer") && !r.get("media").contains("landscape"))
                .findFirst();
        assertTrue(touchOnly.isPresent() && touchOnly.get().get("selector").contains(":has(vaadin-app-layout[headroom-enabled])"),
                "expected the touch-only html height:auto rule to be gated via "
                + ":has(vaadin-app-layout[headroom-enabled]); found: " + touchOnly);
    }

    /** Sets scrollTop directly on the AppLayout's shadow-DOM content container.
     *  This fires a genuine native 'scroll' event, identical to one from real
     *  wheel/touch input, exercising the app's own scroll listener unmodified. */
    private void scrollTo(Locator contentEl, int y) {
        contentEl.evaluate("(el, y) => { el.scrollTop = y; }", y);
    }

    @Test
    void pinnedRailShapedBottomBar_isNeverTransformHidden() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // navbar-bottom has a `hidden` attribute by default (confirmed against the
        // real @vaadin/app-layout template) unless the touch-optimized navbar slot
        // has content; this demo doesn't populate it, so force it visible here -
        // otherwise it's a zero-size, non-rendered element and any check against
        // it would be meaningless regardless of the actual heuristic.
        page.evaluate(
            "() => { const el = document.querySelector('vaadin-app-layout')" +
            "  .shadowRoot.querySelector('#navbarBottom'); " +
            "  el.removeAttribute('hidden'); el.textContent = 'Simulated rail'; }"
        );

        // Simulate a companion "rail" purely via CSS, with zero reference to any
        // specific add-on - same shape/position AppNavLayout's rail actually uses
        // (fixed, ~80px wide, spanning most of the viewport height).
        page.addStyleTag(new Page.AddStyleTagOptions().setContent(
            "vaadin-app-layout::part(navbar-bottom) {" +
            "  position: fixed !important;" +
            "  top: 0; bottom: 0; left: 0;" +
            "  width: 80px;" +
            "  height: auto;" +
            "}"
        ));

        Locator layout = page.locator("vaadin-app-layout");
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator bottomPart = page.locator("vaadin-app-layout div[part~='navbar-bottom']");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        // Overall scroll state still tracks correctly...
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        // ...but the rail-shaped bar itself is never transform-hidden, since
        // looksLikeAPinnedRail() defers to it being fixed + taller than wide.
        String transform = (String) bottomPart.evaluate("el => getComputedStyle(el).transform");
        assertTrue(transform.equals("none"),
                "pinned rail-shaped navbar-bottom should NOT be transformed/hidden, was: " + transform);
    }
}
