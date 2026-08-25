import { Alert, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { activatePlatformAdmin, ApiClientError, getPasswordPolicy } from '../../api/client';

export function PlatformAdminActivationPage() {
  const [params] = useSearchParams(); const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState('');
  const policy = useQuery({ queryKey: ['public-password-policy'], queryFn: getPasswordPolicy });
  const mutation = useMutation({ mutationFn: () => activatePlatformAdmin({ token, password }), onSuccess: () => window.setTimeout(() => navigate('/login', { replace: true }), 1200) });
  const code = mutation.error instanceof ApiClientError ? mutation.error.message : '';
  const used = code === 'ACTIVATION_TOKEN_ALREADY_USED';
  const invalid = !token || ['INVALID_ACTIVATION_TOKEN', 'EXPIRED_ACTIVATION_TOKEN', 'ACTIVATION_TOKEN_REVOKED'].includes(code);
  return <Stack minHeight="100vh" alignItems="center" justifyContent="center" p={2}><Paper variant="outlined" sx={{ p: 3, width: '100%', maxWidth: 440 }}><Stack component="form" spacing={2} onSubmit={(e: FormEvent) => { e.preventDefault(); mutation.mutate(); }}><Typography variant="h4">Merchtyl</Typography><Typography variant="h5">Set up your account</Typography><Typography color="text.secondary">You've been invited as a Merchtyl Platform Administrator.</Typography>{mutation.isSuccess && <Alert severity="success">Account setup complete. Redirecting to login…</Alert>}{used && <Alert severity="info">This account has already been activated.</Alert>}{invalid && <Alert severity="error">This invitation link is no longer valid or has expired.</Alert>}{!used && !invalid && <><Typography variant="body2" color="text.secondary">{policy.data ? `Use ${policy.data.minimumLength}–${policy.data.maximumLength} characters with uppercase, lowercase, a number, and a symbol.` : 'Use a strong, unique password.'}</Typography><TextField label="New Password" type="password" autoComplete="new-password" required value={password} onChange={(e) => setPassword(e.target.value)}/><TextField label="Confirm Password" type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} error={!!confirm && confirm !== password} helperText={confirm && confirm !== password ? 'Passwords do not match' : undefined}/><Button type="submit" variant="contained" disabled={password !== confirm || mutation.isPending}>Set Password</Button></>}<Button component={Link} to="/login">Go to Login</Button></Stack></Paper></Stack>;
}
