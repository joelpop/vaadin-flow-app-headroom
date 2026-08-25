package org.vaadin.addons.joelpop.appheadroom.ui.component;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppHeadroomTest {

    @Tag("wrong-app-layout-tag")
    private static class WrongTagAppLayout extends AppLayout {}

    @Test
    void applyTo_neverAppendsHeadroomAsChildOfLayout() {
        var layout = new AppLayout();
        var headroom = AppHeadroom.applyTo(layout);

        assertNotNull(headroom);
        // Peer-attachment: applyTo() never makes headroom a light-DOM child of the
        // layout it affects (it attaches under the UI root instead, once the layout
        // itself is attached to a UI — see AppHeadroomIT for that live-attach half).
        assertEquals(0, layout.getElement().getChildCount());
    }

    @Test
    void applyTo_doesNotDisturbLayoutsExistingChildren() {
        var layout = new AppLayout();
        var existingNavItem = new Div();
        layout.addToNavbar(existingNavItem);

        AppHeadroom.applyTo(layout);

        assertEquals(1, layout.getElement().getChildCount());
        assertEquals(existingNavItem.getElement(), layout.getElement().getChild(0));
    }

    @Test
    void applyTo_returnsDetachedInstance_whenLayoutNeverAttachedToUI() {
        var layout = new AppLayout();
        var headroom = AppHeadroom.applyTo(layout);

        assertTrue(headroom.getParent().isEmpty());
        assertNull(headroom.getElement().getParent());
    }

    @Test
    void applyTo_throwsIllegalArgumentException_whenLayoutTagIsNotVaadinAppLayout() {
        var wrongTagLayout = new WrongTagAppLayout();

        var ex = assertThrows(IllegalArgumentException.class,
                () -> AppHeadroom.applyTo(wrongTagLayout));

        assertTrue(ex.getMessage().contains("vaadin-app-layout"));
        assertTrue(ex.getMessage().contains("wrong-app-layout-tag"));
    }

    @Test
    void setTopOffset_setsAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setTopOffset(150));
        assertEquals("150", headroom.getElement().getAttribute("top-offset"));
    }

    @Test
    void setHideTolerance_setsAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setHideTolerance(40));
        assertEquals("40", headroom.getElement().getAttribute("hide-tolerance"));
    }

    @Test
    void setShowTolerance_setsAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setShowTolerance(25));
        assertEquals("25", headroom.getElement().getAttribute("show-tolerance"));
    }

    @Test
    void setTopOffset_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setTopOffset(-1));
        assertTrue(ex.getMessage().contains("topOffset"));
    }

    @Test
    void setHideTolerance_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setHideTolerance(-1));
        assertTrue(ex.getMessage().contains("hideTolerance"));
    }

    @Test
    void setShowTolerance_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setShowTolerance(-1));
        assertTrue(ex.getMessage().contains("showTolerance"));
    }

    @Test
    void toleranceSetters_acceptZero_asBoundaryValue() {
        var headroom = AppHeadroom.applyTo(new AppLayout())
                .setTopOffset(0).setHideTolerance(0).setShowTolerance(0);

        assertEquals("0", headroom.getElement().getAttribute("top-offset"));
        assertEquals("0", headroom.getElement().getAttribute("hide-tolerance"));
        assertEquals("0", headroom.getElement().getAttribute("show-tolerance"));
    }

    @Test
    void setTopBarCollapsible_setsBooleanAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setTopBarCollapsible(false));
        assertTrue(headroom.getElement().hasAttribute("top-bar-pinned"));

        headroom.setTopBarCollapsible(true);
        assertFalse(headroom.getElement().hasAttribute("top-bar-pinned"));
    }

    @Test
    void setBottomBarCollapsible_setsBooleanAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setBottomBarCollapsible(false));
        assertTrue(headroom.getElement().hasAttribute("bottom-bar-pinned"));

        headroom.setBottomBarCollapsible(true);
        assertFalse(headroom.getElement().hasAttribute("bottom-bar-pinned"));
    }

    @Test
    void settersChainTogether_andAllApply() {
        var headroom = AppHeadroom.applyTo(new AppLayout())
                .setTopOffset(10).setHideTolerance(20).setShowTolerance(30);

        assertEquals("10", headroom.getElement().getAttribute("top-offset"));
        assertEquals("20", headroom.getElement().getAttribute("hide-tolerance"));
        assertEquals("30", headroom.getElement().getAttribute("show-tolerance"));
    }

    @Test
    void isCollapsed_defaultsToFalse_beforeAnyClientSync() {
        assertFalse(AppHeadroom.applyTo(new AppLayout()).isCollapsed());
    }

    @Test
    void isCollapsed_reflectsManuallySetElementProperty() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        headroom.getElement().setProperty("pinned", false); // "pinned" (wire) means shown
        assertTrue(headroom.isCollapsed());
    }

    @Test
    void addCollapseChangeListener_invokedWithEventPayload_onFireEvent() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var invocationCount = new AtomicInteger();
        var receivedCollapsed = new AtomicReference<Boolean>();

        headroom.addCollapseChangeListener(event -> {
            invocationCount.incrementAndGet();
            receivedCollapsed.set(event.isCollapsed());
        });

        // Third constructor argument is the raw wire "pinned" (shown) value, not
        // "collapsed" directly - see CollapseChangeEvent's own Javadoc. pinned=false
        // here means collapsed=true.
        ComponentUtil.fireEvent(headroom, new AppHeadroom.CollapseChangeEvent(headroom, true, false));

        assertEquals(1, invocationCount.get());
        assertEquals(Boolean.TRUE, receivedCollapsed.get());
    }

    @Test
    void addCollapseChangeListener_registrationRemove_stopsFurtherInvocations() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var invocationCount = new AtomicInteger();
        var registration = headroom.addCollapseChangeListener(event -> invocationCount.incrementAndGet());

        registration.remove();
        ComponentUtil.fireEvent(headroom, new AppHeadroom.CollapseChangeEvent(headroom, true, false));

        assertEquals(0, invocationCount.get());
    }

    // --- Deprecated pinned-terminology delegation coverage ---
    // setTopBarPinned/setBottomBarPinned/isPinned/addPinnedChangeListener are
    // deprecated in favor of setTopBarCollapsible/setBottomBarCollapsible/
    // isCollapsed/addCollapseChangeListener - see AppHeadroom.java's Javadoc:
    // "pinned" collided across two unrelated meanings (a static "excluded from
    // the effect" config and a dynamic "currently shown" state). These confirm
    // the deprecated methods still work correctly by delegating, not that they
    // have independent behavior - including the inverted boolean on both the
    // static-config side AND the dynamic-state side this time (isPinned() is
    // "shown", isCollapsed() is "hidden" - opposites, unlike a same-polarity
    // rename).

    @Test
    void setTopBarPinned_delegatesToSetTopBarCollapsible_preservingOriginalWireBehavior() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setTopBarPinned(true));
        assertTrue(headroom.getElement().hasAttribute("top-bar-pinned"));

        headroom.setTopBarPinned(false);
        assertFalse(headroom.getElement().hasAttribute("top-bar-pinned"));
    }

    @Test
    void setBottomBarPinned_delegatesToSetBottomBarCollapsible_preservingOriginalWireBehavior() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setBottomBarPinned(true));
        assertTrue(headroom.getElement().hasAttribute("bottom-bar-pinned"));

        headroom.setBottomBarPinned(false);
        assertFalse(headroom.getElement().hasAttribute("bottom-bar-pinned"));
    }

    @Test
    void isPinned_delegatesToIsCollapsed_negated() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        headroom.getElement().setProperty("pinned", false);

        assertFalse(headroom.isPinned());
        assertTrue(headroom.isCollapsed());
        assertEquals(!headroom.isCollapsed(), headroom.isPinned());
    }

    @Test
    void addPinnedChangeListener_stillFiresIndependently_whenPinnedChangeEventIsFired() {
        // PinnedChangeEvent and CollapseChangeEvent are two independently
        // @DomEvent-mapped classes for the same underlying client event -
        // firing one doesn't automatically notify listeners of the other (see
        // AppHeadroom.java's onDetach(), which fires both explicitly for
        // exactly this reason). This only confirms the deprecated event class
        // and listener registration still work on their own.
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var invocationCount = new AtomicInteger();
        headroom.addPinnedChangeListener(event -> invocationCount.incrementAndGet());

        ComponentUtil.fireEvent(headroom, new AppHeadroom.PinnedChangeEvent(headroom, true, false));

        assertEquals(1, invocationCount.get());
    }

    @Test
    void isActive_defaultsToTrue_beforeAnyDeviceDetectionCompletes() {
        assertTrue(AppHeadroom.applyTo(new AppLayout()).isActive());
    }

    @Test
    void detectDeviceType_returnsDesktop_whenDetailsIsNull() {
        assertEquals(AppHeadroom.DeviceType.DESKTOP, AppHeadroom.detectDeviceType(null, 768));
    }

    @Test
    void detectDeviceType_returnsDesktop_whenNotTouchDevice() {
        var details = mock(ExtendedClientDetails.class);
        when(details.isTouchDevice()).thenReturn(false);

        assertEquals(AppHeadroom.DeviceType.DESKTOP, AppHeadroom.detectDeviceType(details, 768));
    }

    @Test
    void detectDeviceType_returnsTablet_whenTouchAndScreenAtOrAboveThreshold() {
        var details = mock(ExtendedClientDetails.class);
        when(details.isTouchDevice()).thenReturn(true);
        when(details.getScreenWidth()).thenReturn(1024);
        when(details.getScreenHeight()).thenReturn(768);

        assertEquals(AppHeadroom.DeviceType.TABLET, AppHeadroom.detectDeviceType(details, 768));
    }

    @Test
    void detectDeviceType_returnsPhone_whenTouchAndScreenBelowThreshold() {
        var details = mock(ExtendedClientDetails.class);
        when(details.isTouchDevice()).thenReturn(true);
        when(details.getScreenWidth()).thenReturn(844);
        when(details.getScreenHeight()).thenReturn(390);

        assertEquals(AppHeadroom.DeviceType.PHONE, AppHeadroom.detectDeviceType(details, 768));
    }

    @Test
    void detectDeviceType_classifiesByShorterScreenDimension_regardlessOfOrientation() {
        // 1200x700: the *shorter* side (700) is below the 768 threshold, even though
        // the longer side (1200) is well above it — a landscape-held device with a
        // 700px short side is a phone, not a tablet, regardless of how it's rotated.
        var details = mock(ExtendedClientDetails.class);
        when(details.isTouchDevice()).thenReturn(true);
        when(details.getScreenWidth()).thenReturn(1200);
        when(details.getScreenHeight()).thenReturn(700);

        assertEquals(AppHeadroom.DeviceType.PHONE, AppHeadroom.detectDeviceType(details, 768));
    }

    @Test
    void detectDeviceType_honorsCustomThreshold_shiftingThePhoneTabletBoundary() {
        // A 900px shorter side classifies as TABLET under the default 768 threshold,
        // but as PHONE once the threshold is explicitly raised past it.
        var details = mock(ExtendedClientDetails.class);
        when(details.isTouchDevice()).thenReturn(true);
        when(details.getScreenWidth()).thenReturn(900);
        when(details.getScreenHeight()).thenReturn(1200);

        assertEquals(AppHeadroom.DeviceType.TABLET, AppHeadroom.detectDeviceType(details, 768));
        assertEquals(AppHeadroom.DeviceType.PHONE, AppHeadroom.detectDeviceType(details, 1000));
    }

    @Test
    void setTabletMinShortSidePx_setsField_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setTabletMinShortSidePx(1000));
    }

    @Test
    void setTabletMinShortSidePx_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setTabletMinShortSidePx(-1));
        assertTrue(ex.getMessage().contains("tabletMinShortSidePx"));
    }

    @Test
    void setTransitionDuration_setsAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setTransitionDuration(150));
        assertEquals("150", headroom.getElement().getAttribute("transition-duration"));
    }

    @Test
    void setTransitionDuration_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setTransitionDuration(-1));
        assertTrue(ex.getMessage().contains("transitionDuration"));
    }

    @Test
    void asFloating_returnsSameInstance_onRepeatedCalls_soChainedSetComponentWorks() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var floating = headroom.getCondensedTop().asFloating();
        assertSame(floating, floating.setComponent(new Span("condensed top")));
    }

    @Test
    void asRibbon_returnsSameInstance_onRepeatedCalls_soChainedSetComponentWorks() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ribbon = headroom.getCondensedBottom().asRibbon();
        assertSame(ribbon, ribbon.setComponent(new Span("condensed bottom")));
    }

    @Test
    void asFloating_setComponent_attachesImmediately_regardlessOfTargetLayoutAttachState() {
        // Deliberately unconditional on attach state (see CondensedBar's Javadoc): an
        // Element subtree can be assembled before it's attached to a live UI, so
        // there's no need to defer - and deferring turned out to be actively harmful,
        // since a target layout's own attach/detach timing isn't reliably observable
        // from a peer element (see ARCHITECTURE_REVIEW.md for the real-world bug this
        // caused in a consuming app).
        var layout = new AppLayout(); // never attached to a UI in this test
        var headroom = AppHeadroom.applyTo(layout);
        var component = new Span("condensed top");

        headroom.getCondensedTop().asFloating().setComponent(component);

        assertEquals(headroom.getElement(), component.getElement().getParent());
    }

    @Test
    void asRibbon_setComponent_attachesImmediately_regardlessOfTargetLayoutAttachState() {
        var layout = new AppLayout(); // never attached to a UI in this test
        var headroom = AppHeadroom.applyTo(layout);
        var component = new Span("condensed bottom");

        headroom.getCondensedBottom().asRibbon().setComponent(component);

        assertNotNull(component.getElement().getParent()); // nested inside the frame, not headroom directly
    }

    @Test
    void asFloating_setComponent_calledAgain_removesPreviouslyAttachedComponent() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var floating = headroom.getCondensedTop().asFloating();
        var first = new Span("first");
        floating.setComponent(first);
        assertEquals(first.getElement(), headroom.getElement().getChild(0));

        var second = new Span("second");
        floating.setComponent(second);

        assertEquals(1, headroom.getElement().getChildCount());
        assertEquals(second.getElement(), headroom.getElement().getChild(0));
    }

    @Test
    void asFloating_setComponent_acceptsNull_withoutThrowing() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var floating = headroom.getCondensedTop().asFloating();
        assertSame(floating, floating.setComponent(null));
    }

    @Test
    void asRibbon_setComponent_acceptsNull_withoutThrowing() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ribbon = headroom.getCondensedBottom().asRibbon();
        assertSame(ribbon, ribbon.setComponent(null));
    }

    @Test
    void asFloating_setsShapeAttribute_toFloating_whenConfigured() {
        // Java tells app-headroom.ts directly, via this attribute, which shape (if
        // any) is configured - rather than that side inferring it by observing
        // slot/DOM content itself. See ATTR_CONDENSED_TOP_SHAPE's own doc.
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertFalse(headroom.getElement().hasAttribute(AppHeadroom.ATTR_CONDENSED_TOP_SHAPE));

        var floating = headroom.getCondensedTop().asFloating();
        floating.setComponent(new Span("condensed top"));
        assertEquals(AppHeadroom.SHAPE_FLOATING,
                headroom.getElement().getAttribute(AppHeadroom.ATTR_CONDENSED_TOP_SHAPE));

        floating.setComponent(null);
        assertFalse(headroom.getElement().hasAttribute(AppHeadroom.ATTR_CONDENSED_TOP_SHAPE));
    }

    @Test
    void asRibbon_setsShapeAttribute_toRibbon_whenConfigured() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertFalse(headroom.getElement().hasAttribute(AppHeadroom.ATTR_CONDENSED_BOTTOM_SHAPE));

        var ribbon = headroom.getCondensedBottom().asRibbon();
        ribbon.setComponent(new Span("condensed bottom"));
        assertEquals(AppHeadroom.SHAPE_RIBBON,
                headroom.getElement().getAttribute(AppHeadroom.ATTR_CONDENSED_BOTTOM_SHAPE));

        ribbon.setComponent(null);
        assertFalse(headroom.getElement().hasAttribute(AppHeadroom.ATTR_CONDENSED_BOTTOM_SHAPE));
    }

    @Test
    void asRibbon_afterAsFloating_tearsDownThePreviouslyBuiltFloatingComponent() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        headroom.getCondensedTop().asFloating().setComponent(new Span("floating"));
        assertEquals(1, headroom.getElement().getChildCount());

        headroom.getCondensedTop().asRibbon().setComponent(new Span("ribbon content"));

        assertEquals(1, headroom.getElement().getChildCount());
        assertEquals(AppHeadroom.SHAPE_RIBBON,
                headroom.getElement().getAttribute(AppHeadroom.ATTR_CONDENSED_TOP_SHAPE));
    }

    @Test
    void asRibbon_setComponent_nestsTheSuppliedComponent_insideAnAppHeadroomOwnedFrame() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var content = new Span("condensed bottom");

        headroom.getCondensedBottom().asRibbon().setComponent(content);

        var frame = headroom.getElement().getChild(0);
        assertNotNull(frame);
        assertEquals(content.getElement(), frame.getChild(0));
    }

    @Test
    void asRibbon_frame_defaultsToVaadinBackgroundContainer() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ribbon = headroom.getCondensedBottom().asRibbon();
        ribbon.setComponent(new Span("condensed bottom"));

        assertEquals("var(--vaadin-background-container)", ribbon.getStyle().get("background"));
    }

    @Test
    void asRibbon_getStyle_letsAppOverrideTheDefaultBackground() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ribbon = headroom.getCondensedBottom().asRibbon();
        ribbon.setComponent(new Span("condensed bottom"));

        ribbon.getStyle().setBackgroundColor("red");

        assertEquals("red", ribbon.getStyle().get("background-color"));
    }

    @Test
    void asRibbon_getStyle_throwsIllegalStateException_beforeSetComponentIsCalled() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ribbon = headroom.getCondensedBottom().asRibbon();
        assertThrows(IllegalStateException.class, ribbon::getStyle);
    }

    @Test
    void asRibbon_frame_padsTopBar_onTheTopSide_andBottomBar_onTheBottomSide() {
        var headroom = AppHeadroom.applyTo(new AppLayout());

        var topRibbon = headroom.getCondensedTop().asRibbon();
        topRibbon.setComponent(new Span("condensed top"));
        var bottomRibbon = headroom.getCondensedBottom().asRibbon();
        bottomRibbon.setComponent(new Span("condensed bottom"));

        assertEquals("env(safe-area-inset-top, 0px)", topRibbon.getStyle().get("padding-top"));
        assertNull(topRibbon.getStyle().get("padding-bottom"));
        assertEquals("env(safe-area-inset-bottom, 0px)", bottomRibbon.getStyle().get("padding-bottom"));
        assertNull(bottomRibbon.getStyle().get("padding-top"));
    }

    // --- Deprecated setRenderer(...) delegation coverage ---
    // setRenderer(...) is deprecated in favor of setComponent(Component) - see
    // AppHeadroom.java's Javadoc: the supplier here never added laziness or
    // repeated invocation, only indirection, since it's always invoked exactly
    // once, immediately. These confirm the deprecated method still works
    // correctly by delegating, not that it has behavior of its own.

    @Test
    void asFloating_setRenderer_invokesSupplierExactlyOnce_thenDelegatesToSetComponent() {
        var invocationCount = new AtomicInteger();
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var floating = headroom.getCondensedTop().asFloating();

        floating.setRenderer(() -> {
            invocationCount.incrementAndGet();
            return new Span("condensed top");
        });

        assertEquals(1, invocationCount.get());
        assertEquals(AppHeadroom.SHAPE_FLOATING,
                headroom.getElement().getAttribute(AppHeadroom.ATTR_CONDENSED_TOP_SHAPE));
    }

    @Test
    void asRibbon_setRenderer_invokesSupplierExactlyOnce_thenDelegatesToSetComponent() {
        var invocationCount = new AtomicInteger();
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var ribbon = headroom.getCondensedBottom().asRibbon();

        ribbon.setRenderer(() -> {
            invocationCount.incrementAndGet();
            return new Span("condensed bottom");
        });

        assertEquals(1, invocationCount.get());
        assertEquals("var(--vaadin-background-container)", ribbon.getStyle().get("background"));
    }

    @Test
    void asFloating_setRenderer_withNullSupplier_delegatesToSetComponentNull() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var floating = headroom.getCondensedTop().asFloating();
        floating.setRenderer(() -> new Span("condensed top"));

        assertSame(floating, floating.setRenderer(null));

        assertFalse(headroom.getElement().hasAttribute(AppHeadroom.ATTR_CONDENSED_TOP_SHAPE));
    }
}
