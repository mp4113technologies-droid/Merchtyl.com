import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import {
  Alert,
  Box,
  Button,
  Container,
  Paper,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { firstLoginChangePassword } from '../../api/client';

const passwordSchema = z.object({
  newPassword: z.string()
    .min(12, 'Use at least 12 characters.')
    .regex(/[A-Z]/, 'Include an uppercase letter.')
    .regex(/[a-z]/, 'Include a lowercase letter.')
    .regex(/[0-9]/, 'Include a number.')
    .regex(/[^A-Za-z0-9]/, 'Include a symbol.'),
  confirmPassword: z.string().min(1, 'Confirm your new password.')
}).refine((value) => value.newPassword === value.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Passwords must match.'
});

type PasswordForm = z.infer<typeof passwordSchema>;

export function FirstLoginPasswordChangePage() {
  const navigate = useNavigate();
  const token = window.sessionStorage.getItem('merchtyl.passwordChangeToken');
  const email = window.sessionStorage.getItem('merchtyl.passwordChangeEmail');
  const form = useForm<PasswordForm>({
    resolver: zodResolver(passwordSchema),
    defaultValues: {
      newPassword: '',
      confirmPassword: ''
    }
  });

  const mutation = useMutation({
    mutationFn: async (values: PasswordForm) => {
      if (!token) {
        throw new Error('Password-change session is missing or expired. Sign in again with your temporary password.');
      }
      await firstLoginChangePassword({
        passwordChangeToken: token,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword
      });
    },
    onSuccess: () => {
      window.sessionStorage.removeItem('merchtyl.passwordChangeToken');
      window.sessionStorage.removeItem('merchtyl.passwordChangeEmail');
    }
  });

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', py: { xs: 4, md: 8 } }}>
      <Container maxWidth="sm">
        <Stack spacing={3}>
          <Stack spacing={1} alignItems="center">
            <LockResetOutlinedIcon color="primary" sx={{ fontSize: 42 }} />
            <Typography variant="h4" component="h1">Change password</Typography>
            <Typography color="text.secondary" textAlign="center">
              Your temporary password must be replaced before opening Merchtyl.
            </Typography>
          </Stack>

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', p: { xs: 2, sm: 3 } }}>
            <Stack component="form" spacing={2.5} onSubmit={form.handleSubmit((values) => mutation.mutate(values))}>
              {email && <Alert severity="info">Account: {email}</Alert>}
              {!token && <Alert severity="warning">Password-change session is missing or expired. Sign in again with your temporary password.</Alert>}
              {mutation.isError && <Alert severity="error">{mutation.error.message}</Alert>}
              {mutation.isSuccess && (
                <Alert severity="success">
                  Password changed successfully. Sign in again using your new password.
                </Alert>
              )}

              <Typography variant="body2" color="text.secondary">
                Use at least 12 characters with uppercase, lowercase, number, and symbol characters.
              </Typography>

              <Controller
                name="newPassword"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    label="New password"
                    type="password"
                    autoComplete="new-password"
                    error={Boolean(fieldState.error)}
                    helperText={fieldState.error?.message}
                    fullWidth
                  />
                )}
              />

              <Controller
                name="confirmPassword"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    label="Confirm password"
                    type="password"
                    autoComplete="new-password"
                    error={Boolean(fieldState.error)}
                    helperText={fieldState.error?.message}
                    fullWidth
                  />
                )}
              />

              <Button type="submit" variant="contained" size="large" disabled={mutation.isPending || !token || mutation.isSuccess}>
                Change Password
              </Button>
              {mutation.isSuccess && (
                <Button variant="outlined" onClick={() => navigate('/login', { replace: true })}>
                  Return to login
                </Button>
              )}
            </Stack>
          </Paper>
        </Stack>
      </Container>
    </Box>
  );
}
