package org.vaadin.addons.joelpop.appheadroom.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Synchronize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;

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
        var tag = layout.getElement().getTag();
        if (!"vaadin-app-layout".equals(tag)) {
            throw new IllegalArgumentException(
                    "AppHeadroom.applyTo(...) expects the AppLayout's element tag "
                    + "to be 'vaadin-app-layout', but was '" + tag + "'. This usually "
                    + "means an AppLayout subclass overrode @Tag; headroom behavior "
                    + "targets the vaadin-app-layout web component specifically.");
        }
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

    @Synchronize("pinned-changed")
    public boolean isPinned() {
        return getElement().getProperty("pinned", true);
    }

    @DomEvent("pinned-changed")
    public static class PinnedChangeEvent extends ComponentEvent<AppHeadroom> {
        private final boolean pinned;

        public PinnedChangeEvent(AppHeadroom source, boolean fromClient,
                @EventData("event.detail.pinned") boolean pinned) {
            super(source, fromClient);
            this.pinned = pinned;
        }

        public boolean isPinned() {
            return pinned;
        }
    }

    public Registration addPinnedChangeListener(ComponentEventListener<PinnedChangeEvent> listener) {
        return addListener(PinnedChangeEvent.class, listener);
    }

    /**
     * Resets the server-visible pinned state directly, rather than relying on the
     * client's own {@code pinned-changed} event during teardown: when detachment is
     * server-initiated (e.g. {@code layout.remove(headroom)}), Flow stops routing
     * further client events for this component the moment removal begins, so the
     * client-fired event from {@code disconnectedCallback()} never reaches here.
     */
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        getElement().setProperty("pinned", true);
        ComponentUtil.fireEvent(this, new PinnedChangeEvent(this, false, true));
    }
}
