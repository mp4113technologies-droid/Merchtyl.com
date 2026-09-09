import { Box, Button, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { MerchtylLogo } from './MerchtylLogo';
import { useDeviceEnvironment } from './deviceEnvironment';
import { getMobileAccessPolicy, isDesktopOperationalRoute, isMobileManagementRoute } from './mobileAccessPolicy';
import { useSession } from './session';
import { merchtylTokens } from './theme';

function BrandedAccessPage({ title, message, action }: { title: string; message: string; action: ReactNode }) {
  return (
    <Box component="main" sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', px: 2, py: 4,
      background: `linear-gradient(145deg, ${merchtylTokens.colors.navyDark}, ${merchtylTokens.colors.navy} 55%, ${merchtylTokens.colors.blue})` }}>
      <Paper elevation={8} sx={{ width: '100%', maxWidth: 560, p: { xs: 3, sm: 5 }, borderRadius: 3 }}>
        <Stack spacing={2.5} alignItems="center" textAlign="center">
          <MerchtylLogo size="medium" />
          <Typography variant="h4" component="h1">{title}</Typography>
          <Typography color="text.secondary" sx={{ maxWidth: 430 }}>{message}</Typography>
          {action}
        </Stack>
      </Paper>
    </Box>
  );
}

export function MobilePortalGuard({ children }: { children: ReactNode }) {
  const { currentUser, session, logout } = useSession();
  const device = useDeviceEnvironment();
  const policy = getMobileAccessPolicy(currentUser?.roles ?? session?.roles ?? [], device);

  if (policy.mobilePortalAllowed) return <>{children}</>;

  return <BrandedAccessPage
    title="Mobile access unavailable"
    message="Merchtyl mobile access is available only to authorized management users. Please use your store register to operate POS."
    action={<Button variant="contained" size="large" onClick={() => void logout()}>Sign Out</Button>}
  />;
}

export function MobileManagementRouteGuard({ children }: { children: ReactNode }) {
  const location = useLocation();
  const device = useDeviceEnvironment();

  if (device.isDesktop || location.pathname.startsWith('/platform') || isMobileManagementRoute(location.pathname)) {
    return <>{children}</>;
  }

  const operational = isDesktopOperationalRoute(location.pathname);
  return <Stack spacing={2} sx={{ maxWidth: 680 }}>
    <Typography variant="h4" component="h1">{operational ? 'Operation unavailable on mobile' : 'Feature unavailable on mobile'}</Typography>
    <Typography color="text.secondary">
      {operational
        ? 'This live store operation is available only on a desktop register system.'
        : 'This section is not part of the approved mobile management experience.'}
    </Typography>
    <Button component={Link} to="/" variant="contained" sx={{ alignSelf: 'flex-start' }}>Back to Dashboard</Button>
  </Stack>;
}
