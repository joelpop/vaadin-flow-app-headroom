package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;

/**
 * Plain AppLayout that never calls AppHeadroom.applyTo() — used to verify that
 * app-headroom.ts's injected CSS has zero effect here even though the module
 * is loaded elsewhere by the running app (see the [headroom-enabled] gating
 * on every injected rule).
 */
@Route("headroom-demo-ungated-css")
public class UngatedCssDemoView extends AppLayout {

    public UngatedCssDemoView() {
        // touch-optimized ensures the navbar-bottom slot is actually rendered
        // (it's `hidden` by default otherwise), matching how the other demo
        // views populate it.
        getStyle().set("--vaadin-app-layout-touch-optimized", "true");
        addToNavbar(true, new Span("Bottom bar"));

        var content = new Div();
        content.getStyle().set("min-height", "6000px");
        for (int i = 0; i < 80; i++) {
            content.add(new Paragraph("Filler content line " + i));
        }
        setContent(content);
    }
}