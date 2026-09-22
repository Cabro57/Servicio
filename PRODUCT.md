# Product

<!-- impeccable:product-schema 1 -->

## Platform

desktop (Java Swing + FlatLaf, Windows and Linux; macOS out of scope)

## Users

Single-person technical service / repair shop. One operator does everything at the counter PC: receives devices, repairs them, collects payments, sells over the POS, buys and sells second-hand devices. Customers are often standing at the counter or calling on the phone while the app is in use.

## Product Purpose

Servicio runs the whole repair shop on one local desktop app: customers, devices, work orders, parts/stock, suppliers, labor, payments, customer accounts (cari hesap), POS sales and returns, second-hand device trade, and printable PDF forms / WhatsApp messages. Success means the operator answers any customer question (device status, debt, history) in seconds without leaving the current screen flow.

## Positioning

Offline-first, single-user, local SQLite desktop tool built for small Turkish repair shops; service records, POS sales and customer account balance live in one ledger (`v_customer_balances` combines work orders and sales).

## Operating Context

- Counter PC; screen sizes vary across installs (1366×768 laptops to 1920×1080 monitors) — layouts must work at both.
- Interruptions are constant: customer at the counter, phone calls, WhatsApp.
- Documents: service intake / fault diagnosis / quote-approval / payment receipt PDFs, 80mm POS receipts, second-hand purchase/sale forms.

## Capabilities and Constraints

- UI language Turkish by default, i18n supported (language, region, currency); all dialogs via raven.modal / DialogHelper, no JOptionPane.
- Data per customer: work orders, devices brought to service, POS sales/returns, payments, open documents and balance, second-hand device transactions.
- No network backend; everything local.

## Brand Commitments

Existing FlatLaf-based look (light and dark themes, accent color, rounded cards, SVG icons via `Ikon`) is the established world; surfaces extend it rather than replace it.

## Product Principles

1. Answer the counter question fast: status, debt, and history visible without navigation.
2. Use the screen: no wasted space at 1920 wide, nothing clipped at 1366 wide.
3. One operator, many hats: frequent actions (new service, collect payment, call/WhatsApp) are one click away.
4. Money is never ambiguous: balance color and sign always mean the same thing.
