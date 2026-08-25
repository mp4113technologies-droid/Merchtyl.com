import { Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { ApiClientError, invitePlatformAdmin, listPlatformAdmins, resendPlatformAdminInvitation, updatePlatformAdminStatus } from '../../api/client';
import { useSession } from '../../app/session';

const friendly: Record<string, string> = {
  EMAIL_ALREADY_IN_USE: 'That email address is already in use.',
  PLATFORM_ADMIN_SELF_MODIFICATION_NOT_ALLOWED: 'You cannot deactivate your own administrator account.',
  LAST_SUPER_ADMIN_REQUIRED: 'At least one active Super Admin must remain.',
  INVITATION_ALREADY_ACCEPTED: 'This administrator has already activated their account.'
};

export function PlatformAdminsPage() {
  const { session, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const isSuper = session?.roles.includes('PLATFORM_SUPER_ADMIN') ?? false;
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<{ firstName: string; lastName: string; email: string; role: 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_SUPPORT_ADMIN' }>({ firstName: '', lastName: '', email: '', role: 'PLATFORM_SUPER_ADMIN' });
  const admins = useQuery({ queryKey: ['platform-admins'], queryFn: async () => listPlatformAdmins(await getValidAccessToken()) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['platform-admins'] });
  const invite = useMutation({ mutationFn: async () => invitePlatformAdmin(await getValidAccessToken(), form), onSuccess: () => { setOpen(false); refresh(); } });
  const resend = useMutation({ mutationFn: async (id: string) => resendPlatformAdminInvitation(await getValidAccessToken(), id), onSuccess: refresh });
  const status = useMutation({ mutationFn: async ({ id, enabled, version }: { id: string; enabled: boolean; version: number }) => updatePlatformAdminStatus(await getValidAccessToken(), id, enabled, version), onSuccess: refresh });
  const error = invite.error ?? resend.error ?? status.error;
  const message = error instanceof ApiClientError ? friendly[error.message] ?? error.message : error?.message;
  return <Stack spacing={3}>
    <Stack direction="row" justifyContent="space-between" alignItems="center"><div><Typography variant="h4">Platform Administrators</Typography><Typography color="text.secondary">Platform-level accounts with no tenant or store assignment.</Typography></div>{isSuper && <Button variant="contained" onClick={() => setOpen(true)}>Add Administrator</Button>}</Stack>
    {message && <Alert severity="error">{message}</Alert>}
    <Paper variant="outlined"><Table><TableHead><TableRow><TableCell>Name</TableCell><TableCell>Email</TableCell><TableCell>Role</TableCell><TableCell>Status</TableCell><TableCell>Last Login</TableCell><TableCell>Created At</TableCell><TableCell>Actions</TableCell></TableRow></TableHead><TableBody>
      {admins.data?.content.map((admin) => <TableRow key={admin.id}><TableCell>{admin.firstName} {admin.lastName}</TableCell><TableCell>{admin.email}</TableCell><TableCell>{admin.role.replaceAll('_', ' ')}</TableCell><TableCell><Chip size="small" label={admin.status.replaceAll('_', ' ')} color={admin.status === 'ACTIVE' ? 'success' : admin.status === 'PENDING_ACTIVATION' ? 'warning' : 'default'} /></TableCell><TableCell>{admin.lastLoginAt ? new Date(admin.lastLoginAt).toLocaleString() : 'Never'}</TableCell><TableCell>{new Date(admin.createdAt).toLocaleString()}</TableCell><TableCell><Stack direction="row" spacing={1}>{isSuper && admin.status === 'PENDING_ACTIVATION' && <Button size="small" onClick={() => resend.mutate(admin.id)}>Resend Invitation</Button>}{isSuper && admin.id !== session?.userId && admin.status !== 'PENDING_ACTIVATION' && <Button size="small" color={admin.status === 'ACTIVE' ? 'error' : 'primary'} onClick={() => { if (admin.status !== 'ACTIVE' || window.confirm('Deactivate this administrator? At least one active Super Admin must remain.')) status.mutate({ id: admin.id, enabled: admin.status !== 'ACTIVE', version: admin.version }); }}>{admin.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}</Button>}</Stack></TableCell></TableRow>)}
    </TableBody></Table></Paper>
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm"><Stack component="form" onSubmit={(event: FormEvent) => { event.preventDefault(); invite.mutate(); }}><DialogTitle>Add Platform Administrator</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}><TextField label="First Name" required value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })}/><TextField label="Last Name" required value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })}/><TextField label="Email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}/><TextField select label="Role" required value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as typeof form.role })}><MenuItem value="PLATFORM_SUPER_ADMIN">Super Admin</MenuItem><MenuItem value="PLATFORM_SUPPORT_ADMIN">Support Admin</MenuItem></TextField></Stack></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" variant="contained" disabled={invite.isPending}>Send Invitation</Button></DialogActions></Stack></Dialog>
  </Stack>;
}
