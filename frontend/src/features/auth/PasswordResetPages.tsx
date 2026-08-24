import LockResetIcon from '@mui/icons-material/LockReset';
import { Alert, Box, Button, Container, Link as MuiLink, Paper, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiClientError, forgotPassword, getPasswordPolicy, resetPassword } from '../../api/client';

const genericMessage = 'If an eligible account exists, password reset instructions have been sent.';

function ResetShell({ children, title }: { children: React.ReactNode; title: string }) {
  return <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', py: { xs: 4, md: 8 } }}><Container maxWidth="sm"><Stack spacing={3}>
    <Stack alignItems="center" spacing={1}><LockResetIcon color="primary" sx={{ fontSize: 42 }} /><Typography variant="h4" component="h1">{title}</Typography></Stack>
    <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>{children}</Paper>
  </Stack></Container></Box>;
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const mutation = useMutation({ mutationFn: () => forgotPassword(email) });
  return <ResetShell title="Forgot Password"><Stack component="form" spacing={2.5} onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }}>
    {mutation.isSuccess ? <Alert severity="success">{genericMessage}</Alert> : null}
    {mutation.isError ? <Alert severity="error">Unable to submit the request. Please try again later.</Alert> : null}
    <Typography color="text.secondary">Enter your account email and we’ll send a one-time reset link if the account is eligible.</Typography>
    <TextField label="Email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} />
    <Button type="submit" variant="contained" disabled={mutation.isPending}>{mutation.isPending ? 'Sending' : 'Send reset link'}</Button>
    <MuiLink component={Link} to="/login" textAlign="center">Back to sign in</MuiLink>
  </Stack></ResetShell>;
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const token = params.get('token') ?? '';
  const policy = useQuery({ queryKey: ['public-password-policy'], queryFn: getPasswordPolicy, staleTime: 300_000 });
  const mutation = useMutation({
    mutationFn: () => resetPassword({ token, newPassword, confirmPassword }),
    onSuccess: () => window.setTimeout(() => navigate('/login', { replace: true }), 1500)
  });
  return <ResetShell title="Reset Password"><Stack component="form" spacing={2.5} onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }}>
    {!token ? <Alert severity="error">This password reset link is invalid.</Alert> : null}
    {mutation.isSuccess ? <Alert severity="success">Password updated successfully. Please sign in with your new password.</Alert> : null}
    {mutation.isError ? <Alert severity="error">{resetErrorMessage(mutation.error)}</Alert> : null}
    <Typography color="text.secondary">{policy.data
      ? `Use ${policy.data.minimumLength}–${policy.data.maximumLength} characters${policy.data.requiresUppercase ? ', uppercase' : ''}${policy.data.requiresLowercase ? ', lowercase' : ''}${policy.data.requiresNumber ? ', a number' : ''}${policy.data.requiresSpecialCharacter ? ', and a symbol' : ''}.`
      : 'Use a strong, unique password.'}</Typography>
    <TextField label="New Password" type="password" autoComplete="new-password" required value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
    <TextField label="Confirm Password" type="password" autoComplete="new-password" required value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} error={Boolean(confirmPassword && newPassword !== confirmPassword)} helperText={confirmPassword && newPassword !== confirmPassword ? 'Passwords do not match' : undefined} />
    <Button type="submit" variant="contained" disabled={!token || mutation.isPending || newPassword !== confirmPassword}>{mutation.isPending ? 'Updating' : 'Update password'}</Button>
  </Stack></ResetShell>;
}

function resetErrorMessage(error: Error) {
  if (!(error instanceof ApiClientError)) return 'Password reset failed. Please try again.';
  if (['INVALID_RESET_TOKEN', 'EXPIRED_RESET_TOKEN', 'RESET_TOKEN_REVOKED', 'RESET_TOKEN_PURPOSE_INVALID'].includes(error.code ?? '')) {
    return 'This password reset link is invalid or has expired. Request a new password reset link.';
  }
  if (error.code === 'RESET_TOKEN_ALREADY_USED') return 'This password reset link has already been used. Request a new password reset link.';
  if (error.code === 'PASSWORD_CONFIRMATION_MISMATCH') return 'The passwords do not match.';
  if (error.code === 'PASSWORD_POLICY_VIOLATION') return error.violations.map((violation) => violation.message).join(' ');
  if (['ACCOUNT_DISABLED', 'ACCOUNT_ARCHIVED', 'TENANT_SUSPENDED', 'TENANT_CLOSED', 'PASSWORD_RESET_NOT_ALLOWED'].includes(error.code ?? '')) {
    return 'Password reset is not available for this account. Please contact support or your administrator.';
  }
  return `Password reset failed. Please try again.${error.correlationId ? ` Reference: ${error.correlationId}` : ''}`;
}
