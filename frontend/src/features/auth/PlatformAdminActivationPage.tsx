import { Alert, Button, Paper, Stack, TextField, Typography } from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { activatePlatformAdmin } from '../../api/client';

export function PlatformAdminActivationPage() {
  const [params] = useSearchParams(); const token = params.get('token') ?? '';
  const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState('');
  const mutation = useMutation({ mutationFn: () => activatePlatformAdmin({ token, password }) });
  return <Stack minHeight="100vh" alignItems="center" justifyContent="center" p={2}><Paper variant="outlined" sx={{ p: 3, width: '100%', maxWidth: 440 }}><Stack component="form" spacing={2} onSubmit={(e: FormEvent) => { e.preventDefault(); mutation.mutate(); }}><Typography variant="h5">Activate Platform Administrator</Typography>{mutation.isSuccess && <Alert severity="success">Account activated. You can now sign in.</Alert>}{mutation.isError && <Alert severity="error">This invitation is invalid or expired.</Alert>}<TextField label="New Password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)}/><TextField label="Confirm Password" type="password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} error={!!confirm && confirm !== password}/><Button type="submit" variant="contained" disabled={!token || password !== confirm || mutation.isPending}>Activate Account</Button>{mutation.isSuccess && <Button component={Link} to="/platform/login">Platform Sign In</Button>}</Stack></Paper></Stack>;
}
