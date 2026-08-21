# vaadin-flow-app-headroom

A Vaadin Flow component that hides the app header when the user scrolls down and restores it when they scroll up — the "headroom" pattern.

## Table of Contents

- [How it works](#how-it-works)
- [Requirement](#requirement)
- [Usage](#usage)
- [Configuration](#configuration)
- [Pinning individual bars](#pinning-individual-bars)
- [Condensed views](#condensed-views)
- [Server-visible pinned state](#server-visible-pinned-state)
- [Restricting activation by device type/orientation](#restricting-activation-by-device-typeorientation)
- [Development](#development)
  - [Running the demo](#running-the-demo)
  - [Integration tests](#integration-tests)
- [Credits](#credits)
- [Publishing to Vaadin Directory](#publishing-to-vaadin-directory)

## How it works

- Always shown within the first `topOffset` px from the top of the page (default: **100 px**)
- Hides after the user scrolls down more than `hideTolerance` px past a high-water mark (default: **30 px**)
- Restores after the user scrolls up more than `showTolerance` px from the hidden position (default: **30 px**)
- Guards against bottom overscroll/bounce on iOS causing a false restore
- Animates with `transform: translateY(±100%)` and a 600 ms ease transition — no layout shifts, no JavaScript-driven height recalculations
- `opacity` and `visibility` transition alongside `transform`, all three at the same duration, in both directions: `opacity` produces a genuine fade, and `visibility` (only becoming `hidden` once the fade fully completes, `visible` again from the very start of restoring) is what makes a bar truly disappear rather than just going transparent — including any of a bar's own content that doesn't respond to `transform` at all, e.g. a nav framework's own popover/overlay-based UI that escapes the normal paint hierarchy, since `visibility` is inherited regardless of how a descendant is rendered

The component is a LitElement web component (`<app-headroom>`) backed by `AppHeadroom.java`. It injects global CSS that enables body-scrolling on touch devices and animates `vaadin-app-layout`'s internal `::part(navbar-top)` and `::part(navbar-bottom)` slots.

## Requirement

The component attaches a passive `scroll` listener to `window`, so **body-scrolling mode must be active on touch devices** (`window.scrollY` must be meaningful). The component sets this automatically via an injected media-query rule:

```css
@media (pointer: coarse) {
    html { height: auto; }
}
```

No explicit configuration is needed in the consuming app.

## Usage

```java
AppHeadroom.applyTo(myAppLayout);
```

`applyTo` is the only entry point: it validates that `myAppLayout` is backed by
the standard `vaadin-app-layout` web component, creates an `AppHeadroom`, and
returns the instance for chaining. This works the same regardless of whether
`myAppLayout` is a plain `AppLayout` or a subclass (e.g. one that adds a
persistent side rail of its own) — `AppHeadroom` attaches itself as a peer
element alongside the layout (never as a light-DOM child of it), so it never
shows up in `myAppLayout.getChildren()` and needs no cooperation from
whatever the layout subclass does with its own children.

Call `headroom.remove()` to detach headroom behavior from the layout without
affecting the layout itself.

## Configuration

All setters return `this` for chaining. Call them before or after attaching — they map to HTML attributes read by the web component.

| Method                            | Default | Effect                                                     |
|-----------------------------------|---------|------------------------------------------------------------|
| `setTopOffset(int px)`            | 100     | Distance from page top within which chrome is always shown |
| `setHideTolerance(int px)`        | 30      | Scroll-down distance required to trigger hide              |
| `setShowTolerance(int px)`        | 30      | Scroll-up distance required to trigger restore             |
| `setTransitionDuration(int ms)`   | 600     | Duration of the show/hide slide and padding transitions    |

```java
AppHeadroom.applyTo(myAppLayout)
    .setTopOffset(64)
    .setHideTolerance(10)
    .setShowTolerance(10);
```

## Pinning individual bars

A bar that's already pinned to the viewport (`position: fixed`) and shaped
like a vertical rail rather than a horizontal bar (taller than wide) is left
alone automatically — a plain, observable geometry fact, not something
anything has to declare. For cases where that inference isn't right, override
it explicitly:

```java
AppHeadroom.applyTo(myAppLayout).setBottomBarPinned(true);
```

`setTopBarPinned(boolean)` / `setBottomBarPinned(boolean)` take precedence
over the automatic geometry check.

## Condensed views

By default, a hidden bar shows nothing — content simply reflows into the
vacated space. `getCondensedTop()`/`getCondensedBottom()` let you show a
small alternate view instead, cross-fading into place exactly as the real
bar slides away (and back out as it returns). Pick a **shape** first — the
same "pick one of a few mutually-exclusive modes, get back a shape-specific
accessor" pattern `Grid`'s `asSingleSelect()`/`asMultiSelect()` already use:

```java
AppHeadroom headroom = AppHeadroom.applyTo(myAppLayout);

headroom.getCondensedTop().asFloating()
        .setRenderer(() -> new CondensedHeader());

headroom.getCondensedBottom().asRibbon()
        .setRenderer(() -> new CondensedFooter());
```

Neither is configured by default — today's "show nothing while hidden"
behavior is unchanged unless you opt in. `setRenderer(...)` is invoked
immediately, whether or not the target `AppLayout` has attached to a live UI
yet; calling it again (including with `null`), or picking a different
shape, tears down whatever was previously built first.

Both shapes are positioned and sized entirely independently of `AppLayout`
— the view becomes a peer element positioned relative to the viewport, not
a descendant of the layout — and both handle safe-area clearance (a
notch/Dynamic Island or home indicator) automatically, so you're never
asked to get that right yourself:

- **`asFloating()`** — a small, compact view that centers itself
  automatically. `AppHeadroom` owns the whole gap around it, reconciled
  (not simply added) against the safe area, so there's nothing to configure
  beyond content.
- **`asRibbon()`** — a full-width, opaque bar whose background extends all
  the way into the safe area while only its content is inset from the
  unsafe zone, the same treatment the real bar's own landscape rule already
  uses. Defaults to `var(--vaadin-background-container)` — a base-theme
  property present under any theme, light/dark-aware automatically — so it
  fits your app out of the box; override it via the returned accessor's
  `getStyle()` if you want something else:
  ```java
  headroom.getCondensedBottom().asRibbon()
          .getStyle().setBackgroundColor("var(--my-brand-color)");
  ```

The transition is a plain cross-fade — the condensed view stays at its
final resting position the whole time; only opacity changes, timed with
`setTransitionDuration`. `--headroom-condensed-top-z-index` /
`--headroom-condensed-bottom-z-index` (default `200`) are plain CSS custom
property override points for stacking, the same convention as
`--headroom-landscape-bottom-bar-z-index`; `--headroom-condensed-gap`
(default `8px`) overrides `asFloating()`'s default gap the same way.

Fades purely on scroll position, the same as the real bar — including for a
bar the automatic pinned-rail geometry check keeps from ever hiding (see
[Pinning individual bars](#pinning-individual-bars)), since a condensed
Component has no structural relationship to whatever made the real bar
rail-shaped. The one thing that does suppress it: an explicit
`setTopBarPinned(true)`/`setBottomBarPinned(true)` — a deliberate "never hide
this" declaration from your own code, unlike the automatic geometry guess.

Clicking (or tapping) a condensed view always brings the real bars back —
both together, regardless of which one was clicked, since there's a single
pinned state for the whole layout, not one per bar. Nothing to configure:
the cursor changes to `var(--vaadin-clickable-cursor, pointer)` on devices
with a fine pointer (a mouse; touch-only devices show no cursor change,
having no cursor to change), and the condensed view's text is never
selectable, guarding against an accidental tap-and-hold or drag-select
gesture firing instead of the click.

## Server-visible pinned state

```java
var headroom = AppHeadroom.applyTo(myAppLayout);
headroom.addPinnedChangeListener(event -> System.out.println("pinned: " + event.isPinned()));
boolean currentlyPinned = headroom.isPinned();
```

`isPinned()` reflects whether the chrome is currently shown, kept in sync
with the client's own scroll-driven state (also reset to `true` if `headroom`
is detached via `remove()`). `addPinnedChangeListener` notifies on every
change instead of polling.

## Restricting activation by device type/orientation

By default the effect is active on every device. To restrict it, supply a
predicate over the session's detected device type and current orientation —
re-evaluated automatically whenever either becomes known or changes (e.g. on
rotation), with nothing further to wire up:

```java
AppHeadroom.applyTo(myAppLayout).setActivationPredicate((deviceType, orientation) ->
    deviceType == AppHeadroom.DeviceType.PHONE
        || (deviceType == AppHeadroom.DeviceType.TABLET && orientation == AppHeadroom.Orientation.LANDSCAPE));
```

Device type (`PHONE` / `TABLET` / `DESKTOP`) is classified from touch
capability and physical screen size (not viewport width, which fluctuates as
a desktop user resizes their browser window). `isActive()` reflects the
predicate's last evaluation. The screen-size threshold that separates
`TABLET` from `PHONE` (default `768`px, the shorter physical screen side)
is itself overridable — must be called before the layout attaches, since
device type is only ever detected once:

```java
AppHeadroom.applyTo(myAppLayout).setTabletMinShortSidePx(600);
```

The landscape-mode bottom bar's stacking order is a plain CSS custom
property, not a Java API — set `--headroom-landscape-bottom-bar-z-index`
directly on your `vaadin-app-layout` (default `200`) if it needs to sit
above or below other fixed-position chrome in your app.

## Development

### Running the demo

```
mvn jetty:run
```

Starts the test/demo server at http://localhost:8080.

### Integration tests

```
mvn verify -Pit
```

## Credits

The "headroom" name and hide-on-scroll-down/show-on-scroll-up interaction
pattern originate from [Headroom.js](https://github.com/WickyNilliams/headroom.js)
by Nick Williams (MIT License). This is an independent implementation built
for Vaadin Flow/Lit — it doesn't use any of that project's code — but the
pattern and name are its idea.

## Publishing to Vaadin Directory

You can create the zip package needed for [Vaadin Directory](https://vaadin.com/directory/) using

```
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions
mvn clean install -Pdirectory
```

The package is created as `target/{project-name}-1.0.0.zip`

For more information or to upload the package, visit https://vaadin.com/directory/my-components?uploadNewComponent
