import BackspaceOutlinedIcon from '@mui/icons-material/BackspaceOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import type { RegisterSession } from '../../api/types';
import { secureTill, unlockTill } from '../../api/client';
import { MerchtylLogo } from '../../app/MerchtylLogo';
import { useSession } from '../../app/session';

export function SecureTill({ session, storeName, busy = false }: { session: RegisterSession; storeName?: string; busy?: boolean }) {
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const [secured, setSecured] = React.useState(Boolean(session.tillSecured));
  const [pin, setPin] = React.useState('');
  const [usePassword, setUsePassword] = React.useState(false);
  const [password, setPassword] = React.useState('');
  const [noPinOpen, setNoPinOpen] = React.useState(false);

  React.useEffect(() => setSecured(Boolean(session.tillSecured)), [session.id, session.tillSecured]);

  const refresh = (updated: RegisterSession) => {
    setSecured(Boolean(updated.tillSecured));
    queryClient.setQueriesData({ queryKey: ['register-session'] }, (value: unknown) => {
      const candidate = value as RegisterSession | undefined;
      return candidate?.id === updated.id ? updated : value;
    });
  };
  const secure = useMutation({
    mutationFn: async () => secureTill(await getValidAccessToken(), session.id),
    onSuccess: (updated) => { refresh(updated); setPin(''); setPassword(''); }
  });
  const unlock = useMutation({
    mutationFn: async () => unlockTill(await getValidAccessToken(), session.id, usePassword ? { password } : { pin }),
    onSuccess: (updated) => { refresh(updated); setPin(''); setPassword(''); setUsePassword(false); }
  });
  const requestSecure = () => session.posPinConfigured ? secure.mutate() : setNoPinOpen(true);
  const digit = (value: string) => setPin(current => current.length < 6 ? current + value : current);

  React.useEffect(() => {
    if (!secured || usePassword) return;
    const handler = (event: KeyboardEvent) => {
      event.preventDefault(); event.stopImmediatePropagation();
      if (/^\d$/.test(event.key)) digit(event.key);
      else if (event.key === 'Backspace') setPin(value => value.slice(0, -1));
      else if (event.key === 'Enter' && pin.length === 6 && !unlock.isPending) unlock.mutate();
    };
    window.addEventListener('keydown', handler, true);
    return () => window.removeEventListener('keydown', handler, true);
  }, [secured, usePassword, pin, unlock.isPending]);

  return <>
    <Button variant="outlined" startIcon={<LockOutlinedIcon />} disabled={busy || secure.isPending} onClick={requestSecure}>Secure Till</Button>
    <Dialog open={noPinOpen} onClose={() => setNoPinOpen(false)}><DialogTitle>POS PIN required</DialogTitle><DialogContent><Alert severity="info">You need to set a POS PIN before securing this till. Ask an Owner or Manager to configure it from Users → POS Security.</Alert></DialogContent><DialogActions><Button onClick={() => setNoPinOpen(false)}>Close</Button></DialogActions></Dialog>
    {secured ? <Box role="dialog" aria-modal="true" aria-label="Till Secured" sx={{ position: 'fixed', inset: 0, zIndex: 1600, bgcolor: 'primary.dark', display: 'grid', placeItems: 'center', p: 2 }}>
      <Paper elevation={12} sx={{ width: 'min(430px, 100%)', p: { xs: 2.5, sm: 4 }, textAlign: 'center', borderRadius: 3 }}>
        <Stack spacing={2.25} alignItems="center">
          <MerchtylLogo variant="primary" size="medium" />
          <Box><Typography variant="h4" fontWeight={800}>Till Secured</Typography><Typography color="text.secondary">{storeName ?? 'Store'} · {session.deviceName ?? 'Register'}</Typography><Typography fontWeight={700}>{session.assignedCashierDisplayName}</Typography></Box>
          {unlock.isError ? <Alert severity="error" sx={{ width: '100%' }}>{session.tillPinLockedUntil && new Date(session.tillPinLockedUntil) > new Date() && !usePassword ? 'PIN resume is temporarily disabled. Use your password or try again later.' : `Invalid ${usePassword ? 'password' : 'PIN'}. Try again.`}</Alert> : null}
          {usePassword ? <TextField autoFocus fullWidth type="password" label="Password" value={password} onChange={event => setPassword(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && password) unlock.mutate(); }} /> : <>
            <Typography aria-label={`${pin.length} PIN digits entered`} sx={{ letterSpacing: 10, fontSize: 28, minHeight: 42 }}>{'●'.repeat(pin.length)}{'○'.repeat(6 - pin.length)}</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 72px)', gap: 1 }}>{['1','2','3','4','5','6','7','8','9'].map(value => <Button key={value} variant="outlined" sx={{ minHeight: 56, fontSize: 22 }} onClick={() => digit(value)}>{value}</Button>)}<IconButton aria-label="Delete PIN digit" onClick={() => setPin(value => value.slice(0, -1))}><BackspaceOutlinedIcon /></IconButton><Button variant="outlined" sx={{ fontSize: 22 }} onClick={() => digit('0')}>0</Button><Button aria-label="Resume Till" variant="contained" disabled={pin.length !== 6 || unlock.isPending} onClick={() => unlock.mutate()}>✓</Button></Box>
          </>}
          <Typography>Enter PIN to Resume</Typography>
          {usePassword ? <Button variant="contained" fullWidth disabled={!password || unlock.isPending} onClick={() => unlock.mutate()}>Resume Till</Button> : null}
          <Button onClick={() => { setUsePassword(value => !value); unlock.reset(); }}>{usePassword ? 'Use PIN' : 'Use Password'}</Button>
        </Stack>
      </Paper>
    </Box> : null}
  </>;
}
