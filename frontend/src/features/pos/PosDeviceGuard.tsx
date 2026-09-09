import { Box, Button, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { MerchtylLogo } from '../../app/MerchtylLogo';
import { useDeviceEnvironment } from '../../app/deviceEnvironment';
import { merchtylTokens } from '../../app/theme';

export function PosDeviceGuard({ children }: { children: ReactNode }) {
  const { isDesktop } = useDeviceEnvironment();

  if (isDesktop) return <>{children}</>;

  return (
    <Box
      component="main"
      sx={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        px: 2,
        py: 4,
        background: `linear-gradient(145deg, ${merchtylTokens.colors.navyDark} 0%, ${merchtylTokens.colors.navy} 55%, ${merchtylTokens.colors.blue} 100%)`
      }}
    >
      <Paper elevation={8} sx={{ width: '100%', maxWidth: 560, p: { xs: 3, sm: 5 }, borderRadius: 3 }}>
        <Stack spacing={2.5} alignItems="center" textAlign="center">
          <MerchtylLogo size="medium" />
          <Typography variant="h4" component="h1">POS unavailable on mobile</Typography>
          <Typography color="text.secondary" sx={{ maxWidth: 430 }}>
            Retail and Restaurant POS are available only on desktop register systems. You can continue using management and reporting features from this device.
          </Typography>
          <Button component={Link} to="/store-menu" variant="contained" size="large">
            Back to Store Menu
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}
