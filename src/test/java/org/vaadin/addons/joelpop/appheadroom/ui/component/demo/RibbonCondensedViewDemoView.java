package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

/**
 * Demo view for {@link AppHeadroom.CondensedBar#asRibbon()}. Touch-optimized
 * (with a populated bottom navbar item) so navbar-bottom actually renders,
 * same reasoning as {@code CondensedViewDemoView}.
 */
@Route("headroom-demo-ribbon")
public class RibbonCondensedViewDemoView extends AppLayout {

    public static final String CONDENSED_TOP_ID = "condensed-top-view";
    public static final String CONDENSED_BOTTOM_ID = "condensed-bottom-view";

    public RibbonCondensedViewDemoView() {
        getStyle().set("--vaadin-app-layout-touch-optimized", "true");

        var headroom = AppHeadroom.applyTo(this)
                .setTopOffset(100)
                .setHideTolerance(40)
                .setShowTolerance(40);

        headroom.getCondensedTop().asRibbon().setRenderer(() -> {
            var span = new Span("Condensed top");
            span.setId(CONDENSED_TOP_ID);
            return span;
        });
        headroom.getCondensedBottom().asRibbon().setRenderer(() -> {
            var span = new Span("Condensed bottom");
            span.setId(CONDENSED_BOTTOM_ID);
            return span;
        });

        addToNavbar(new H3("Ribbon condensed views demo"));
        addToNavbar(true, new Span("Bottom bar"));

        var content = new Div();
        content.getStyle().set("min-height", "6000px");
        for (int i = 0; i < 80; i++) {
            content.add(new Paragraph("Filler content line " + i));
        }
        setContent(content);
    }
}
