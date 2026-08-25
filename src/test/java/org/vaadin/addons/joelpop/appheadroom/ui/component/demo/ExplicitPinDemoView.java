package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

/**
 * Demo view for the explicit setBottomBarCollapsible(false) override — an
 * ordinary (non-fixed, non-rail-shaped) touch-optimized bottom bar that
 * should never hide on scroll purely because the application asked for it
 * explicitly.
 */
@Route("headroom-demo-explicit-pin")
public class ExplicitPinDemoView extends AppLayout {

    public static final String CONDENSED_BOTTOM_ID = "condensed-bottom-view";

    public ExplicitPinDemoView() {
        // touch-optimized ensures the navbar-bottom slot is actually rendered
        // (it's `hidden` by default otherwise) - a standard vaadin-app-layout
        // custom property, not anything specific to a particular AppLayout subclass.
        getStyle().set("--vaadin-app-layout-touch-optimized", "true");
        addToNavbar(true, new Span("Bottom bar"));

        var content = new Div();
        content.getStyle().set("min-height", "6000px");
        for (int i = 0; i < 80; i++) {
            content.add(new Paragraph("Filler content line " + i));
        }
        setContent(content);

        var headroom = AppHeadroom.applyTo(this)
                .setTopOffset(100)
                .setHideTolerance(40)
                .setShowTolerance(40)
                .setBottomBarCollapsible(false);
        var condensedBottom = new Span("Condensed bottom");
        condensedBottom.setId(CONDENSED_BOTTOM_ID);
        headroom.getCondensedBottom().asFloating().setComponent(condensedBottom);
    }
}
