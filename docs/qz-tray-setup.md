# QZ Tray Receipt Printer Setup

Merchtyl supports two receipt-printing paths:

- Browser print dialog, which works without hardware integration.
- QZ Tray direct printing, which sends receipt jobs to a configured local printer.

## Install QZ Tray

1. Download and install QZ Tray from https://qz.io/download/.
2. Start the QZ Tray desktop app on the register workstation.
3. Confirm the receipt printer is installed in the operating system.
4. Open `/settings/hardware/printers` in Merchtyl.
5. Select `QZ Tray printer`.
6. Enter the operating-system printer name exactly as QZ Tray sees it.
7. Set the receipt width and copy count.
8. Save settings, then use `Check QZ` and `Test print`.

QZ Tray connects through a local websocket. If `Check QZ` fails, confirm QZ Tray is running and that browser or antivirus software is not blocking localhost websocket access.

## Printer Name

The printer name must match the printer registered with the operating system. Use QZ Tray's bundled sample page or the QZ Tray logs to confirm the exact name when unsure.

## Browser Fallback

When `Fallback to browser` is enabled, Merchtyl attempts QZ Tray first. If QZ connection, printer lookup, or printing fails, Merchtyl opens the browser print dialog instead and reports the QZ error on screen.

A receipt-printing failure never reverses or voids a completed sale. The sale remains completed and the receipt can be reprinted.

## Cash Drawer Pulse

Cash drawer pulse is optional and only applies to QZ Tray printing. The default command is:

```text
\x1Bp\x00\x19\xFA
```

This is an ESC/POS drawer-kick command commonly used by receipt printers. Confirm the command against the printer and cash drawer hardware manuals before enabling it.

## Silent Printing

QZ Tray may show trust prompts unless the site is configured with QZ signing certificates. For silent production printing, configure QZ Tray signing according to the official QZ Tray signing guide: https://qz.io/docs/signing.

Official QZ Tray getting-started documentation is available at https://qz.io/wiki/getting-started.
