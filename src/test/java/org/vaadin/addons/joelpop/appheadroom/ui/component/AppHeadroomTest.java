package org.vaadin.addons.joelpop.appheadroom.ui.component;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
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

class AppHeadroomTest {

    @Tag("wrong-app-layout-tag")
    private static class WrongTagAppLayout extends AppLayout {}

    @Test
    void applyTo_appendsSingleHeadroomChild_andReturnsInstance() {
        var layout = new AppLayout();
        var headroom = AppHeadroom.applyTo(layout);

        assertNotNull(headroom);
        assertEquals(1, layout.getElement().getChildCount());
        assertEquals(headroom.getElement(), layout.getElement().getChild(0));
    }

    @Test
    void applyTo_appendsAlongsideExistingChildren_withoutRemovingThem() {
        var layout = new AppLayout();
        var existingNavItem = new Div();
        layout.addToNavbar(existingNavItem);

        var headroom = AppHeadroom.applyTo(layout);

        assertEquals(2, layout.getElement().getChildCount());
        assertEquals(existingNavItem.getElement(), layout.getElement().getChild(0));
        assertEquals(headroom.getElement(), layout.getElement().getChild(1));
    }

    @Test
    void create_returnsDetachedInstance_withNoParent() {
        var headroom = AppHeadroom.create();

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
        var headroom = AppHeadroom.create();
        assertSame(headroom, headroom.setTopOffset(150));
        assertEquals("150", headroom.getElement().getAttribute("top-offset"));
    }

    @Test
    void setHideTolerance_setsAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.create();
        assertSame(headroom, headroom.setHideTolerance(40));
        assertEquals("40", headroom.getElement().getAttribute("hide-tolerance"));
    }

    @Test
    void setShowTolerance_setsAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.create();
        assertSame(headroom, headroom.setShowTolerance(25));
        assertEquals("25", headroom.getElement().getAttribute("show-tolerance"));
    }

    @Test
    void setTopOffset_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.create();
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setTopOffset(-1));
        assertTrue(ex.getMessage().contains("topOffset"));
    }

    @Test
    void setHideTolerance_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.create();
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setHideTolerance(-1));
        assertTrue(ex.getMessage().contains("hideTolerance"));
    }

    @Test
    void setShowTolerance_throwsIllegalArgumentException_whenNegative() {
        var headroom = AppHeadroom.create();
        var ex = assertThrows(IllegalArgumentException.class, () -> headroom.setShowTolerance(-1));
        assertTrue(ex.getMessage().contains("showTolerance"));
    }

    @Test
    void toleranceSetters_acceptZero_asBoundaryValue() {
        var headroom = AppHeadroom.create()
                .setTopOffset(0).setHideTolerance(0).setShowTolerance(0);

        assertEquals("0", headroom.getElement().getAttribute("top-offset"));
        assertEquals("0", headroom.getElement().getAttribute("hide-tolerance"));
        assertEquals("0", headroom.getElement().getAttribute("show-tolerance"));
    }

    @Test
    void setTopBarPinned_setsBooleanAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.create();
        assertSame(headroom, headroom.setTopBarPinned(true));
        assertTrue(headroom.getElement().hasAttribute("top-bar-pinned"));

        headroom.setTopBarPinned(false);
        assertFalse(headroom.getElement().hasAttribute("top-bar-pinned"));
    }

    @Test
    void setBottomBarPinned_setsBooleanAttribute_andReturnsThisForChaining() {
        var headroom = AppHeadroom.create();
        assertSame(headroom, headroom.setBottomBarPinned(true));
        assertTrue(headroom.getElement().hasAttribute("bottom-bar-pinned"));

        headroom.setBottomBarPinned(false);
        assertFalse(headroom.getElement().hasAttribute("bottom-bar-pinned"));
    }

    @Test
    void settersChainTogether_andAllApply() {
        var headroom = AppHeadroom.create()
                .setTopOffset(10).setHideTolerance(20).setShowTolerance(30);

        assertEquals("10", headroom.getElement().getAttribute("top-offset"));
        assertEquals("20", headroom.getElement().getAttribute("hide-tolerance"));
        assertEquals("30", headroom.getElement().getAttribute("show-tolerance"));
    }

    @Test
    void isPinned_defaultsToTrue_beforeAnyClientSync() {
        assertTrue(AppHeadroom.create().isPinned());
    }

    @Test
    void isPinned_reflectsManuallySetElementProperty() {
        var headroom = AppHeadroom.create();
        headroom.getElement().setProperty("pinned", false);
        assertFalse(headroom.isPinned());
    }

    @Test
    void addPinnedChangeListener_invokedWithEventPayload_onFireEvent() {
        var headroom = AppHeadroom.create();
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
        var headroom = AppHeadroom.create();
        var invocationCount = new AtomicInteger();
        var registration = headroom.addPinnedChangeListener(event -> invocationCount.incrementAndGet());

        registration.remove();
        ComponentUtil.fireEvent(headroom, new AppHeadroom.PinnedChangeEvent(headroom, true, false));

        assertEquals(0, invocationCount.get());
    }
}
