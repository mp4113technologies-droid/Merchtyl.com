import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
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
        if (error instanceof ApiClientError && error.status === 401) {
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
    <Box sx={{ minHeight: '100dvh', bgcolor: 'background.default', py: { xs: 4, md: 8 } }}>
      <Container maxWidth="sm">
        <Stack spacing={3}>
          <Stack spacing={1} alignItems="center">
            <LockOutlinedIcon color="primary" sx={{ fontSize: 40 }} />
            <Typography variant="h4" component="h1">Merchtyl</Typography>
            <Typography color="text.secondary" textAlign="center">
              Sign in to open the retail workspace.
            </Typography>
          </Stack>

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', p: { xs: 2, sm: 3 } }}>
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
              <Typography variant="body2" textAlign="center" color="text.secondary">
                Platform administrator?{' '}
                <MuiLink component={RouterLink} to="/platform/login" underline="hover">
                  Sign in to platform admin
                </MuiLink>
              </Typography>
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}
