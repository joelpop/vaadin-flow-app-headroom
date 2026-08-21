package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

@Route("headroom-demo-detach")
public class DetachResetDemoView extends AppLayout {

    public static final String PINNED_STATE_ID = "pinned-state";
    public static final String REMOVE_BUTTON_ID = "remove-headroom-button";
    public static final String CONDENSED_TOP_ID = "condensed-top-view";

    public DetachResetDemoView() {
        var pinnedState = new Span("pinned");
        pinnedState.setId(PINNED_STATE_ID);

        var removeButton = new Button("Remove headroom");
        removeButton.setId(REMOVE_BUTTON_ID);

        addToNavbar(new H3("Detach demo"), removeButton);

        var content = new Div(pinnedState);
        content.getStyle().set("min-height", "6000px");
        for (int i = 0; i < 80; i++) {
            content.add(new Paragraph("Filler content line " + i));
        }
        setContent(content);

        var headroom = AppHeadroom.applyTo(this)
                .setTopOffset(100)
                .setHideTolerance(40)
                .setShowTolerance(40);
        headroom.getCondensedTop().asFloating().setRenderer(() -> {
            var span = new Span("Condensed top");
            span.setId(CONDENSED_TOP_ID);
            return span;
        });
        headroom.addPinnedChangeListener(event ->
                pinnedState.setText(event.isPinned() ? "pinned" : "unpinned"));

        removeButton.addClickListener(event -> headroom.remove());
    }
}
