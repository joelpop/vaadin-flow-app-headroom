package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

/**
 * Demo view for {@link AppHeadroom#setCondensedTopRenderer}/{@link
 * AppHeadroom#setCondensedBottomRenderer}. Touch-optimized (with a populated
 * bottom navbar item) so navbar-bottom actually renders, matching how
 * {@code ExplicitPinDemoView} avoids needing the {@code hidden}-attribute
 * removal workaround other, non-touch-optimized demos need.
 */
@Route("headroom-demo-condensed")
public class CondensedViewDemoView extends AppLayout {

    public static final String CONDENSED_TOP_ID = "condensed-top-view";
    public static final String CONDENSED_BOTTOM_ID = "condensed-bottom-view";
    public static final String REPLACED_CONDENSED_TOP_ID = "replaced-condensed-top-view";
    public static final String REPLACE_CONDENSED_TOP_BUTTON_ID = "replace-condensed-top-button";

    public CondensedViewDemoView() {
        getStyle().set("--vaadin-app-layout-touch-optimized", "true");

        var headroom = AppHeadroom.applyTo(this)
                .setTopOffset(100)
                .setHideTolerance(40)
                .setShowTolerance(40)
                .setCondensedTopRenderer(() -> {
                    var span = new Span("Condensed top");
                    span.setId(CONDENSED_TOP_ID);
                    return span;
                })
                .setCondensedBottomRenderer(() -> {
                    var span = new Span("Condensed bottom");
                    span.setId(CONDENSED_BOTTOM_ID);
                    return span;
                });

        var replaceButton = new Button("Replace condensed top", event ->
                headroom.setCondensedTopRenderer(() -> {
                    var span = new Span("Replaced condensed top");
                    span.setId(REPLACED_CONDENSED_TOP_ID);
                    return span;
                }));
        replaceButton.setId(REPLACE_CONDENSED_TOP_BUTTON_ID);

        addToNavbar(new H3("Condensed views demo"), replaceButton);
        addToNavbar(true, new Span("Bottom bar"));

        var content = new Div();
        content.getStyle().set("min-height", "6000px");
        for (int i = 0; i < 80; i++) {
            content.add(new Paragraph("Filler content line " + i));
        }
        setContent(content);
    }
}
