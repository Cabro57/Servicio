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
typography:
  display:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em + 14px)"
    fontWeight: 700
  headline:
    fontFamily: "Roboto (FlatRobotoFont)"
    fontSize: "calc(1em + 12px)"
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
  quick-action-primary:
    backgroundColor: "{colors.accent-default-light}"
    textColor: "#FFFFFF"
    rounded: "{rounded.action}"
    padding: "9px 12px 9px 14px"
  quick-action:
    rounded: "{rounded.action}"
    padding: "9px 12px 9px 14px"
  period-toggle-selected:
    backgroundColor: "{colors.accent-default-light}"
    textColor: "#FFFFFF"
    rounded: "{rounded.control}"
    padding: "2px 6px"
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

Servicio is a working tool for one operator at a repair-shop counter who is constantly interrupted. The system is the established FlatLaf world (light, dark and OLED themes, user-selectable accent, Roboto, Ikon SVG line icons) extended with a small set of house rules: tinted rounded cards on the panel background, color that only ever means something, and keyboard reach everywhere. Everything is resolved from theme keys (`$Panel.background`, `$Component.accentColor`, `$Servicio.*Color`) so a theme switch repaints correctly without code.

Density is moderate: bold numbers large enough to read at a glance, everything else at base or one step smaller and muted. Surfaces are flat; depth comes from tint, not shadow. Money reads right-aligned in bold and is colored by sign.

**Key Characteristics:**
- Flat, tinted rounded cards (15px) grouped with 14px gutters.
- One accent fill per screen; all other color is semantic.
- Type hierarchy expressed as FlatLaf relative steps (`font: bold +N`), never absolute sizes.
- Every frequent action has an Alt+letter shortcut shown as a keycap; Ctrl+K opens the command palette.
- Every count, row and stage is clickable and opens its record or a filtered list.

## Colors

The palette is the active FlatLaf theme plus five semantic tokens defined per light/dark in `themes/FlatLaf.properties` and read through `SemanticColor`; nothing else carries hue.

### Primary
- **Theme Accent** (default light `accent-default-light`, dark `accent-default-dark`; user-overridable via `LafService`): fill of the single primary quick action, the selected period toggle, and text links (`DashboardUi.link`). Always referenced as `$Component.accentColor`.

### Secondary
- **Semantic set** (`Servicio.successColor` / `dangerColor` / `warningColor` / `infoColor` / `actionColor`): success = money in, settled, positive net; danger = debt, negative net, refunds; warning = open receivables, blocked work; info = in progress; action = waiting for the operator ("teslime hazır"). Light values are chosen for 4.5:1 on white.
- **Badge pairs** (`model/enums/BadgeColor` resolved by `BadgePalette`, separate light/dark bg+fg): YELLOW pending/blocked, BLUE ongoing, GREEN done, RED negative/return/debt, DARK_GREEN money settled, GRAY neutral/out of our hands, PURPLE needs operator action. The enum is normative for status, payment-type and pipeline-stage icons and badges.
- **Change badges** (`Label.greenBadge` / `Label.redBadge`, `change-up` / `change-down` at 10% fade background): period-over-period percentage only.

### Neutral
- **Panel Background** (`$Panel.background`): page ground.
- **Card Surface** (`tint($Panel.background, 25%)` light, `3%` dark; `card-surface-*` are the FlatLight/FlatDark results): every card.
- **Muted Text** (`$Label.disabledForeground`): captions, dates, zero counts, keycaps, secondary list lines.
- **Hairline** (`$Component.borderColor`): card border and keycap outline.

### Named Rules
**The One Accent Rule.** A screen carries at most one accent-filled action (home: "Yeni Servis"); the only other accent fill is the selected state of a segmented filter. The header "Yeni" menu button is deliberately unfilled.

**The Sign Rule.** Money is colored by sign through `DashboardUi.styleMoney`: positive success, negative danger, zero `$Label.foreground`. Receivables owed to the shop use warning. Within a breakdown of already-counted cash, positive lines stay neutral and only negatives turn danger.

**The Meaning-Only Rule.** Color never decorates. Same meaning, same color; different meaning, different color. No raw `new Color(...)` or hex in UI code; read `SemanticColor`, `BadgePalette`, or a `$` theme key.

## Typography

**Display Font:** Roboto (FlatRobotoFont, installed at bootstrap; FlatLaf default size as base)
**Body Font:** Roboto; Roboto Semibold for `$h*.font` titles

**Character:** One neutral family; hierarchy comes from weight and relative step, so it scales with FlatLaf UI scaling and user font size.

### Hierarchy
- **Display** (bold, base +14): the one hero figure, today's net cash.
- **Headline** (bold, base +12): pipeline stage counts; zero counts drop to muted.
- **Page Title** (bold, base +5): screen title ("Ana Sayfa") with a muted full date beneath.
- **Title** (semibold, `$h3.font`, base +3): card titles ("Bugünkü kasa", "Atölye", "Dikkat bekleyenler", "Kazanç").
- **Body** (regular or bold, base): row names and amounts in bold; period figures bold +1, net profit bold +3.
- **Label** (regular, base −1, muted): secondary lines, counts summaries ("3 satış · 1 iade · net tahsilat"), empty-state hints.
- **Keycap** (regular, base −2, muted): shortcut labels.

### Named Rules
**The Relative Step Rule.** Sizes are written as FlatLaf style offsets (`font: bold +N`, `$h3.font`, `font: -1`), never as point sizes.

## Layout

Home is a split horizon: a full-width header strip (page title + date left, five quick actions right, insets 14 18 10 18), then a two-column content area (insets 4 18 18 18, gap 14). Left column grows and may shrink to zero (`wmin 0`): pipeline, attention queue, active services, distribution. Right column is the docked cash ledger at `330px : 32% : 440px`: today's cash, today's movements, period earnings. Cards stack with 14px gaps; card interiors use 14 16 12 16 insets with 8–12px row gaps.

Width behavior (1366 to 1920): content tracks viewport width, horizontal scrolling is never allowed, vertical scroll uses a thin 5px bar. When the header strip is narrower than 1180 scaled px (1366 screen with drawer open), quick actions go compact: keycaps hide, the shortcut stays in the tooltip. Pipeline stages share a size group with `0:pref` minimum so they compress rather than clip.

App shell: top toolbar (drawer, back, forward, refresh, "Yeni" menu listing every `QuickAction` with accelerators) and a centered search button opening the palette. Footer is a fixed 30px business status bar tinted like cards (20% light / 5% dark): Kasa, Atölyede, teslime hazır, borçlu (each clickable, colored by meaning), then keycap reminders on the right. Technical info lives in the help popup, not the footer.

## Elevation & Depth

Flat. No shadows anywhere in the shell or home; separation comes from the tinted card surface plus a 1px `$Component.borderColor` hairline at 15px radius. Hover is expressed by a background appearing on flat row buttons, not by lift.

### Named Rules
**The No-Nesting Rule.** A card never sits inside a card; embedded charts drop their own card class and background.

## Shapes

Rounded, soft rectangles on a fixed radius ladder: keycap 6, controls/toggles/change badges 8, row buttons and secondary buttons 10, quick actions and pipeline stages 12, cards and status badges 15. Borders are hairlines only.

## Components

### Buttons
- **Quick action (primary):** accent fill, `Button.default.foreground` text and icon, 16px icon + bold label + keycap, arc 12, margin 9 14 9 12; hover darkens accent 6%, pressed 12%. One per screen.
- **Quick action (secondary):** default button, same anatomy with `Label.foreground` icon and muted keycap.
- **Row button:** transparent, arc 10, no border, margin 4 8; background appears on hover. Used for every list row, pipeline stage and receivables line so the whole row is the target.
- **Link:** toolbar-type text button in accent, margin 2 6 ("Tümünü gör", "Atölyedekileri listele", "Tekrar dene").
- **Secondary action:** default button arc 10 with a 16px icon ("Kasa Raporu", row "Tahsilat").

### Chips
- **Period filter:** toolbar toggle group (Bugün … Tümü), arc 8, margin 2 6; selected state is accent background with default-button foreground.
- **Status badge:** `BadgePalette.style` bold −1, arc 15, padding 4 10. **Change badge:** green/red badge class, signed percent, hidden when not comparable.

### Cards / Containers
- **Corner Style:** 15px.
- **Background:** card surface (style class `dashboardBackground`).
- **Shadow Strategy:** none (see Elevation).
- **Border:** 1px `$Component.borderColor`.
- **Internal Padding:** 14 16 12 16. Header row: title left, muted meta or link right.

### Inputs / Fields
- **Command palette field:** borderless, transparent, leading search icon, clear button, placeholder "Müşteri, servis, parça ara ya da bir işlem yaz…"; results between hairline separators, keycap hints below ("↑ ↓ gez", "Enter aç / çalıştır", "Esc kapat").

### Navigation
- Toolbar icon buttons arc 10; "Yeni" menu bold, unfilled; the side drawer menu is grouped. Footer status items are toolbar buttons with 14px muted icons.

### Keycap
Base −2 muted text in a 1px `$Component.borderColor` outline, arc 6, padding 1 5 (0 4 in footer and palette). On an accent fill the keycap uses default-button foreground at 75% with a 40% outline. Shortcut text always comes from `QuickAction.getShortcutText()` ("Alt+N").

### Empty State
Two centered lines inside the card: bold headline stating what is absent, muted −1 hint saying how it fills ("Bugün henüz hareket yok"). Error variant adds a "Tekrar dene" link. Loading uses the same shell ("Yükleniyor…").

## Do's and Don'ts

### Do:
- **Do** route every global action through `QuickAction` so toolbar menu, palette, quick-action strip, footer hints and Alt+letter bindings stay in one list.
- **Do** use Alt+letter or Ctrl+K for global shortcuts; F1–F8 and Ctrl+T/W/I belong to POS, Ctrl+1..6 to customer detail.
- **Do** color money with `DashboardUi.styleMoney` or the same success/danger/neutral mapping.
- **Do** make counts and rows open their record or a filtered list, with a tooltip saying where they go.
- **Do** mute zero values so the eye lands on non-empty stages.

### Don't:
- **Don't** add a second accent-filled button to a screen.
- **Don't** write `new Color(...)`, hex, or `BadgeColor.get*Hex()` directly in UI code.
- **Don't** nest a card inside a card.
- **Don't** set absolute font sizes; use FlatLaf relative steps.
- **Don't** reuse Alt+letters or keys owned by POS and customer detail.
