import LoginIcon from '@mui/icons-material/Login';
import {
  Alert,
  Box,
  Button,
  Container,
  Link as MuiLink,
  Paper,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { ApiClientError } from '../../api/client';
import { useSession } from '../../app/session';
import { useMerchantPortal } from '../../app/MerchantPortalContext';

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

type LoginForm = z.infer<typeof loginSchema>;

type LoginLocationState = {
  from?: {
    pathname?: string;
  };
};

export function AuthPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { portalContext, merchant } = useMerchantPortal();
  const merchantPortal = portalContext.type === 'MERCHANT' || (portalContext.type === 'DEVELOPMENT' && Boolean(portalContext.merchantSlug));
  const { loginWithCredentials, loginWithPlatformCredentials, sessionExpired, clearSessionExpired } = useSession();
  const from = (location.state as LoginLocationState | null)?.from?.pathname ?? '/';
  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: ''
    }
  });

  useEffect(() => () => clearSessionExpired(), [clearSessionExpired]);

  const mutation = useMutation({
    mutationFn: async (values: LoginForm) => {
      try {
        const result = await loginWithCredentials(values);
        if (result.authenticationStatus === 'PASSWORD_CHANGE_REQUIRED' && result.passwordChangeToken) {
          window.sessionStorage.setItem('merchtyl.passwordChangeToken', result.passwordChangeToken);
          window.sessionStorage.setItem('merchtyl.passwordChangeEmail', result.email);
          navigate('/first-login/change-password', { replace: true });
          return;
        }
      } catch (error) {
        if (!merchantPortal && error instanceof ApiClientError && error.status === 401) {
          await loginWithPlatformCredentials(values);
          return;
        }
        throw error;
      }
    },
    onSuccess: () => {
      if (window.sessionStorage.getItem('merchtyl.passwordChangeToken')) {
        return;
      }
      navigate(from, { replace: true });
    }
  });

  return (
    <Box sx={{ minHeight: '100dvh', bgcolor: 'background.default', py: { xs: 4, md: 7 }, display: 'grid', alignItems: 'center' }}>
      <Container maxWidth="sm">
        <Stack spacing={2.5}>
          <Stack spacing={0.75} alignItems="center">
            <Box component="img" src="/branding/merchtyl-logo-horizontal.png" alt="Merchtyl" sx={{ width: 220, maxWidth: '70vw', height: 58, objectFit: 'contain' }} />
            <Typography variant="h5" component="h1" color="primary.dark">
              {merchant ? `Welcome to ${merchant.displayName}` : 'Merchtyl'}
            </Typography>
            <Typography color="text.secondary" textAlign="center">
              {merchant ? `Sign in to continue to ${merchant.displayName}.` : 'Sign in to Merchtyl.'}
            </Typography>
          </Stack>

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', p: { xs: 2.5, sm: 3 }, borderRadius: 2 }}>
            <Stack component="form" spacing={2.5} onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
              {sessionExpired ? (
                <Alert severity="warning">Your session expired. Sign in again to continue.</Alert>
              ) : null}
              {mutation.isError ? <Alert severity="error">{mutation.error instanceof ApiClientError && mutation.error.code === 'ACCOUNT_LOCKED' ? 'Your account has been locked after multiple unsuccessful login attempts.' : mutation.error.message}</Alert> : null}

              <Controller
                name="email"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    label="Email"
                    type="email"
                    autoComplete="username"
                    error={Boolean(fieldState.error)}
                    helperText={fieldState.error?.message}
                    fullWidth
                  />
                )}
              />

              <Controller
                name="password"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    label="Password"
                    type="password"
                    autoComplete="current-password"
                    error={Boolean(fieldState.error)}
                    helperText={fieldState.error?.message}
                    fullWidth
                  />
                )}
              />

              <Button
                type="submit"
                variant="contained"
                size="large"
                disabled={mutation.isPending}
                startIcon={<LoginIcon />}
              >
                {mutation.isPending ? 'Signing in' : 'Sign in'}
              </Button>
              <MuiLink component={RouterLink} to="/forgot-password" textAlign="center" underline="hover">Reset Password</MuiLink>
              {!merchantPortal ? <Typography variant="body2" textAlign="center" color="text.secondary">
                Platform administrator?{' '}
                <MuiLink component={RouterLink} to="/platform/login" underline="hover">
                  Sign in to platform admin
                </MuiLink>
              </Typography> : null}
              <Typography variant="caption" textAlign="center" color="text.secondary">Powered by Merchtyl</Typography>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}
