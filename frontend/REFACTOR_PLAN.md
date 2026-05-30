# Frontend Refactor Plan

## Goal

Eliminate code duplication, break monolithic components into focused units, and extract missing abstractions — without changing any visible behavior or UI.

---

## Current Problems

| Issue | Severity | Locations |
|---|---|---|
| `AvatarDisplay` defined twice | High | `ProfilePage.jsx`, `BioPage.jsx` |
| `isValidHttpUrl()` + `getInitials()` defined twice | High | `ProfilePage.jsx`, `BioPage.jsx` |
| Password show/hide toggle duplicated | High | `LoginPage`, `RegisterPage` (×2) |
| API error handling (status code checks) repeated | High | 5+ files |
| `ShortenItem` is 640 lines with 8 responsibilities | High | `ShortenItem.jsx` |
| Copy-to-clipboard logic duplicated | Medium | `ShortenItem`, `BioPage` |
| Modal open/loading state pattern repeated | Medium | `ShortenItem` (4 pairs of states) |
| Loading → Empty → Content pattern repeated | Medium | Dashboard, Analytics, LinkHistory |
| Chart options config duplicated | Low | `Graph.jsx`, `ClicksLineChart.jsx` |
| `AnalyticsPage` mixes data transform + rendering | Medium | `AnalyticsPage.jsx` |

---

## Refactor Phases

---

### Phase 1 — Extract Shared Utilities
**Scope:** Pure functions only. Zero component changes. Safest to do first.

#### 1.1 Move helpers to `utils/helper.js`

Extract from `ProfilePage.jsx` and `BioPage.jsx` into the existing `utils/helper.js`:

```js
// Add to utils/helper.js
export function isValidHttpUrl(string) { ... }
export function getInitials(name) { ... }
```

Then remove the inline definitions and import from `helper.js` in both files.

#### 1.2 Create `utils/errorHandler.js`

Central API error handler to replace the repeated pattern:

```js
// utils/errorHandler.js
export function handleApiError(error, customMessages = {}) {
  const status = error?.response?.status;
  const message = customMessages[status] ?? defaultMessages[status] ?? "Something went wrong.";
  toast.error(message);
}
```

Replace inline status-check blocks in: `LoginPage`, `RegisterPage`, `CreateNewShorten`, `ShortenItem`, `ProfilePage`.

---

### Phase 2 — Extract Shared Components
**Scope:** New files in `components/Common/`. No existing component internals change.

#### 2.1 `components/Common/AvatarDisplay.jsx`

Both `ProfilePage` and `BioPage` define an identical `AvatarDisplay` inline. Extract it:

```jsx
// Props: avatarUrl, username, size (sm | md | lg)
// Renders: image with fallback → gradient initials circle
```

Replace both inline definitions with `<AvatarDisplay />`.

#### 2.2 `components/Common/PasswordField.jsx`

The show/hide toggle appears three times (Login once, Register twice). Extract into a component that wraps `TextField`:

```jsx
// Props: label, id, errors, register, required
// Manages: showPassword state, eye icon toggle internally
```

Replace all three password input blocks in `LoginPage` and `RegisterPage`.

#### 2.3 `components/Common/ErrorMessage.jsx`

The `<p className="text-sm font-semibold text-red-600 mt-1">` pattern appears 6+ times inline. Extract to a tiny presentational component:

```jsx
// Props: message (string | undefined)
// Renders nothing if message is falsy
```

#### 2.4 `components/Common/ActionButton.jsx`

`ShortenItem` defines an `ActionButton` nested component (~15 lines). This is a generic icon button with tooltip. Move it to `Common/` so it can be reused elsewhere:

```jsx
// Props: onClick, icon, label, variant, disabled, loading
```

#### 2.5 `components/Common/QueryLayout.jsx`

The loading → empty → content render pattern appears in three pages. Extract a wrapper component:

```jsx
// Props: isLoading, isEmpty, emptyState (node), children
// Renders: <Loader> | <EmptyState {...emptyState}> | children
```

Replace the repeated conditional blocks in `DashboardLayout`, `AnalyticsPage`, and `LinkHistoryPage`.

---

### Phase 3 — Extract Custom Hooks
**Scope:** New files in `hooks/`. Reduces per-component state boilerplate.

#### 3.1 `hooks/useCopyToClipboard.js`

```js
// Returns: { copied, copy(text) }
// Resets copied → false after 2s automatically
```

Replace copy logic in `ShortenItem` and `BioPage`.

#### 3.2 `hooks/useConfirmAction.js`

Replaces the repeated `[open, setOpen] + [loading, setLoading]` pairs for every confirmable action:

```js
// Returns: { open, loading, trigger(), confirm(asyncFn), close() }
```

`ShortenItem` currently has 4 of these pairs (`deleteOpen/deleteLoading`, `toggleOpen/toggleLoading`, etc.). Using this hook reduces it to 4 `useConfirmAction()` calls.

#### 3.3 `hooks/useShortenForm.js`

Extract the form submission logic and payload normalization out of `CreateNewShorten.jsx`:

```js
// Handles: validation, payload building, mutation call, error handling
// Returns: { onSubmit, isLoading, errors }
```

---

### Phase 4 — Split Monolithic Components
**Scope:** Break large files into focused sub-components. Most impactful for maintainability.

#### 4.1 Split `ShortenItem.jsx` (640 lines → ~4 files)

`ShortenItem` currently handles: display, inline edit, enable/disable toggle, delete, copy, QR display, link preview. Split by responsibility:

```
components/Dashboard/
├── ShortenItem.jsx          ← orchestrator only (~80 lines)
├── ShortenItemHeader.jsx    ← URL, copy button, status badge (~80 lines)
├── ShortenItemActions.jsx   ← edit / toggle / delete buttons (~100 lines)
└── ShortenItemExpanded.jsx  ← QR code + link preview panel (~100 lines)
```

`ShortenItem` becomes a thin container that composes the four sub-components and manages shared state via the hooks from Phase 3.

#### 4.2 Extract analytics data layer from `AnalyticsPage.jsx`

Move all `useMemo` computations, chart data builders, and live-data merging into `utils/analyticsTransform.js`:

```js
// Exports pure functions:
export function buildClicksTrend(analytics, liveAnalytics) { ... }
export function buildDimensionBreakdown(analytics) { ... }
export function mergeLiveData(base, live) { ... }
```

`AnalyticsPage` then only handles layout and rendering — no inline data transformation.

#### 4.3 Extract profile sections from `ProfilePage.jsx`

```
pages/ProfilePage.jsx      ← layout + edit state only
components/Profile/
├── ProfileAvatar.jsx      ← avatar upload/display (uses AvatarDisplay)
└── ProfileLinksSection.jsx← list of links with delete
```

---

### Phase 5 — Chart Deduplication (Low Priority)
**Scope:** Small cleanup, do last.

#### 5.1 Create `utils/chartConfig.js`

Both `Graph.jsx` and `ClicksLineChart.jsx` have nearly identical `options` objects. Extract shared defaults:

```js
// utils/chartConfig.js
export const baseChartOptions = { responsive: true, ... };
export function buildTooltipOptions(formatter) { ... }
```

Each chart file then spreads `baseChartOptions` and overrides only what it needs.

---

## Proposed Final Structure

```
frontend/src/
├── api/
│   └── api.js
├── components/
│   ├── Analytics/
│   │   ├── ClicksLineChart.jsx
│   │   ├── DimensionBar.jsx
│   │   ├── DimensionCard.jsx
│   │   ├── PeakActivityCard.jsx
│   │   └── VelocityBadge.jsx
│   ├── Common/
│   │   ├── ActionButton.jsx       ← NEW (extracted from ShortenItem)
│   │   ├── AvatarDisplay.jsx      ← NEW (extracted from ProfilePage + BioPage)
│   │   ├── Button.jsx
│   │   ├── Card.jsx
│   │   ├── ConfirmDialog.jsx
│   │   ├── DateRangePicker.jsx
│   │   ├── EmptyState.jsx
│   │   ├── ErrorMessage.jsx       ← NEW
│   │   ├── Footer.jsx
│   │   ├── Loader.jsx
│   │   ├── Logo.jsx
│   │   ├── Navbar.jsx
│   │   ├── PasswordField.jsx      ← NEW
│   │   ├── QueryLayout.jsx        ← NEW
│   │   ├── StatBlock.jsx
│   │   ├── StatusBadge.jsx
│   │   └── TextField.jsx
│   ├── Dashboard/
│   │   ├── CreateNewShorten.jsx
│   │   ├── Graph.jsx
│   │   ├── ShortenItem.jsx        ← SPLIT (orchestrator only)
│   │   ├── ShortenItemActions.jsx ← NEW
│   │   ├── ShortenItemExpanded.jsx← NEW
│   │   ├── ShortenItemHeader.jsx  ← NEW
│   │   ├── ShortenPopUp.jsx
│   │   └── ShortenUrlList.jsx
│   ├── Link/
│   │   ├── LinkPreviewCard.jsx
│   │   └── QRCodeDisplay.jsx
│   └── Profile/
│       ├── ProfileAvatar.jsx      ← NEW
│       └── ProfileLinksSection.jsx← NEW
├── hooks/
│   ├── useConfirmAction.js        ← NEW
│   ├── useCopyToClipboard.js      ← NEW
│   ├── useQuery.js
│   └── useShortenForm.js          ← NEW
├── pages/
│   ├── AboutPage.jsx
│   ├── AnalyticsPage.jsx          ← trimmed (data logic moved out)
│   ├── BioPage.jsx
│   ├── DashboardLayout.jsx
│   ├── ErrorPage.jsx
│   ├── LandingPage.jsx
│   ├── LinkDetailPage.jsx
│   ├── LinkHistoryPage.jsx
│   ├── LoginPage.jsx
│   ├── ProfilePage.jsx            ← trimmed (sections moved out)
│   ├── RegisterPage.jsx
│   └── ShortenUrlPage.jsx
└── utils/
    ├── analyticsTransform.js      ← NEW
    ├── apiRoutes.js
    ├── authUtils.js
    ├── chartConfig.js             ← NEW
    ├── constant.js
    ├── errorHandler.js            ← NEW
    └── helper.js                  ← updated (isValidHttpUrl, getInitials added)
```

---

## Execution Order

| Phase | What | Risk | Files Created | Files Modified |
|---|---|---|---|---|
| 1 | Shared utilities | Very Low | `errorHandler.js`, update `helper.js` | `ProfilePage`, `BioPage`, 5× error handler consumers |
| 2 | Shared components | Low | 5 new Common components | `LoginPage`, `RegisterPage`, `ProfilePage`, `BioPage`, 3× QueryLayout consumers |
| 3 | Custom hooks | Low | 3 new hooks | `ShortenItem`, `CreateNewShorten`, `BioPage` |
| 4 | Split monoliths | Medium | 5 new component files | `ShortenItem`, `AnalyticsPage`, `ProfilePage` |
| 5 | Chart dedup | Very Low | `chartConfig.js` | `Graph.jsx`, `ClicksLineChart.jsx` |

Work one phase at a time. Each phase is independently shippable — the app should be fully functional after every step.

---

## What Does Not Change

- No routing changes
- No API contract changes
- No state management architecture changes (React Query + useState stays)
- No UI or styling changes
- No dependencies added or removed
