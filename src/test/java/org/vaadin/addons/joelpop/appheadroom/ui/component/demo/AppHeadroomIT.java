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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Locator expandedState = page.locator("#" + HeadroomDemoView.EXPANDED_STATE_ID);
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");

        assertThat(expandedState).hasText("expanded");
        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertTrue(navbarTop.boundingBox().y >= 0,
                "navbar-top should be on-screen while expanded, was y=" + navbarTop.boundingBox().y);

        scrollTo(contentEl, 500); // past topOffset(100) + hideTolerance(40)

        assertThat(layout).hasAttribute("headroom-unpinned", "");
        assertThat(expandedState).hasText("collapsed"); // proves the full client -> @Synchronize -> listener -> DOM round trip
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y < 0,
                "navbar-top should be translated off-screen once collapsed, was y=" + navbarTop.boundingBox().y);
        pauseForHumanIfHeaded();
    }

    @Test
    void navbarTop_becomesInvisible_onlyAfterTheSlideOutAnimationFinishes_andVisibleAgainImmediatelyOnRestore() {
        // visibility is transitioned alongside transform, at a matching duration,
        // specifically so a bar's content that doesn't respond to transform at all
        // (e.g. an AppLayout extension's own popover/overlay-based UI escaping the
        // normal paint hierarchy) still genuinely disappears - visibility is
        // inherited, so it reaches such a descendant regardless of how it renders,
        // where transform alone would not. See the styles comment in
        // app-headroom.ts for why this needs no JS timing of its own: CSS already
        // defines a discrete property like visibility to flip only at the very end
        // of a transition toward hidden, and at the very start toward visible.
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");

        scrollTo(contentEl, 500); // past topOffset(100) + hideTolerance(40)

        // Mid-slide-out (well before the 600ms default transition completes) - still
        // visible, not yet hidden, so the slide-out itself stays visible throughout.
        page.waitForTimeout(200);
        assertEquals("visible", (String) navbarTop.evaluate("el => getComputedStyle(el).visibility"),
                "navbar-top should still be visible mid-slide-out, not hidden before the animation finishes");

        // Once fully settled off-screen, now hidden.
        page.waitForTimeout(TRANSITION_SETTLE_MS - 200);
        assertEquals("hidden", (String) navbarTop.evaluate("el => getComputedStyle(el).visibility"),
                "navbar-top should be hidden once the slide-out animation has fully finished");

        scrollTo(contentEl, 500 - 41); // > showTolerance(40) upward from the unpin point

        // Immediately on restore (well before the slide-in animation completes) -
        // already visible again, not waiting for the slide-in to finish, so the
        // slide-in itself is visible throughout rather than snapping into view at
        // the end.
        page.waitForTimeout(50);
        assertEquals("visible", (String) navbarTop.evaluate("el => getComputedStyle(el).visibility"),
                "navbar-top should already be visible again immediately on restore, not just once the slide-in finishes");
    }

    @Test
    void navbarTop_opacity_fadesSymmetrically_inBothDirections() {
        // opacity is what actually produces the visible fade (visibility itself has
        // nothing to interpolate) - transitioned at the same duration as transform
        // and visibility, in both directions, per explicit direction: since they
        // all start and finish together on the way out, they should on the way in
        // too, rather than opacity snapping instantly on one side only.
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");

        scrollTo(contentEl, 500); // past topOffset(100) + hideTolerance(40)

        // Mid-fade-out: strictly between fully shown and fully hidden.
        page.waitForTimeout(200);
        double midHideOpacity = Double.parseDouble(
                (String) navbarTop.evaluate("el => getComputedStyle(el).opacity"));
        assertTrue(midHideOpacity > 0 && midHideOpacity < 1,
                "navbar-top's opacity should be strictly between 0 and 1 mid-fade-out, was " + midHideOpacity);

        page.waitForTimeout(TRANSITION_SETTLE_MS - 200);
        assertEquals("0", (String) navbarTop.evaluate("el => getComputedStyle(el).opacity"),
                "navbar-top should be fully faded out once the transition has settled");

        scrollTo(contentEl, 500 - 41); // > showTolerance(40) upward from the unpin point

        // Mid-fade-in: strictly between fully hidden and fully shown - if opacity
        // instead snapped to 1 immediately (asymmetric with the fade-out), this
        // would read exactly "1" here instead.
        page.waitForTimeout(200);
        double midShowOpacity = Double.parseDouble(
                (String) navbarTop.evaluate("el => getComputedStyle(el).opacity"));
        assertTrue(midShowOpacity > 0 && midShowOpacity < 1,
                "navbar-top's opacity should be strictly between 0 and 1 mid-fade-in, was " + midShowOpacity);

        page.waitForTimeout(TRANSITION_SETTLE_MS - 200);
        assertEquals("1", (String) navbarTop.evaluate("el => getComputedStyle(el).opacity"),
                "navbar-top should be fully faded in once the transition has settled");
    }

    @Test
    void scrollingBackUpPastShowTolerance_repinsLayout_andNotifiesServer() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE); // let the deferred rAF scroll-listener setup settle first

        Locator layout = page.locator("vaadin-app-layout");
        Locator expandedState = page.locator("#" + HeadroomDemoView.EXPANDED_STATE_ID);
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");

        scrollTo(contentEl, 500);
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y < 0,
                "navbar-top should be translated off-screen once collapsed, was y=" + navbarTop.boundingBox().y);
        pauseForHumanIfHeaded();

        scrollTo(contentEl, 500 - 41); // > showTolerance(40) upward from the unpin point

        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertThat(expandedState).hasText("expanded");
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y >= 0,
                "navbar-top should be back on-screen once re-expanded, was y=" + navbarTop.boundingBox().y);
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
        Locator expandedState = page.locator("#" + DetachResetDemoView.EXPANDED_STATE_ID);
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        // dispatchEvent (not click()) bypasses Playwright's scroll-into-view actionability
        // check, which would otherwise reset our deliberately-collapsed scroll position.
        Locator removeButton = page.locator("#" + DetachResetDemoView.REMOVE_BUTTON_ID);

        scrollTo(contentEl, 500);
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        assertThat(expandedState).hasText("collapsed");

        removeButton.dispatchEvent("click");

        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertThat(layout).not().hasAttribute("headroom-enabled", "");
        assertThat(expandedState).hasText("expanded"); // proves the reset reaches the server, not just the client attribute
    }

    @Test
    void explicitlyNonCollapsibleBottomBar_isNeverTransformHidden() {
        page.navigate(BASE_URL + "/headroom-demo-explicit-pin");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator layout = page.locator("vaadin-app-layout");
        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator bottomPart = page.locator("vaadin-app-layout div[part~='navbar-bottom']");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        // Overall scroll state still tracks correctly...
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        // ...but the explicitly-non-collapsible bar is never transform-hidden, even
        // though it's an ordinary bar (not fixed, not rail-shaped) that
        // looksLikeAPinnedRail() alone would NOT have exempted.
        String transform = (String) bottomPart.evaluate("el => getComputedStyle(el).transform");
        assertTrue(transform.equals("none"),
                "explicitly-non-collapsible navbar-bottom should NOT be transformed/hidden, was: " + transform);
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
    void headroomElement_isPeerOfUiRoot_notLightDomChildOfLayout() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Boolean isChildOfLayout = (Boolean) page.evaluate(
                "() => Array.from(document.querySelector('vaadin-app-layout').children)" +
                "  .some(el => el.tagName === 'APP-HEADROOM')");
        assertEquals(false, isChildOfLayout,
                "app-headroom must never be a light-DOM child of the vaadin-app-layout it affects");

        Boolean existsElsewhereInDocument = (Boolean) page.evaluate(
                "() => document.querySelector('app-headroom') !== null");
        assertTrue(existsElsewhereInDocument,
                "app-headroom should still be attached somewhere in the document (as a peer)");
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
    void reEvaluatingGlobalStylesModule_doesNotDuplicateStylesheet() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // First, confirm the *real* shipped module actually sets this exact marker
        // on real evaluation — ties this test to app-headroom.ts's actual behavior,
        // not just to a same-named constant duplicated in this test's own JS below.
        Object markerSetByRealModule = page.evaluate("() => document.__appHeadroomGlobalStylesInstalled === true");
        assertEquals(true, markerSetByRealModule,
                "expected app-headroom.ts's real module evaluation to set "
                + "document.__appHeadroomGlobalStylesInstalled = true; it did not");

        Object countBefore = page.evaluate("() => document.adoptedStyleSheets.length");

        // Reproduces the exact guard app-headroom.ts uses (same marker property
        // name on `document`), simulating what a second evaluation of that
        // module's top-level code would do (Vite HMR, or a second bundle).
        page.evaluate(
            "() => { " +
            "  const marker = '__appHeadroomGlobalStylesInstalled'; " +
            "  if (!document[marker]) { " +
            "    const sheet = new CSSStyleSheet(); " +
            "    sheet.replaceSync('/* simulated re-evaluation */'); " +
            "    document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet]; " +
            "    document[marker] = true; " +
            "  } " +
            "}"
        );

        Object countAfter = page.evaluate("() => document.adoptedStyleSheets.length");

        assertEquals(countBefore, countAfter,
                "expected document.adoptedStyleSheets count to stay the same when the guarded "
                + "install logic runs a second time, but it changed from " + countBefore
                + " to " + countAfter);
    }

    @Test
    void observedAttributes_matchJavaConstants() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        @SuppressWarnings("unchecked")
        List<String> observed = (List<String>) page.evaluate(
            "() => Array.from(customElements.get('app-headroom').observedAttributes)"
        );

        // Compares against AppHeadroom.java's own public constants, not a value
        // hardcoded a third time here — if either side renames an attribute
        // without updating the other, this fails with a clear message instead
        // of desyncing silently.
        Set<String> expected = Set.of(
                AppHeadroom.ATTR_TOP_OFFSET,
                AppHeadroom.ATTR_HIDE_TOLERANCE,
                AppHeadroom.ATTR_SHOW_TOLERANCE,
                AppHeadroom.ATTR_TOP_BAR_PINNED,
                AppHeadroom.ATTR_BOTTOM_BAR_PINNED,
                AppHeadroom.ATTR_TRANSITION_DURATION,
                AppHeadroom.ATTR_CONDENSED_TOP_SHAPE,
                AppHeadroom.ATTR_CONDENSED_BOTTOM_SHAPE
        );

        assertEquals(expected, new HashSet<>(observed),
                "app-headroom's browser-registered observedAttributes should exactly match "
                + "AppHeadroom.java's public ATTR_* constants; found: " + observed);
    }

    @Test
    void injectedCssMediaRules_areGatedOnHeadroomEnabledAndActive() {
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

        // These two rules only matter while scroll-tracking is actually running, so
        // (unlike the transition/hide-transform rules) they're gated on [headroom-active]
        // too, not just [headroom-enabled] - see the reported clipping bug this fixes:
        // an inactive AppHeadroom must never force position:fixed on the bottom bar.
        var landscapeTouch = rules.stream()
                .filter(r -> r.get("media").contains("landscape"))
                .findFirst();
        assertTrue(landscapeTouch.isPresent()
                        && landscapeTouch.get().get("selector").contains("headroom-enabled")
                        && landscapeTouch.get().get("selector").contains("headroom-active"),
                "expected the landscape+touch navbar-bottom rule to be gated on both "
                + "[headroom-enabled] and [headroom-active]; found: " + landscapeTouch);

        var touchOnly = rules.stream()
                .filter(r -> r.get("media").contains("pointer") && !r.get("media").contains("landscape"))
                .findFirst();
        assertTrue(touchOnly.isPresent()
                        && touchOnly.get().get("selector").contains(":has(vaadin-app-layout[headroom-enabled][headroom-active])"),
                "expected the touch-only html height:auto rule to be gated via "
                + ":has(vaadin-app-layout[headroom-enabled][headroom-active]); found: " + touchOnly);
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
        // specific AppLayout subclass - just the general shape/position a
        // persistent side rail would actually use (fixed, ~80px wide, spanning
        // most of the viewport height).
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

    @Test
    void activationPredicate_allowsEffect_onEmulatedPhone() {
        // iPhone-13-ish: touch, screen shorter side (390) well below the 768 tablet threshold.
        try (BrowserContext phoneContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(390, 844).setViewportSize(390, 844))) {
            Page phonePage = phoneContext.newPage();
            phonePage.navigate(BASE_URL + "/headroom-demo-activation-predicate");
            phonePage.waitForLoadState(LoadState.NETWORKIDLE);

            Locator layout = phonePage.locator("vaadin-app-layout");
            phonePage.evaluate("() => window.scrollTo(0, 500)"); // touch mode: body-scrolling, not the content div
            phonePage.waitForTimeout(TRANSITION_SETTLE_MS);

            assertThat(layout).hasAttribute("headroom-unpinned", "");
        }
    }

    @Test
    void activationPredicate_suppressesEffect_onEmulatedPortraitTablet() {
        // iPad-ish: touch, screen shorter side (768) at the tablet threshold, held portrait
        // (width < height) - the predicate only allows TABLET in landscape, so this should
        // never hide regardless of how far past hideTolerance the user scrolls.
        try (BrowserContext tabletContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(768, 1024).setViewportSize(768, 1024))) {
            Page tabletPage = tabletContext.newPage();
            tabletPage.navigate(BASE_URL + "/headroom-demo-activation-predicate");
            tabletPage.waitForLoadState(LoadState.NETWORKIDLE);

            Locator layout = tabletPage.locator("vaadin-app-layout");
            tabletPage.evaluate("() => window.scrollTo(0, 500)"); // touch mode: body-scrolling, not the content div
            tabletPage.waitForTimeout(TRANSITION_SETTLE_MS);

            assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        }
    }

    @Test
    void activationPredicate_reevaluatesLive_onOrientationChange() {
        try (BrowserContext tabletContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(768, 1024).setViewportSize(768, 1024))) {
            Page tabletPage = tabletContext.newPage();
            tabletPage.navigate(BASE_URL + "/headroom-demo-activation-predicate");
            tabletPage.waitForLoadState(LoadState.NETWORKIDLE);

            Locator layout = tabletPage.locator("vaadin-app-layout");

            // Portrait tablet: inactive per the predicate - scrolling past hideTolerance
            // must not hide the chrome.
            tabletPage.evaluate("() => window.scrollTo(0, 500)");
            tabletPage.waitForTimeout(TRANSITION_SETTLE_MS);
            assertThat(layout).not().hasAttribute("headroom-unpinned", "");

            // Back to 0 *before* rotating: _startTracking() captures its initial pinY
            // baseline from the current scroll position the moment tracking (re)starts,
            // so rotating while still sitting at y=500 would seed pinY at ~500 instead
            // of ~0 - then scrolling back down to 500 would read as "no net movement
            // from the baseline" rather than a genuine scroll past hideTolerance.
            tabletPage.evaluate("() => window.scrollTo(0, 0)");

            // Rotate to landscape (swap width/height) - windowSizeSignal() picks this up
            // reactively and re-evaluates the predicate without any further wiring.
            tabletPage.setViewportSize(1024, 768);
            tabletPage.waitForTimeout(TRANSITION_SETTLE_MS);

            tabletPage.evaluate("() => window.scrollTo(0, 500)");
            tabletPage.waitForTimeout(TRANSITION_SETTLE_MS);

            assertThat(layout).hasAttribute("headroom-unpinned", "");
        }
    }

    @Test
    void customTabletThreshold_reclassifiesA768ScreenAsPhone() {
        // Same 768-short-side screen that activationPredicate_suppressesEffect_onEmulatedPortraitTablet
        // classifies as TABLET (inactive) under the *default* 768 threshold. This demo raises
        // setTabletMinShortSidePx to 1000, and restricts activation to PHONE only - so the very
        // same screen becoming active here proves the custom threshold actually took effect.
        try (BrowserContext tabletContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(768, 1024).setViewportSize(768, 1024))) {
            Page tabletPage = tabletContext.newPage();
            tabletPage.navigate(BASE_URL + "/headroom-demo-custom-thresholds");
            tabletPage.waitForLoadState(LoadState.NETWORKIDLE);

            Locator layout = tabletPage.locator("vaadin-app-layout");
            tabletPage.evaluate("() => window.scrollTo(0, 500)");
            tabletPage.waitForTimeout(TRANSITION_SETTLE_MS);

            assertThat(layout).hasAttribute("headroom-unpinned", "");
        }
    }

    @Test
    void customTransitionDuration_appliesToComputedStyle() {
        // Set purely by _attachToTarget() at bind time, independent of whether tracking is
        // active - so this doesn't need any device/touch emulation, unlike the test above.
        page.navigate(BASE_URL + "/headroom-demo-custom-thresholds");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");
        String transitionDuration = (String) navbarTop.evaluate("el => getComputedStyle(el).transitionDuration");
        assertTrue(transitionDuration.contains("0.15"),
                "expected the custom 150ms transition duration, was: " + transitionDuration);
    }

    @Test
    void landscapeBottomBarZIndex_isOverridableViaCssCustomProperty() {
        // Nothing in this library ever sets --headroom-landscape-bottom-bar-z-index itself
        // (per design - see app-headroom.ts) - this proves the var(...) plumbing an app would
        // rely on to override it actually works.
        try (BrowserContext tabletContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(1024, 768).setViewportSize(1024, 768))) {
            Page tabletPage = tabletContext.newPage();
            tabletPage.navigate(BASE_URL + "/headroom-demo");
            tabletPage.waitForLoadState(LoadState.NETWORKIDLE);

            // navbar-bottom is `hidden` by default unless the touch-optimized navbar slot
            // has content; this demo doesn't populate it, so force it visible here - same
            // workaround pinnedRailShapedBottomBar_isNeverTransformHidden already needs.
            tabletPage.evaluate(
                "() => { const el = document.querySelector('vaadin-app-layout')" +
                "  .shadowRoot.querySelector('#navbarBottom'); " +
                "  el.removeAttribute('hidden'); el.textContent = 'Bottom bar'; }"
            );
            tabletPage.evaluate(
                "() => document.querySelector('vaadin-app-layout')" +
                "  .style.setProperty('--headroom-landscape-bottom-bar-z-index', '999')"
            );

            Locator bottomPart = tabletPage.locator("vaadin-app-layout div[part~='navbar-bottom']");
            String zIndex = (String) bottomPart.evaluate("el => getComputedStyle(el).zIndex");
            assertEquals("999", zIndex);
        }
    }

    @Test
    void inactiveHeadroom_doesNotEngageBodyScrollingMode() {
        // Regression test for a reported bug: on a touch device, an inactive AppHeadroom
        // (e.g. TabletOnlyDemoView's predicate, which excludes PHONE entirely) must not
        // switch <html> into body-scrolling mode - that's only needed while scroll-tracking
        // is actually running (see the [headroom-active] gate on that CSS rule). Before this
        // fix, [headroom-enabled] alone triggered it regardless of activation, which - among
        // other now-removed unconditional rules - produced the reported clipped rendering.
        //
        // (Note: the bottom bar's own position:fixed in landscape+touch is Vaadin's own
        // built-in behavior for this viewport shape, confirmed by directly disabling
        // AppHeadroom's own landscape-fixed rule and observing no change - so that
        // property isn't a usable signal here; <html>'s height is.)
        try (BrowserContext phoneContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(844, 390).setViewportSize(844, 390))) {
            Page phonePage = phoneContext.newPage();
            phonePage.navigate(BASE_URL + "/headroom-demo-tablet-only");
            phonePage.waitForLoadState(LoadState.NETWORKIDLE);
            phonePage.waitForTimeout(TRANSITION_SETTLE_MS);

            int htmlHeight = parsePx(phonePage.evaluate("() => getComputedStyle(document.documentElement).height"));
            int viewportHeight = ((Number) phonePage.evaluate("() => window.innerHeight")).intValue();
            assertTrue(htmlHeight <= viewportHeight + 10,
                    "inactive AppHeadroom must not switch <html> into body-scrolling mode - "
                    + "expected height close to the viewport (" + viewportHeight + "px), was " + htmlHeight + "px");
        }
    }

    @Test
    void activeHeadroom_stillEngagesBodyScrollingModeInTouch() {
        // A/B control for the test above, on the identical emulated context: confirms the
        // fix didn't just disable body-scrolling mode outright - it should still switch on
        // while AppHeadroom is genuinely active (here, HeadroomDemoView's default, unrestricted
        // predicate), where <html>'s height should grow to fit the page's full content height.
        try (BrowserContext phoneContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(844, 390).setViewportSize(844, 390))) {
            Page phonePage = phoneContext.newPage();
            phonePage.navigate(BASE_URL + "/headroom-demo");
            phonePage.waitForLoadState(LoadState.NETWORKIDLE);
            phonePage.waitForTimeout(TRANSITION_SETTLE_MS);

            int htmlHeight = parsePx(phonePage.evaluate("() => getComputedStyle(document.documentElement).height"));
            int viewportHeight = ((Number) phonePage.evaluate("() => window.innerHeight")).intValue();
            assertTrue(htmlHeight > viewportHeight * 2,
                    "active AppHeadroom should still switch <html> into body-scrolling mode "
                    + "(height should grow to fit content, viewport was " + viewportHeight + "px), was " + htmlHeight + "px");
        }
    }

    private static int parsePx(Object cssPxValue) {
        return (int) Double.parseDouble(((String) cssPxValue).replace("px", ""));
    }

    @Test
    void condensedTopRenderer_isAbsentFromDom_whenNoRendererConfigured() {
        page.navigate(BASE_URL + "/headroom-demo");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        assertEquals(0, page.locator("[slot='condensed-top']").count());
        // The wrapper <div> itself isn't rendered at all when no shape is configured -
        // not just empty/zero-size - since render() conditionally includes it based on
        // condensedTopShape/condensedBottomShape, which Java never sets in this scenario.
        assertEquals(0, page.locator("app-headroom").locator(".condensed-top-wrapper").count());
        assertEquals(0, page.locator("app-headroom").locator(".condensed-bottom-wrapper").count());
    }

    @Test
    void condensedTopRenderer_isPresentButHidden_whileRealBarIsShown() {
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator condensedTop = page.locator("#" + CondensedViewDemoView.CONDENSED_TOP_ID);
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");
        assertEquals(1, condensedTop.count());
        // Opacity/transition live on the AppHeadroom-owned wrapper, not the app-supplied
        // Component itself - opacity isn't an inherited CSS property, so checking the
        // Component's own computed style here would always read "1" regardless of the
        // wrapper's actual state.
        assertEquals("0", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));
    }

    @Test
    void condensedTopRenderer_becomesVisible_whenRealTopBarHidesOnScroll() {
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");
        Locator condensedTop = page.locator("#" + CondensedViewDemoView.CONDENSED_TOP_ID);
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");

        scrollTo(contentEl, 500); // past topOffset(100) + hideTolerance(40)
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertEquals("1", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));
        assertTrue(navbarTop.boundingBox().y < 0,
                "real navbar-top should still be transform-hidden off-screen, was y=" + navbarTop.boundingBox().y);
        // Fade-only design: the condensed view never slides, it stays at its resting
        // position the whole time - only opacity changes. Resting position is now the
        // reconciled default gap (8px, see the wrapper's inset-block-start), not flush
        // 0 - checking the Component's own position (not just the wrapper's) confirms
        // the wrapper's default centering actually placed it there too.
        assertTrue(Math.abs(condensedTop.boundingBox().y - 8) < 1,
                "condensed top view should stay at its resting position (y≈8), was y=" + condensedTop.boundingBox().y);
    }

    @Test
    void condensedBottomRenderer_becomesVisible_whenRealBottomBarHidesOnScroll() {
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarBottom = page.locator("vaadin-app-layout div[part~='navbar-bottom']");
        Locator wrapper = page.locator("app-headroom").locator(".condensed-bottom-wrapper");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertEquals("1", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));
        String transform = (String) navbarBottom.evaluate("el => getComputedStyle(el).transform");
        assertTrue(!transform.equals("none"),
                "real navbar-bottom should be transform-hidden, was: " + transform);
    }

    @Test
    void condensedTopRenderer_hidesAgain_whenScrollingBackUpPastShowTolerance() {
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertEquals("1", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));

        scrollTo(contentEl, 500 - 45); // > showTolerance(40) upward from the unpin point
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertEquals("0", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));
    }

    @Test
    void condensedView_transitionDuration_matchesConfiguredTransitionDuration() {
        // Built regardless of whether tracking is active - see CustomThresholdsDemoView's
        // own Javadoc - so no device emulation is needed here, same as
        // customTransitionDuration_appliesToComputedStyle above.
        page.navigate(BASE_URL + "/headroom-demo-custom-thresholds");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // transition lives on the wrapper, not the app-supplied Component - see
        // condensedTopRenderer_isPresentButHidden_whileRealBarIsShown's comment.
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");
        String transitionDuration = (String) wrapper.evaluate("el => getComputedStyle(el).transitionDuration");
        assertTrue(transitionDuration.contains("0.15"),
                "expected the custom 150ms transition duration on the condensed view, was: " + transitionDuration);
    }

    @Test
    void condensedBottomRenderer_stillAppears_overPinnedRailShapedBottomBar() {
        // Reversed from this test's earlier assertion, by design: a condensed view is a
        // peer element positioned relative to the viewport, with no structural
        // relationship to whatever made the real bar rail-shaped, so the automatic
        // pinned-rail geometry check that correctly keeps the real bar from ever hiding
        // has no business also suppressing this - see the file header comment in
        // app-headroom.ts. Only an explicit setBottomBarCollapsible(false) still
        // suppresses it - see
        // condensedBottomRenderer_neverAppears_whenBottomBarNotCollapsible below.
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Simulate a companion "rail" purely via CSS, same technique as
        // pinnedRailShapedBottomBar_isNeverTransformHidden above.
        page.addStyleTag(new Page.AddStyleTagOptions().setContent(
            "vaadin-app-layout::part(navbar-bottom) {" +
            "  position: fixed !important;" +
            "  top: 0; bottom: 0; left: 0;" +
            "  width: 80px;" +
            "  height: auto;" +
            "}"
        ));

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator layout = page.locator("vaadin-app-layout");
        Locator headroomHost = page.locator("app-headroom");
        Locator wrapper = headroomHost.locator(".condensed-bottom-wrapper");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        // Overall scroll state still tracks correctly, and the real (rail-shaped) bar
        // never gets transform-hidden (unchanged, established by
        // pinnedRailShapedBottomBar_isNeverTransformHidden)...
        assertThat(layout).hasAttribute("headroom-unpinned", "");
        // ...but the condensed view fades in anyway - the host's own headroom-hide-bottom
        // is no longer gated by looksLikeAPinnedRail(), only by the explicit pin.
        assertThat(headroomHost).hasAttribute("headroom-hide-bottom", "");
        assertEquals("1", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));
    }

    @Test
    void condensedBottomRenderer_neverAppears_whenBottomBarNotCollapsible() {
        page.navigate(BASE_URL + "/headroom-demo-explicit-pin");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator headroomHost = page.locator("app-headroom");
        Locator wrapper = headroomHost.locator(".condensed-bottom-wrapper");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertThat(headroomHost).not().hasAttribute("headroom-hide-bottom", "");
        assertEquals("0", (String) wrapper.evaluate("el => getComputedStyle(el).opacity"));
    }

    @Test
    void removingHeadroomFromLayout_alsoRemovesCondensedComponentsFromDom() {
        page.navigate(BASE_URL + "/headroom-demo-detach");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator removeButton = page.locator("#" + DetachResetDemoView.REMOVE_BUTTON_ID);
        Locator condensedTop = page.locator("#" + DetachResetDemoView.CONDENSED_TOP_ID);

        assertThat(condensedTop).hasCount(1);

        removeButton.dispatchEvent("click");

        // hasCount (unlike a plain count() call) auto-retries until the assertion holds
        // or times out - needed here since the removal reaches the client only after the
        // click's server round trip completes, not synchronously with the click itself.
        assertThat(condensedTop).hasCount(0);
    }

    @Test
    void asFloating_setComponent_calledAgainAfterAttach_replacesPreviouslyAttachedComponent() {
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator condensedTop = page.locator("#" + CondensedViewDemoView.CONDENSED_TOP_ID);
        Locator replacedCondensedTop = page.locator("#" + CondensedViewDemoView.REPLACED_CONDENSED_TOP_ID);
        Locator replaceButton = page.locator("#" + CondensedViewDemoView.REPLACE_CONDENSED_TOP_BUTTON_ID);

        assertThat(condensedTop).hasCount(1);
        assertThat(replacedCondensedTop).hasCount(0);

        replaceButton.click();

        // hasCount auto-retries until the click's server round trip completes and the
        // client applies the swap - see removingHeadroomFromLayout_alsoRemovesCondensedComponentsFromDom
        // above for the same reasoning.
        assertThat(condensedTop).hasCount(0);
        assertThat(replacedCondensedTop).hasCount(1);
    }

    @Test
    void asFloating_wrapperInset_reconcilesDefaultGapAgainstSafeArea_viaMax() {
        // env(safe-area-inset-*) always resolves to 0 in this environment (established
        // earlier this session - the real bottom bar's own safe-area rule has no numeric
        // IT test for the same reason), so max(8px, 0px) resolves to the plain default
        // gap here - not a genuinely non-zero safe-area override - the same accepted
        // limitation as that rule, not a new gap introduced here. What this does confirm:
        // the reconciliation is max(), not addition - if it were additive, an app-supplied
        // Component with its own padding would show a doubled gap, which floating mode's
        // whole design exists to avoid (see AppHeadroom.java's asFloating() Javadoc).
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");

        assertEquals("8px", (String) wrapper.evaluate("el => getComputedStyle(el).insetInlineStart"));
        assertEquals("8px", (String) wrapper.evaluate("el => getComputedStyle(el).insetInlineEnd"));
        assertEquals("8px", (String) wrapper.evaluate("el => getComputedStyle(el).insetBlockStart"));
    }

    @Test
    void asRibbon_wrapperStaysFlush_regardlessOfSafeArea() {
        // The piece that was broken before this shape-aware redesign: a full-width
        // ribbon's background needs to reach the true edge, which an inset on the
        // wrapper (as floating mode correctly uses) would prevent.
        page.navigate(BASE_URL + "/headroom-demo-ribbon");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator topWrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");
        Locator bottomWrapper = page.locator("app-headroom").locator(".condensed-bottom-wrapper");

        assertEquals("0px", (String) topWrapper.evaluate("el => getComputedStyle(el).insetInlineStart"));
        assertEquals("0px", (String) topWrapper.evaluate("el => getComputedStyle(el).insetBlockStart"));
        assertEquals("0px", (String) bottomWrapper.evaluate("el => getComputedStyle(el).insetBlockEnd"));
    }

    @Test
    void asRibbon_frame_fillsFullWidth_withDefaultBackground_andSafeAreaPadding() {
        page.navigate(BASE_URL + "/headroom-demo-ribbon");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // The frame (AppHeadroom-owned, see AppHeadroom.java's RibbonCondensedBar) is the
        // direct slot content, one level up from the app's own rendered Component.
        Locator frame = page.locator("[slot='condensed-top']");
        assertEquals("100%", (String) frame.evaluate("el => el.style.width"));
        assertEquals("var(--vaadin-background-container)",
                (String) frame.evaluate("el => el.style.background"));
        // env(safe-area-inset-*) resolves to 0 in this environment (same accepted
        // limitation noted elsewhere) - confirms the property is present and on the
        // correct side (top for the top bar), not the numeric safe-area value itself.
        assertEquals("0px", (String) frame.evaluate("el => getComputedStyle(el).paddingTop"));
        assertEquals("0px", (String) frame.evaluate("el => getComputedStyle(el).paddingBottom"));
    }

    @Test
    void condensedTop_click_returnsToShownState_withoutMovingScrollPosition() {
        // Click-to-expand: a condensed view is a stand-in for the real bar, so clicking
        // it always brings the real chrome back - see app-headroom.ts's _expand(). The
        // scroll position itself is deliberately left alone, matching how scrolling
        // back up doesn't force a jump either - only the chrome's pinned state changes.
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator layout = page.locator("vaadin-app-layout");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");
        Locator condensedTop = page.locator("#" + CondensedViewDemoView.CONDENSED_TOP_ID);

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertThat(layout).hasAttribute("headroom-unpinned", "");

        condensedTop.click();
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
        assertTrue(navbarTop.boundingBox().y >= 0,
                "navbar-top should be back on-screen after clicking the condensed view, was y=" + navbarTop.boundingBox().y);
        assertEquals(500, ((Number) contentEl.evaluate("el => el.scrollTop")).intValue(),
                "clicking the condensed view should not itself move the scroll position");
        pauseForHumanIfHeaded();
    }

    @Test
    void condensedBottom_click_alsoRestoresTopBar() {
        // Only one pinned state exists for the whole layout (not one per bar), so
        // clicking either condensed view brings back both bars together - see
        // _expand()'s own comment on why it doesn't try to distinguish which one was
        // clicked.
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator navbarTop = page.locator("vaadin-app-layout div[part~='navbar-top']");
        Locator condensedBottom = page.locator("#" + CondensedViewDemoView.CONDENSED_BOTTOM_ID);

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertTrue(navbarTop.boundingBox().y < 0, "navbar-top should be hidden before the click");

        condensedBottom.click();
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertTrue(navbarTop.boundingBox().y >= 0,
                "navbar-top should be restored too, having clicked only the condensed bottom view");
    }

    @Test
    void condensedWrapper_isNotTextSelectable() {
        // app-headroom.ts also declares -webkit-user-select: none and
        // -webkit-touch-callout: none - a real device (iOS Safari) showed plain
        // user-select: none alone still leaves long-press text-selection, and its
        // accompanying "Copy / Look Up / Translate" callout, active. Neither is
        // independently checkable here, so this test only covers the unprefixed
        // property: confirmed via a Chromium probe (this codebase's only browser
        // engine) that -webkit-user-select is a pure alias of user-select there,
        // reporting whatever user-select already resolves to regardless of whether
        // it's separately declared - so asserting it here wouldn't distinguish a
        // regression from a pass, and -webkit-touch-callout is a WebKit-exclusive
        // property Chromium doesn't recognize at all (getComputedStyle reports an
        // empty string for it). Both are an accepted, unverifiable-here limitation,
        // the same as env(safe-area-inset-*) elsewhere - the fix itself is real and
        // needed on the actual target platform, just not provable through this
        // Chromium-only IT harness.
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");
        assertEquals("none", (String) wrapper.evaluate("el => getComputedStyle(el).userSelect"));
    }

    @Test
    void condensedWrapper_cursor_isPointerOnAFinePointerDevice_butNotOnATouchOnlyDevice() {
        // Default context here has no touch capability, so Chromium reports a fine
        // primary pointer - the same emulation-realism this codebase already relies on
        // for @media (pointer: coarse) (see activationPredicate_allowsEffect_onEmulatedPhone).
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");
        assertEquals("pointer", (String) wrapper.evaluate("el => getComputedStyle(el).cursor"));

        try (BrowserContext touchContext = browser.newContext(new Browser.NewContextOptions()
                .setHasTouch(true).setIsMobile(true)
                .setScreenSize(390, 844).setViewportSize(390, 844))) {
            Page touchPage = touchContext.newPage();
            touchPage.navigate(BASE_URL + "/headroom-demo-condensed");
            touchPage.waitForLoadState(LoadState.NETWORKIDLE);
            Locator touchWrapper = touchPage.locator("app-headroom").locator(".condensed-top-wrapper");
            assertTrue(!"pointer".equals(touchWrapper.evaluate("el => getComputedStyle(el).cursor")),
                    "a touch-only device has no cursor to change - the pointer cursor shouldn't apply there");
        }
    }

    @Test
    void asFloating_wrapper_shrinksToTheAppComponent_notTheWholeRow() {
        // If the wrapper stayed stretched full-width the way the ribbon shape's does
        // (see asRibbon_wrapper_staysFullWidth_soClickingAnywhereOnTheBarExpands below),
        // clicking/hovering anywhere in that invisible row - including well outside the
        // app's own compact Component - would incorrectly trigger _expand() and show a
        // pointer cursor. See the width: fit-content + margin-inline: auto comment on
        // the floating-specific rules in app-headroom.ts.
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");
        Locator condensedTop = page.locator("#" + CondensedViewDemoView.CONDENSED_TOP_ID);

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        var wrapperBox = wrapper.boundingBox();
        var contentBox = condensedTop.boundingBox();
        assertTrue(Math.abs(wrapperBox.width - contentBox.width) < 2,
                "wrapper should shrink to the app's own Component width, was wrapper=" + wrapperBox.width
                        + " content=" + contentBox.width);
    }

    @Test
    void asFloating_clickingBesideTheAppComponent_doesNotTriggerExpand() {
        page.navigate(BASE_URL + "/headroom-demo-condensed");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator layout = page.locator("vaadin-app-layout");
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertThat(layout).hasAttribute("headroom-unpinned", "");

        // A point near the viewport's right edge - nowhere near the centered "Condensed
        // top" text regardless of how it's aligned, but squarely inside the old,
        // full-width stretched row (spanning edge to edge minus the safe-area gaps).
        // Clicking here must NOT trigger _expand(), unlike clicking the component
        // itself (see condensedTop_click_returnsToShownState_withoutMovingScrollPosition
        // above).
        var wrapperBox = wrapper.boundingBox();
        var viewport = page.viewportSize();
        double farRightX = viewport.width - 20;
        double y = wrapperBox.y + wrapperBox.height / 2;
        page.mouse().click(farRightX, y);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertThat(layout).hasAttribute("headroom-unpinned", "");
    }

    @Test
    void asRibbon_wrapper_staysFullWidth_soClickingAnywhereOnTheBarExpands() {
        // The opposite of the floating shape, deliberately: the ribbon frame IS the
        // whole visible bar, so click-anywhere-on-the-bar is correct - the same
        // expectation a real toolbar/nav bar already sets. Guards against a future
        // change accidentally shrinking this shape's wrapper the same way the floating
        // shape's was just narrowed.
        page.navigate(BASE_URL + "/headroom-demo-ribbon");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator contentEl = page.locator("vaadin-app-layout div[content]");
        Locator layout = page.locator("vaadin-app-layout");
        Locator wrapper = page.locator("app-headroom").locator(".condensed-top-wrapper");

        scrollTo(contentEl, 500);
        page.waitForTimeout(TRANSITION_SETTLE_MS);
        assertThat(layout).hasAttribute("headroom-unpinned", "");

        // Near the bar's left edge, well away from the centered "Condensed top" text -
        // still inside the frame/wrapper since it spans edge-to-edge in this shape.
        var wrapperBox = wrapper.boundingBox();
        page.mouse().click(wrapperBox.x + 5, wrapperBox.y + wrapperBox.height / 2);
        page.waitForTimeout(TRANSITION_SETTLE_MS);

        assertThat(layout).not().hasAttribute("headroom-unpinned", "");
    }
}
