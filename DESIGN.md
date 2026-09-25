---
name: Servicio
description: Counter-side repair shop desktop app on FlatLaf (Swing), light and dark themes
colors:
  accent-default-light: "#2675BF"
  accent-default-dark: "#4B6EAF"
  success-light: "#0E7C4A"
  success-dark: "#32D74B"
  danger-light: "#B42318"
  danger-dark: "#FF6B6B"
  warning-light: "#B54708"
  warning-dark: "#FFB020"
  info-light: "#175CD3"
  info-dark: "#58A6FF"
  action-light: "#6941C6"
  action-dark: "#B692F6"
  change-up: "#1F9669"
  change-down: "#F43F5E"
  card-surface-light: "#F5F5F5"
  card-surface-dark: "#424547"
  list-card-surface-light: "#FFFFFF"
  row-hover-light: "#F7F7F7"
  row-selected-light: "#E5EEF7"
typography:
  display:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em + 14px)"
    fontWeight: 700
  headline:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em + 12px)"
    fontWeight: 700
  record-title:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em + 6px)"
    fontWeight: 700
  page-title:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em + 5px)"
    fontWeight: 700
  title:
    fontFamily: "Roboto Semibold (FlatLaf $h3.font)"
    fontSize: "calc(1em + 3px)"
    fontWeight: 600
  body:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "1em"
    fontWeight: 400
  label:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em - 1px)"
    fontWeight: 400
  keycap:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em - 2px)"
    fontWeight: 400
rounded:
  keycap: "6px"
  control: "8px"
  row: "10px"
  action: "12px"
  card: "15px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "14px"
  lg: "18px"
components:
  card:
    backgroundColor: "{colors.card-surface-light}"
    rounded: "{rounded.card}"
    padding: "14px 16px 12px 16px"
  list-card:
    backgroundColor: "{colors.list-card-surface-light}"
    rounded: "{rounded.card}"
    padding: "10px 14px 8px 14px"
  list-row:
    height: "48px"
  list-row-hover:
    backgroundColor: "{colors.row-hover-light}"
    height: "48px"
  list-row-selected:
    backgroundColor: "{colors.row-selected-light}"
    height: "48px"
  quick-action-primary:
    backgroundColor: "{colors.accent-default-light}"
    textColor: "#FFFFFF"
    rounded: "{rounded.action}"
    padding: "9px 12px 9px 14px"
  quick-action:
    rounded: "{rounded.action}"
    padding: "9px 12px 9px 14px"
  primary-button:
    backgroundColor: "{colors.accent-default-light}"
    textColor: "#FFFFFF"
    rounded: "{rounded.row}"
    padding: "7px 14px"
  secondary-button:
    rounded: "{rounded.row}"
    padding: "7px 12px"
  period-toggle-selected:
    backgroundColor: "{colors.accent-default-light}"
    textColor: "#FFFFFF"
    rounded: "{rounded.control}"
    padding: "2px 6px"
  view-tab-selected:
    backgroundColor: "{colors.accent-default-light}"
    textColor: "#FFFFFF"
    rounded: "{rounded.control}"
    padding: "4px 10px"
  row-button:
    rounded: "{rounded.row}"
    padding: "4px 8px"
  keycap:
    typography: "{typography.keycap}"
    rounded: "{rounded.keycap}"
    padding: "1px 5px"
---

# Design System: Servicio

## Overview

**Creative North Star: "The Counter Ledger"**

Servicio is a working tool for one operator at a repair-shop counter who is constantly interrupted. The system is the established FlatLaf world (light, dark and OLED themes, user-selectable accent, Roboto, Ikon SVG line icons) extended with a small set of house rules: rounded hairline cards on the panel background (tinted on home, white on list and detail pages), color that only ever means something, and keyboard reach everywhere. Everything is resolved from theme keys (`$Panel.background`, `$Component.accentColor`, `$Servicio.*`) so a theme or accent switch repaints correctly without code.

Density is moderate: bold numbers large enough to read at a glance, everything else at base or one step smaller and muted. Surfaces are flat; depth comes from surface tone and hairlines, not shadow. Money reads right-aligned in bold and is colored by meaning.

**Key Characteristics:**
- Flat rounded cards (15px): tinted on the home dashboard, white `listCard` on list and detail pages.
- Two shared page templates: the list page (`AbstractTableForm`) and the detail page (`DetailHeader` + `DetailKit`).
- One accent fill per screen; all other color is semantic.
- Type hierarchy expressed as FlatLaf relative steps (`font: bold +N`), never absolute sizes.
- Every frequent action has an Alt+letter shortcut shown as a keycap; Ctrl+K opens the command palette.
- Every count, row and stage is clickable and opens its record or a filtered list.

## Colors

The palette is the active FlatLaf theme plus semantic and surface keys defined per light/dark in `themes/FlatLaf.properties` and read through `SemanticColor` or `$` references; nothing else carries hue.

### Primary
- **Theme Accent** (default light `accent-default-light`, dark `accent-default-dark`; user-overridable via `LafService`): fill of the single primary action, the selected period toggle and view tab, and text links. Always referenced as `$Component.accentColor`.
- **On-Accent Text** (`Servicio.onAccentForeground` = `contrast($Component.accentColor, #1c1c1e, #ffffff, 40%)`): text, icon and keycap color on any accent fill. It picks near-black or white from the accent's lightness, so a light user accent (green, yellow, orange) stays legible.

### Secondary
- **Semantic set** (`Servicio.successColor` / `dangerColor` / `warningColor` / `infoColor` / `actionColor`): success = money in, settled, positive net; danger = debt, negative net, refunds; warning = open receivables, blocked work; info = in progress; action = waiting for the operator ("teslime hazır"). Light values are chosen for 4.5:1 on white.
- **Badge pairs** (`model/enums/BadgeColor` resolved by `BadgePalette`, separate light/dark bg+fg): YELLOW pending/blocked, BLUE ongoing, GREEN done, RED negative/return/debt, DARK_GREEN money settled, GRAY neutral/out of our hands, PURPLE needs operator action. The enum is normative for status, payment-type and pipeline-stage icons and badges.
- **Change badges** (`Label.greenBadge` / `Label.redBadge`, `change-up` / `change-down` at 10% fade background): period-over-period percentage only.

### Neutral
- **Panel Background** (`$Panel.background`): page ground.
- **Card Surface** (`tint($Panel.background, 25%)` light, `3%` dark; `card-surface-*` are the FlatLight/FlatDark results): home dashboard cards (`dashboardBackground`).
- **List Card Surface** (`$Table.background`; `list-card-surface-light` in FlatLight): the `listCard` style carrying list tables, detail identity bars and cards, settings dictionary lists and modal sub-cards.
- **Row Hover** (`Servicio.rowHoverBackground`: `darken($Table.background,3%)` light, `lighten($Table.background,4%)` dark): the hovered list row.
- **Row Selected** (`Servicio.rowSelectedBackground`: `mix($Component.accentColor,$Table.background,12%)` light, `22%` dark): the selected list row; text color does not change.
- **Muted Text** (`$Label.disabledForeground`): captions, dates, zero counts, keycaps, secondary list lines, summary clauses.
- **Hairline** (`$Component.borderColor`): card border, table row lines and keycap outline.

### Named Rules
**The One Accent Rule.** A screen carries at most one accent-filled action (home "Yeni Servis", list page "Yeni …", detail page primary); the only other accent fill is the selected state of a segmented filter (period toggles, view tabs). The header "Yeni" menu button is deliberately unfilled.

**The On-Accent Rule.** Anything drawn on an accent fill (label, icon, keycap, selected tab text) uses `$Servicio.onAccentForeground`, never `Button.default.foreground` or a fixed white.

**The Sign Rule.** Money is colored by meaning through `DashboardUi.styleMoney`: positive success, negative danger, zero `$Label.foreground`. Receivables owed to the shop use warning. Within a breakdown of already-counted cash, positive lines stay neutral and only negatives turn danger. In tables the same mapping is `MoneyCellRenderer` (right-aligned, bold): NEUTRAL for prices and totals; OWED for amounts still due (>0 warning, 0 muted "—" or "Ödendi" at −1); SIGN for net figures (success / danger / neutral); BALANCE for customer balances (owed to us warning, owed by us danger, 0 muted); NEGATIVE for already-counted cash (only refunds turn danger).

**The Meaning-Only Rule.** Color never decorates. Same meaning, same color; different meaning, different color. No raw `new Color(...)` or hex in UI code; read `SemanticColor`, `BadgePalette`, or a `$` theme key.

## Typography

**Display Font:** Roboto (FlatRobotoFont, installed at bootstrap; FlatLaf default size as base)
**Body Font:** Roboto; Roboto Semibold for `$h*.font` titles

**Character:** One neutral family; hierarchy comes from weight and relative step, so it scales with FlatLaf UI scaling and user font size.

### Hierarchy
- **Display** (bold, base +14): the one hero figure, today's net cash.
- **Headline** (bold, base +12): pipeline stage counts; zero counts drop to muted.
- **Record Title** (bold, base +6): the record name or number in a detail identity bar. Identity-bar stat values are bold +5.
- **Page Title** (bold, base +5): screen title ("Ana Sayfa", list page titles) with a muted line beneath (full date on home, summary sentence on lists).
- **Title** (semibold, `$h3.font`, base +3): card titles ("Bugünkü kasa", "Cihaz bilgileri", "Cihaz geçmişi").
- **Body** (regular or bold, base): row names, fact values and amounts in bold; period figures bold +1, net profit bold +3.
- **Label** (regular, base −1, muted): secondary lines, table headers, total counts, stat captions, empty-state hints.
- **Keycap** (regular, base −2): shortcut labels.

### Named Rules
**The Relative Step Rule.** Sizes are written as FlatLaf style offsets (`font: bold +N`, `$h3.font`, `font: -1`), never as point sizes.

## Layout

Home is a split horizon: a full-width header strip (page title + date left, five quick actions right, insets 14 18 10 18), then a two-column content area (insets 4 18 18 18, gap 14). Left column grows and may shrink to zero (`wmin 0`): pipeline, attention queue, active services, distribution. Right column is the docked cash ledger at `330px : 32% : 440px`: today's cash, today's movements, period earnings. Cards stack with 14px gaps; card interiors use 14 16 12 16 insets with 8–12px row gaps.

**List page** (`AbstractTableForm`, insets 14 18 16 18, 12px between header and card). Header: bold +5 title over a one-line `ListSummary` ("33 cihaz atölyede · 10 teslime hazır · 6 parça bekliyor"): muted clauses joined by " · ", the key count bold in `$Label.foreground`, meaningful clauses bold in their semantic color. Secondary actions and the single accent primary sit right, vertically centered across both lines. Below, one `listCard` (insets 10 14 8 14) holds, top to bottom: counted view tabs left and search (200:280:340px, Ctrl+F) right, the table, and a footer with the muted −1 total count ("69 kayıt") left and the pager right.

**Detail page** (insets 20, gap 16). A full-width identity bar on top, then a body of a growing list or work column on the left and a fixed fact rail on the right (340px; 360px for sale and work-order records). The rail stacks `DetailKit.card` fact cards. When title, stats and actions no longer fit one row (1366 with drawer open), the bar goes compact: actions drop to a right-aligned second row and the title is never truncated.

Width behavior (1366 to 1920): content tracks viewport width, horizontal scrolling is never allowed, vertical scroll uses a thin bar (5px home, 8px list and detail). When the home header strip is narrower than 1180 scaled px, quick actions go compact: keycaps hide, the shortcut stays in the tooltip. Pipeline stages share a size group with `0:pref` minimum so they compress rather than clip.

App shell: top toolbar (drawer, back, forward, refresh) and a centered search button opening the palette; every `QuickAction` is reached through the palette, Alt+letter and the home quick-action strip. Footer (`StatusBar`) is a fixed 30px ledger line tinted like cards (20% light / 5% dark). The left side is the business pulse. Kasa, Atölyede, Teslime hazır and Borçlu are each a muted −1 caption plus a bold value. The value is colored only by meaning, and zero stays muted. Each item is a flat hover row and opens its list. Items are separated by 14px hairlines. The right side is system state: the update indicator (visible only when there is something to do), the age of the last backup (warning when 7+ days old, danger when there is none; opens Ayarlar > Yedekleme), and the version (opens Hakkında). The footer has no keycap hints, no "Yeni" menu and no help button. Shortcuts are listed in Ayarlar > Klavye kısayolları. A lock button (same behavior as auto-lock: open modals return on unlock) sits in the drawer's avatar header.

## Elevation & Depth

Flat. No shadows anywhere; separation comes from the card surface (tinted on home, `$Table.background` on list and detail pages) plus a 1px `$Component.borderColor` hairline at 15px radius. Hover is expressed by a background appearing on flat rows and row buttons, not by lift.

### Named Rules
**The No-Nesting Rule.** A card never sits inside a card; embedded charts and tables drop their own card class and background.

## Shapes

Rounded, soft rectangles on a fixed radius ladder: keycap 6, controls/toggles/view tabs/change badges 8, row buttons, secondary and detail primary buttons and search fields 10, quick actions, the list "Yeni …" button and pipeline stages 12, cards and status badges 15. Borders are hairlines only.

## Components

### Buttons
- **Quick action (primary):** accent fill, `$Servicio.onAccentForeground` text and icon, 16px icon + bold label + keycap, arc 12, margin 9 14 9 12; hover darkens accent 6%, pressed 12%. One per screen. The list page "Yeni …" button uses the same anatomy (margin 8 14 8 12).
- **Quick action (secondary):** default button, same anatomy with `Label.foreground` icon and muted keycap.
- **Detail primary:** `DetailKit.primaryButton`: accent fill, on-accent bold label and 16px icon, arc 10, margin 7 14, same hover/pressed darkening; the `QuickAction` overload adds the on-accent keycap. The rightmost action of the identity bar.
- **Secondary action:** default button arc 10, margin 7 12, with a 16px `Label.foreground` icon ("Kasa Raporu", "Düzenle", row "Tahsilat").
- **Row button:** transparent, arc 10, no border, margin 4 8; background appears on hover. Used for every list row, pipeline stage and receivables line so the whole row is the target.
- **Link:** toolbar-type text button in accent, margin 2 6 ("Tümünü gör", "Müşteriye git", "Tekrar dene").

### Chips
- **Period filter:** toolbar toggle group (Bugün … Tümü), arc 8, margin 2 6; selected state is accent background with on-accent text.
- **View tabs:** `ViewTabs`, toolbar toggles arc 8, margin 4 10, each a predefined filter with its match count in regular weight after the label ("Tümü 69 · Tamirde 15"); selected is accent fill with on-accent text. Used on list cards and inside detail list sections.
- **Status badge:** `BadgePalette.style` bold −1, arc 15, padding 4 10. **Change badge:** green/red badge class, signed percent, hidden when not comparable.

### Cards / Containers
- **Corner Style:** 15px.
- **Background:** the home dashboard uses the tinted card surface (`dashboardBackground`); list pages, detail pages (including customer detail), settings dictionary lists and modal sub-cards use white `listCard`.
- **Shadow Strategy:** none (see Elevation).
- **Border:** 1px `$Component.borderColor`.
- **Internal Padding:** 14 16 12 16 on home; 14 16 14 16 for detail cards; 18 20 14 20 for a wide detail list section. Header row: `$h3.font` title left, muted meta, link or view tabs right.
- **Fact rows:** muted label left, bold value right-aligned, gap 12 8; empty values read "—".

### Inputs / Fields
- **List search:** arc 10, margin 3 8, leading 16px muted search icon, clear button, placeholder naming what it searches ("Müşteri, cihaz, seri no veya SRV no ara…").
- **Command palette field:** borderless, transparent, leading search icon, clear button, placeholder "Müşteri, servis, parça ara ya da bir işlem yaz…"; results between hairline separators, keycap hints below ("↑ ↓ gez", "Enter aç / çalıştır", "Esc kapat").

### Navigation
- Toolbar icon buttons arc 10; "Yeni" menu bold, unfilled; the side drawer menu is grouped. Footer status items are toolbar buttons with 14px muted icons. Detail pages add a toolbar back arrow (arc 12) at the start of the identity bar.

### List Table
`ListTable` styled by `TableStyler.applyStandardStyle`: 48px rows, horizontal hairlines only, 34px header in muted −1 regular. The whole row is the target: hover paints `rowHoverBackground`, a single click or Enter opens the record, and the cursor is a hand. Row action buttons render only on the hovered or selected row; settings dictionary lists follow the same hover-reveal. Two-line cells put the bold name over a muted −1 secondary line. Money cells use `MoneyCellRenderer` (see The Sign Rule). Empty, loading and no-result states render inside the card through `TableStatePanel`.

### Identity Bar
`DetailHeader` in a `listCard` (insets 14 16 14 20): back arrow; record title with badges, and a muted meta line joined by "   ·   "; a stat trio (muted −1 caption over bold +5 value, right-aligned, colored only when meaningful); secondary actions; the primary last. An optional bold danger warning line with an alert icon sits beneath.

### Keycap
Base −2 muted text in a 1px `$Component.borderColor` outline, arc 6, padding 1 5 (0 4 in footer and palette). On an accent fill the keycap is a chip: `$Servicio.onAccentForeground` at 85% for text, 12% fill and 40% outline (`QuickActionButton`, list "Yeni …", `DetailKit.primaryButton(…, QuickAction)`). Shortcut text always comes from `QuickAction.getShortcutText()` ("Alt+N").

### Empty State
Two centered lines inside the card: bold headline stating what is absent, muted −1 hint saying how it fills ("Bugün henüz hareket yok"). Error variant adds a "Tekrar dene" link. Loading uses the same shell ("Yükleniyor…").

## Do's and Don'ts

### Do:
- **Do** route every global action through `QuickAction` so toolbar menu, palette, quick-action strip, footer hints and Alt+letter bindings stay in one list.
- **Do** use Alt+letter or Ctrl+K for global shortcuts; F1–F8 and Ctrl+T/W/I belong to POS, Ctrl+1..6 to customer detail.
- **Do** color money with `DashboardUi.styleMoney`, or `MoneyCellRenderer` in tables, choosing the mode by what the amount means.
- **Do** make counts and rows open their record or a filtered list, with a tooltip saying where they go.
- **Do** mute zero values so the eye lands on non-empty stages.
- **Do** build new list screens on `AbstractTableForm` and new record screens on `DetailHeader` + `DetailKit`.
- **Do** draw text and icons on accent fills in `$Servicio.onAccentForeground`, and keycaps on accent in the chip style.

### Don't:
- **Don't** add a second accent-filled button to a screen.
- **Don't** write `new Color(...)`, hex, `Button.default.foreground` on accent, or `BadgeColor.get*Hex()` directly in UI code.
- **Don't** nest a card inside a card.
- **Don't** set absolute font sizes; use FlatLaf relative steps.
- **Don't** reuse Alt+letters or keys owned by POS and customer detail.
- **Don't** show always-visible row action icons on list tables; reveal them on hover or selection and let the row itself open the record.
- **Don't** use the tinted `dashboardBackground` card on list or detail pages; it belongs to the home dashboard.
