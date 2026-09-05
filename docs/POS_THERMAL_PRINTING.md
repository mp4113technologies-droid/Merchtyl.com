# POS thermal printing

Merchtyl's POS receipt and kitchen-ticket code invokes the browser print operation only after the backend has confirmed and persisted the sale. On a production Windows POS workstation, Microsoft Edge kiosk printing accepts that POS print operation without showing its print confirmation UI.

## Windows workstation setup

1. Install the 80 mm thermal printer in Windows.
2. Print a Windows test page successfully.
3. Configure the intended POS thermal printer as the Windows/default printer.
4. Use Microsoft Edge for the POS terminal.
5. Create a dedicated **Merchtyl POS** desktop shortcut. Use the Edge path installed on the workstation and substitute the real merchant portal URL. A common 64-bit Edge shortcut target is:

   ```text
   "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --kiosk-printing --start-fullscreen https://{merchantSlug}.merchtyl.com
   ```

   Some installations use:

   ```text
   "C:\Program Files\Microsoft\Edge\Application\msedge.exe" --kiosk-printing --start-fullscreen https://{merchantSlug}.merchtyl.com
   ```

6. In Merchtyl's receipt-printer settings, select kiosk auto-print and enable automatic receipt printing for that POS browser profile.

Opening Edge normally cannot print silently: the browser/Windows print dialog will appear. Browser JavaScript cannot reliably detect whether `--kiosk-printing` was supplied, so the receipt-printer setting is not proof that the Edge process was launched correctly.

Retail POS automatically prints the persisted customer receipt. Restaurant / Kitchen POS presents application-level Kitchen Ticket, Customer Receipt, and Print Both choices after persistence. Print Both queues the kitchen ticket first and the customer receipt second. Manual reprint actions remain available and do not create another sale.

Reports are intentionally separate. EOD and any other administrative report continues to use its existing browser print action in a normal browser session, including the browser/Windows printer-selection dialog.

## Current limitation

Edge kiosk printing targets the printer selected by Windows/Edge. It does not give normal browser JavaScript reliable control over individual Windows printers. If a future workstation must route the customer receipt to a front-counter printer and the kitchen ticket to a different attached printer, the POS printing interfaces should be backed by a Merchtyl Windows Print Agent. The transaction workflows and receipt/ticket documents can remain unchanged; such an agent is outside the current implementation.
