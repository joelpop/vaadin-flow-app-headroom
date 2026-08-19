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
    void setTopBarPinned_setsBooleanAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setTopBarPinned(true));
        assertTrue(headroom.getElement().hasAttribute("top-bar-pinned"));

        headroom.setTopBarPinned(false);
        assertFalse(headroom.getElement().hasAttribute("top-bar-pinned"));
    }

    @Test
    void setBottomBarPinned_setsBooleanAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setBottomBarPinned(true));
        assertTrue(headroom.getElement().hasAttribute("bottom-bar-pinned"));

        headroom.setBottomBarPinned(false);
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
    void isPinned_defaultsToTrue_beforeAnyClientSync() {
        assertTrue(AppHeadroom.applyTo(new AppLayout()).isPinned());
    }

    @Test
    void isPinned_reflectsManuallySetElementProperty() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        headroom.getElement().setProperty("pinned", false);
        assertFalse(headroom.isPinned());
    }

    @Test
    void addPinnedChangeListener_invokedWithEventPayload_onFireEvent() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var invocationCount = new AtomicInteger();
        var receivedPinned = new AtomicReference<Boolean>();

        headroom.addPinnedChangeListener(event -> {
            invocationCount.incrementAndGet();
            receivedPinned.set(event.isPinned());
        });

        ComponentUtil.fireEvent(headroom, new AppHeadroom.PinnedChangeEvent(headroom, true, false));

        assertEquals(1, invocationCount.get());
        assertEquals(Boolean.FALSE, receivedPinned.get());
    }

    @Test
    void addPinnedChangeListener_registrationRemove_stopsFurtherInvocations() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        var invocationCount = new AtomicInteger();
        var registration = headroom.addPinnedChangeListener(event -> invocationCount.incrementAndGet());

        registration.remove();
        ComponentUtil.fireEvent(headroom, new AppHeadroom.PinnedChangeEvent(headroom, true, false));

        assertEquals(0, invocationCount.get());
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
    void setCondensedTopRenderer_returnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setCondensedTopRenderer(() -> new Span("condensed top")));
    }

    @Test
    void setCondensedBottomRenderer_returnsThisForChaining() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setCondensedBottomRenderer(() -> new Span("condensed bottom")));
    }

    @Test
    void setCondensedTopRenderer_doesNotInvokeSupplier_beforeTargetLayoutAttachesToUI() {
        var invocationCount = new AtomicInteger();
        var layout = new AppLayout(); // never attached to a UI in this test

        AppHeadroom.applyTo(layout).setCondensedTopRenderer(() -> {
            invocationCount.incrementAndGet();
            return new Span("condensed top");
        });

        assertEquals(0, invocationCount.get());
    }

    @Test
    void setCondensedBottomRenderer_doesNotInvokeSupplier_beforeTargetLayoutAttachesToUI() {
        var invocationCount = new AtomicInteger();
        var layout = new AppLayout(); // never attached to a UI in this test

        AppHeadroom.applyTo(layout).setCondensedBottomRenderer(() -> {
            invocationCount.incrementAndGet();
            return new Span("condensed bottom");
        });

        assertEquals(0, invocationCount.get());
    }

    @Test
    void setCondensedTopRenderer_acceptsNull_withoutThrowing() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setCondensedTopRenderer(null));
    }

    @Test
    void setCondensedBottomRenderer_acceptsNull_withoutThrowing() {
        var headroom = AppHeadroom.applyTo(new AppLayout());
        assertSame(headroom, headroom.setCondensedBottomRenderer(null));
    }
}
