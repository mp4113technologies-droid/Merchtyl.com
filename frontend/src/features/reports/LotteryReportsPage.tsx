import { Alert, Box, Button, Card, CardContent, Grid, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { getLotterySalesReport, listRegisters, listStores, listUsers } from '../../api/client';
import type { LotterySalesReport } from '../../api/types';
import { useSession } from '../../app/session';

const today = new Date().toISOString().slice(0, 10);
type Filters = { storeId: string; registerId: string; cashierId: string; dateFrom: string; dateTo: string; type: 'ALL'|'SOLD'|'WIN'; source: 'ALL'|'PHYSICAL_TICKET'|'MANUAL' };
const defaults: Filters = { storeId: '', registerId: '', cashierId: '', dateFrom: today, dateTo: today, type: 'ALL', source: 'ALL' };
const money = (value: number, currency: string) => new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);

function Summary({ report }: { report: LotterySalesReport }) {
  const values = [
    ['Physical Ticket Sales', report.physicalTicketSales],
    ['Manual Lottery Sold', report.manualLotterySold],
    ['Total Lottery Sold', report.totalLotterySold],
    ['Lottery Wins', report.lotteryWins],
    ['Net Lottery', report.netLottery],
    ['Actual Cash Payouts', report.actualCashPayouts]
  ] as const;
  return <Grid container spacing={1.5}>{values.map(([label, value]) => <Grid item xs={12} sm={6} lg={label === 'Net Lottery' ? 4 : 2} key={label}><Card variant="outlined"><CardContent><Typography color="text.secondary" variant="body2">{label}</Typography><Typography variant="h6" color={label === 'Net Lottery' && value < 0 ? 'error.main' : 'text.primary'}>{money(value, report.currencyCode)}</Typography></CardContent></Card></Grid>)}</Grid>;
}

export function LotteryReportsPage() {
  const { getValidAccessToken } = useSession();
  const [draft, setDraft] = React.useState(defaults);
  const [filters, setFilters] = React.useState(defaults);
  const stores = useQuery({ queryKey: ['stores', 'lottery-report'], queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 }) });
  const registers = useQuery({ queryKey: ['registers', 'lottery-report', draft.storeId], queryFn: async () => listRegisters(await getValidAccessToken(), { page: 0, size: 100, storeId: draft.storeId || undefined }), enabled: Boolean(draft.storeId) });
  const users = useQuery({ queryKey: ['users', 'lottery-report'], queryFn: async () => listUsers(await getValidAccessToken(), { page: 0, size: 100 }) });
  const report = useQuery({ queryKey: ['reports', 'lottery-sales', filters], queryFn: async () => getLotterySalesReport(await getValidAccessToken(), {
    storeId: filters.storeId || undefined, registerId: filters.registerId || undefined, cashierId: filters.cashierId || undefined,
    dateFrom: filters.dateFrom, dateTo: filters.dateTo, type: filters.type, source: filters.source
  }) });
  const set = (key: keyof Filters, value: string) => setDraft(current => ({ ...current, [key]: value, ...(key === 'storeId' ? { registerId: '' } : {}) } as Filters));
  return <Stack spacing={2}>
    <Box><Typography variant="h4">Lottery Sales</Typography><Typography color="text.secondary">Physical tickets and completed manual Lottery cart activity.</Typography></Box>
    <Paper variant="outlined" sx={{ p: 2 }}><Grid container spacing={1.5}>
      <Grid item xs={12} md={3}><TextField select fullWidth label="Store" value={draft.storeId} onChange={e => set('storeId', e.target.value)}><MenuItem value="">All Stores</MenuItem>{stores.data?.content.map(store => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}</TextField></Grid>
      <Grid item xs={12} md={3}><TextField select fullWidth label="Register" value={draft.registerId} onChange={e => set('registerId', e.target.value)} disabled={!draft.storeId}><MenuItem value="">All Registers</MenuItem>{registers.data?.content.map(register => <MenuItem key={register.id} value={register.id}>{register.name}</MenuItem>)}</TextField></Grid>
      <Grid item xs={12} md={3}><TextField select fullWidth label="Cashier" value={draft.cashierId} onChange={e => set('cashierId', e.target.value)}><MenuItem value="">All Cashiers</MenuItem>{users.data?.content.map(user => <MenuItem key={user.id} value={user.id}>{user.displayName}</MenuItem>)}</TextField></Grid>
      <Grid item xs={6} md={1.5}><TextField fullWidth type="date" label="From" value={draft.dateFrom} onChange={e => set('dateFrom', e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
      <Grid item xs={6} md={1.5}><TextField fullWidth type="date" label="To" value={draft.dateTo} onChange={e => set('dateTo', e.target.value)} InputLabelProps={{ shrink: true }} /></Grid>
      <Grid item xs={12} md={3}><TextField select fullWidth label="Type" value={draft.type} onChange={e => set('type', e.target.value)}><MenuItem value="ALL">All</MenuItem><MenuItem value="SOLD">Sold</MenuItem><MenuItem value="WIN">Win</MenuItem></TextField></Grid>
      <Grid item xs={12} md={3}><TextField select fullWidth label="Source" value={draft.source} onChange={e => set('source', e.target.value)}><MenuItem value="ALL">All</MenuItem><MenuItem value="PHYSICAL_TICKET">Physical Ticket</MenuItem><MenuItem value="MANUAL">Manual</MenuItem></TextField></Grid>
      <Grid item xs={12} md={3}><Button fullWidth variant="contained" sx={{ height: '100%' }} onClick={() => setFilters(draft)}>Apply Filters</Button></Grid>
    </Grid></Paper>
    {report.error ? <Alert severity="error">{report.error instanceof Error ? report.error.message : 'Unable to load Lottery Sales report.'}</Alert> : null}
    {report.data ? <><Summary report={report.data} /><Paper variant="outlined" sx={{ overflowX: 'auto' }}><Table size="small"><TableHead><TableRow>{['Date/Time','Register','Cashier','Type','Source','Description','Amount','Sale/Receipt #'].map(value => <TableCell key={value} align={value === 'Amount' ? 'right' : 'left'}>{value}</TableCell>)}</TableRow></TableHead><TableBody>{report.data.activities.map((row, index) => <TableRow key={`${row.occurredAt}-${index}`}><TableCell>{new Date(row.occurredAt).toLocaleString()}</TableCell><TableCell>{row.register}</TableCell><TableCell>{row.cashier}</TableCell><TableCell>{row.type}</TableCell><TableCell>{row.source === 'PHYSICAL_TICKET' ? 'Physical Ticket' : 'Manual'}</TableCell><TableCell>{row.description}</TableCell><TableCell align="right">{money(row.type === 'WIN' ? -row.amount : row.amount, report.data.currencyCode)}</TableCell><TableCell>{row.receiptNumber ?? '—'}</TableCell></TableRow>)}{report.data.activities.length === 0 ? <TableRow><TableCell colSpan={8} align="center">No Lottery activity found.</TableCell></TableRow> : null}</TableBody></Table></Paper></> : null}
  </Stack>;
}
