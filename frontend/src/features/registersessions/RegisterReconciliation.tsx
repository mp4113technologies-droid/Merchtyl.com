import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Grid, Stack,
  Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography
} from '@mui/material';
import { useMutation } from '@tanstack/react-query';
import * as React from 'react';
import { closeRegisterSession, startRegisterSessionClosing } from '../../api/client';
import type { CashLedgerBreakdown, CashLedgerSourceType, RegisterSessionStatus } from '../../api/types';
import { useSession } from '../../app/session';

export type ReconciliationSession = {
  id: string;
  status: RegisterSessionStatus;
  version: number;
  openingCash: number;
  expectedCash: number;
  countedCash: number | null;
  differenceCash: number | null;
  reconciliation: CashLedgerBreakdown | null;
};

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function sourceLabel(value: CashLedgerSourceType) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function reconciliationFor(session: ReconciliationSession): CashLedgerBreakdown {
  return session.reconciliation ?? {
    openingCash: session.openingCash,
    retailCashReceived: Math.max(0, session.expectedCash - session.openingCash), retailChange: 0,
    retailRefunds: 0, lotteryCashSales: 0, lotteryPayouts: 0, payoutReversals: 0,
    lotterySaleCancellations: 0, otherCashIn: 0, otherCashOut: 0,
    totalIn: Math.max(0, session.expectedCash - session.openingCash), totalOut: 0,
    expectedCash: session.expectedCash, sourceBreakdown: []
  };
}

export function ReconciliationBreakdown({ session, currencyCode = 'USD' }: { session: ReconciliationSession; currencyCode?: string }) {
  const reconciliation = reconciliationFor(session);
  const rows = [
    ['Opening cash', '+', reconciliation.openingCash], ['Retail cash received', '+', reconciliation.retailCashReceived],
    ['Retail change', '-', reconciliation.retailChange], ['Retail refunds', '-', reconciliation.retailRefunds],
    ['Lottery cash sales', '+', reconciliation.lotteryCashSales], ['Lottery payouts', '-', reconciliation.lotteryPayouts],
    ['Payout reversals', '+', reconciliation.payoutReversals], ['Lottery sale cancellations', '-', reconciliation.lotterySaleCancellations],
    ['Other cash in', '+', reconciliation.otherCashIn], ['Other cash out', '-', reconciliation.otherCashOut]
  ] as const;
  return <Stack spacing={2}>
    <Grid container spacing={2}>
      {[['Opening cash', reconciliation.openingCash], ['Cash in', reconciliation.totalIn], ['Cash out', reconciliation.totalOut], ['Expected cash', reconciliation.expectedCash]].map(([label, amount]) =>
        <Grid item xs={12} sm={6} md={3} key={label}><Typography variant="body2" color="text.secondary">{label}</Typography><Typography fontWeight={700}>{money(amount as number, currencyCode)}</Typography></Grid>)}
      {session.countedCash !== null ? <><Grid item xs={12} sm={6} md={3}><Typography variant="body2" color="text.secondary">Counted cash</Typography><Typography fontWeight={700}>{money(session.countedCash, currencyCode)}</Typography></Grid><Grid item xs={12} sm={6} md={3}><Typography variant="body2" color="text.secondary">Difference</Typography><Typography fontWeight={700} color={(session.differenceCash ?? 0) === 0 ? 'success.main' : 'warning.main'}>{money(session.differenceCash ?? 0, currencyCode)}</Typography></Grid></> : null}
    </Grid>
    <Table size="small" aria-label="Register reconciliation formula"><TableHead><TableRow><TableCell>Category</TableCell><TableCell align="center">Effect</TableCell><TableCell align="right">Amount</TableCell></TableRow></TableHead><TableBody>
      {rows.map(([label, sign, amount]) => <TableRow key={label}><TableCell>{label}</TableCell><TableCell align="center">{sign}</TableCell><TableCell align="right">{money(amount, currencyCode)}</TableCell></TableRow>)}
      <TableRow><TableCell><Typography fontWeight={700}>Expected closing cash</Typography></TableCell><TableCell align="center">=</TableCell><TableCell align="right"><Typography fontWeight={700}>{money(reconciliation.expectedCash, currencyCode)}</Typography></TableCell></TableRow>
    </TableBody></Table>
    {reconciliation.sourceBreakdown.length ? <Table size="small" aria-label="Reconciliation source breakdown"><TableHead><TableRow><TableCell>Source</TableCell><TableCell>Direction</TableCell><TableCell align="right">Amount</TableCell></TableRow></TableHead><TableBody>{reconciliation.sourceBreakdown.map((item) => <TableRow key={`${item.sourceType}-${item.direction}`}><TableCell>{sourceLabel(item.sourceType)}</TableCell><TableCell>{item.direction}</TableCell><TableCell align="right">{money(item.amount, currencyCode)}</TableCell></TableRow>)}</TableBody></Table> : <Typography variant="body2" color="text.secondary">No cash activity after opening float.</Typography>}
  </Stack>;
}

export function RegisterReconciliationDialog({ open, session, registerName, currencyCode, onClose, onCompleted }: {
  open: boolean; session: ReconciliationSession | null; registerName: string; currencyCode?: string;
  onClose: () => void; onCompleted: () => void | Promise<void>;
}) {
  const { getValidAccessToken } = useSession();
  const [active, setActive] = React.useState<ReconciliationSession | null>(session);
  const [countedCash, setCountedCash] = React.useState('');
  React.useEffect(() => { setActive(session); setCountedCash(session ? String(session.expectedCash) : ''); }, [session, open]);
  const start = useMutation({ mutationFn: async () => startRegisterSessionClosing(await getValidAccessToken(), active!.id, { version: active!.version }), onSuccess: setActive });
  const complete = useMutation({
    mutationFn: async () => closeRegisterSession(await getValidAccessToken(), active!.id, { countedCash: Number(countedCash), version: active!.version }),
    onSuccess: async () => { await onCompleted(); onClose(); }
  });
  const error = start.error ?? complete.error;
  const variance = active ? Number(countedCash || 0) - active.expectedCash : 0;
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="md" transitionDuration={0}>
    <DialogTitle>Complete Register Reconciliation</DialogTitle><DialogContent><Stack spacing={2} sx={{ pt: 1 }}>
      <Typography variant="h6">{registerName}</Typography>{error ? <Alert severity="error">{error instanceof Error ? error.message : 'Reconciliation failed'}</Alert> : null}
      {active ? <ReconciliationBreakdown session={active} currencyCode={currencyCode} /> : null}
      <TextField label="Actual cash count" type="number" inputProps={{ min: 0, step: '0.01' }} value={countedCash} onChange={(event) => setCountedCash(event.target.value)} required />
      <Typography>Variance: <strong>{money(variance, currencyCode)}</strong></Typography>
    </Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button>
      {active?.status === 'OPEN' ? <Button variant="contained" disabled={start.isPending} onClick={() => start.mutate()}>Start Closing</Button> : <Button variant="contained" startIcon={<ReceiptLongOutlinedIcon />} disabled={!active || active.status !== 'CLOSING' || complete.isPending || countedCash === '' || Number(countedCash) < 0} onClick={() => complete.mutate()}>Complete Reconciliation</Button>}
    </DialogActions></Dialog>;
}
