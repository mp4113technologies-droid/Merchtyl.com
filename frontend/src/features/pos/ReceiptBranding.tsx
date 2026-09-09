import { Box, Stack, Typography } from '@mui/material';
import { MerchtylLogo } from '../../app/MerchtylLogo';
import type { ReceiptDocument } from '../../api/types';

export function CustomerReceiptHeader({ receipt }: { receipt: ReceiptDocument }) {
  const legalName = receipt.store.legalName?.trim();
  const storeName = receipt.store.name?.trim() || legalName || 'Store';
  const address = receipt.store.address?.trim();

  return <Stack spacing={0.25} textAlign="center" sx={{ maxWidth: '100%' }}>
    {legalName && legalName !== storeName ? <Typography variant="body2" fontWeight={600}>{legalName}</Typography> : null}
    <Typography
      component="h2"
      sx={{ fontSize: 22, fontWeight: 800, lineHeight: 1.15, overflowWrap: 'anywhere', wordBreak: 'normal' }}
    >
      {storeName}
    </Typography>
    {address ? <Typography variant="body2" sx={{ fontSize: 12, lineHeight: 1.25 }}>{address}</Typography> : null}
    <Typography variant="body2" sx={{ pt: 0.75, fontSize: 13, fontWeight: 600 }}>{receipt.brandTagline}</Typography>
  </Stack>;
}

/** Secondary platform attribution shared by Retail and Restaurant customer receipts. */
export function CustomerReceiptFooter() {
  return <Box textAlign="center" sx={{ mt: 1.5 }}>
    <Typography sx={{ fontSize: 10, fontWeight: 500, lineHeight: 1.2, mb: 0.4 }}>Powered by</Typography>
    <MerchtylLogo
      variant="black"
      size="medium"
      sx={{ display: 'block', width: '48%', maxWidth: 130, height: 'auto', objectFit: 'contain', mx: 'auto' }}
    />
  </Box>;
}
