package org.vaadin.addons.joelpop.appheadroom.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.dependency.JsModule;

@Tag("app-headroom")
@JsModule("./app-headroom.ts")
public class AppHeadroom extends Component {

    private AppHeadroom() {}

    public static AppHeadroom create() {
        return new AppHeadroom();
    }

    /**
     * Applies headroom scroll-hide/show behavior to the given AppLayout and
     * returns the component for optional further configuration.
     */
    public static AppHeadroom applyTo(AppLayout layout) {
        var h = new AppHeadroom();
        layout.getElement().appendChild(h.getElement());
        return h;
    }

    public AppHeadroom setTopOffset(int px) {
        getElement().setAttribute("top-offset", String.valueOf(px));
        return this;
    }

    public AppHeadroom setHideTolerance(int px) {
        getElement().setAttribute("hide-tolerance", String.valueOf(px));
        return this;
    }

    public AppHeadroom setShowTolerance(int px) {
        getElement().setAttribute("show-tolerance", String.valueOf(px));
        return this;
    }
}
