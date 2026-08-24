import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import UndoOutlinedIcon from '@mui/icons-material/UndoOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Paper,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import {
  cancelLotterySale,
  listLotteryPayouts,
  listLotterySales,
  reverseLotteryPayout
} from '../../api/client';
import type { LotteryPayout, LotterySale, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery management request failed';
}

function idempotencyKey(prefix: string) {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function canManageLottery(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canReversePayout(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function LoadingPanel({ label: panelLabel }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 180 }}>
      <CircularProgress aria-label={panelLabel} />
      <Typography color="text.secondary">{panelLabel}</Typography>
    </Stack>
  );
}

function SaleCancellationPanel({
  sales,
  loading,
  disabled,
  error,
  onCancel
}: {
  sales: LotterySale[];
  loading: boolean;
  disabled: boolean;
  error?: string;
  onCancel: (sale: LotterySale, reason: string) => void;
}) {
  const [selectedSaleId, setSelectedSaleId] = React.useState('');
  const [reason, setReason] = React.useState('');
  const selectedSale = sales.find((sale) => sale.id === selectedSaleId);

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <CancelOutlinedIcon color="primary" />
          <Typography variant="h6">Cancel lottery sale</Typography>
        </Stack>
        {error ? <Alert severity="error">{error}</Alert> : null}
        {loading ? <LoadingPanel label="Loading lottery sales" /> : null}
        {!loading && sales.length === 0 ? <Alert severity="info">No recorded lottery sales are available for cancellation.</Alert> : null}
        {sales.length > 0 ? (
          <>
            <TextField
              select
              SelectProps={{ native: true }}
              label="Original sale"
              value={selectedSaleId}
              onChange={(event) => setSelectedSaleId(event.target.value)}
              disabled={disabled}
              fullWidth
            >
              <option value="">Select sale</option>
              {sales.map((sale) => (
                <option key={sale.id} value={sale.id}>
                  {sale.operatorName} {money(sale.amount, sale.currencyCode)} {sale.ticketReference ?? sale.id}
                </option>
              ))}
            </TextField>
            {selectedSale ? (
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" color="text.secondary">Payment</Typography>
                  <Typography fontWeight={700}>{label(selectedSale.paymentMethod)}</Typography>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" color="text.secondary">Cash returned</Typography>
                  <Typography fontWeight={700}>{selectedSale.paymentMethod === 'CASH' ? 'Yes' : 'No'}</Typography>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" color="text.secondary">Occurred</Typography>
                  <Typography fontWeight={700}>{new Date(selectedSale.occurredAt).toLocaleString()}</Typography>
                </Grid>
              </Grid>
            ) : null}
            <TextField
              label="Cancellation reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              disabled={disabled}
              fullWidth
              multiline
              minRows={3}
            />
            <Button
              variant="contained"
              color="warning"
              startIcon={<CancelOutlinedIcon />}
              disabled={disabled || !selectedSale || !reason.trim()}
              onClick={() => selectedSale && onCancel(selectedSale, reason)}
              sx={{ alignSelf: 'flex-start' }}
            >
              Cancel sale
            </Button>
          </>
        ) : null}
      </Stack>
    </Paper>
  );
}

function PayoutReversalPanel({
  payouts,
  loading,
  disabled,
  elevated,
  error,
  onReverse
}: {
  payouts: LotteryPayout[];
  loading: boolean;
  disabled: boolean;
  elevated: boolean;
  error?: string;
  onReverse: (payout: LotteryPayout, reason: string) => void;
}) {
  const [selectedPayoutId, setSelectedPayoutId] = React.useState('');
  const [reason, setReason] = React.useState('');
  const selectedPayout = payouts.find((payout) => payout.id === selectedPayoutId);

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <UndoOutlinedIcon color="primary" />
          <Typography variant="h6">Reverse lottery payout</Typography>
        </Stack>
        {!elevated ? <Alert severity="warning">Payout reversal requires manager approval permission.</Alert> : null}
        {error ? <Alert severity="error">{error}</Alert> : null}
        {loading ? <LoadingPanel label="Loading paid lottery payouts" /> : null}
        {!loading && payouts.length === 0 ? <Alert severity="info">No paid lottery payouts are available for reversal.</Alert> : null}
        {payouts.length > 0 ? (
          <>
            <TextField
              select
              SelectProps={{ native: true }}
              label="Original payout"
              value={selectedPayoutId}
              onChange={(event) => setSelectedPayoutId(event.target.value)}
              disabled={disabled || !elevated}
              fullWidth
            >
              <option value="">Select payout</option>
              {payouts.map((payout) => (
                <option key={payout.id} value={payout.id}>
                  {payout.operatorName} {money(payout.amount, payout.currencyCode)} {payout.ticketNumber}
                </option>
              ))}
            </TextField>
            {selectedPayout ? (
              <Grid container spacing={2}>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" color="text.secondary">Method</Typography>
                  <Typography fontWeight={700}>{label(selectedPayout.payoutMethod)}</Typography>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" color="text.secondary">Paid</Typography>
                  <Typography fontWeight={700}>{selectedPayout.paidAt ? new Date(selectedPayout.paidAt).toLocaleString() : 'Unknown'}</Typography>
                </Grid>
                <Grid item xs={12} md={4}>
                  <Typography variant="body2" color="text.secondary">Compensating entry</Typography>
                  <Typography fontWeight={700}>LOTTERY_PAYOUT_REVERSAL / IN</Typography>
                </Grid>
              </Grid>
            ) : null}
            <TextField
              label="Reversal reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              disabled={disabled || !elevated}
              fullWidth
              multiline
              minRows={3}
            />
            <Button
              variant="contained"
              color="warning"
              startIcon={<UndoOutlinedIcon />}
              disabled={disabled || !elevated || !selectedPayout || !reason.trim()}
              onClick={() => selectedPayout && onReverse(selectedPayout, reason)}
              sx={{ alignSelf: 'flex-start' }}
            >
              Reverse payout
            </Button>
          </>
        ) : null}
      </Stack>
    </Paper>
  );
}

export function LotteryManagementPage() {
  const { currentUser, session, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const allowed = canManageLottery(roles);
  const elevated = canReversePayout(roles);
  const [notice, setNotice] = React.useState<string | null>(null);

  const sales = useQuery({
    queryKey: ['lottery-sales', 'management', 'recorded'],
    queryFn: async () => listLotterySales(await getValidAccessToken(), { status: 'RECORDED', size: 25 }),
    enabled: allowed
  });

  const payouts = useQuery({
    queryKey: ['lottery-payouts', 'management', 'paid'],
    queryFn: async () => listLotteryPayouts(await getValidAccessToken(), { status: 'PAID', size: 25 }),
    enabled: allowed
  });

  const cancelMutation = useMutation({
    mutationFn: async ({ sale, reason }: { sale: LotterySale; reason: string }) => cancelLotterySale(
      await getValidAccessToken(),
      sale.id,
      { reason },
      idempotencyKey('lottery-sale-cancel')),
    onSuccess: async (result) => {
      setNotice(`Lottery sale cancelled: ${result.reason}`);
      await queryClient.invalidateQueries({ queryKey: ['lottery-sales', 'management'] });
    }
  });

  const reverseMutation = useMutation({
    mutationFn: async ({ payout, reason }: { payout: LotteryPayout; reason: string }) => reverseLotteryPayout(
      await getValidAccessToken(),
      payout.id,
      { reason },
      idempotencyKey('lottery-payout-reverse')),
    onSuccess: async (result) => {
      setNotice(`Lottery payout reversed: ${result.reason}`);
      await queryClient.invalidateQueries({ queryKey: ['lottery-payouts', 'management'] });
    }
  });

  if (!allowed) {
    return <Alert severity="error">This account cannot manage lottery adjustments.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <HistoryOutlinedIcon color="primary" />
          <Typography variant="h5" component="h1">Lottery management</Typography>
        </Stack>
        <Typography color="text.secondary">Cancel lottery sales and reverse paid lottery payouts with compensating records.</Typography>
      </Box>
      {notice ? <Alert severity="success">{notice}</Alert> : null}
      <Grid container spacing={3}>
        <Grid item xs={12} lg={6}>
          <SaleCancellationPanel
            sales={sales.data?.content ?? []}
            loading={sales.isLoading}
            disabled={cancelMutation.isPending}
            error={sales.error ? errorMessage(sales.error) : cancelMutation.error ? errorMessage(cancelMutation.error) : undefined}
            onCancel={(sale, reason) => cancelMutation.mutate({ sale, reason })}
          />
        </Grid>
        <Grid item xs={12} lg={6}>
          <PayoutReversalPanel
            payouts={payouts.data?.content ?? []}
            loading={payouts.isLoading}
            disabled={reverseMutation.isPending}
            elevated={elevated}
            error={payouts.error ? errorMessage(payouts.error) : reverseMutation.error ? errorMessage(reverseMutation.error) : undefined}
            onReverse={(payout, reason) => reverseMutation.mutate({ payout, reason })}
          />
        </Grid>
      </Grid>
      <Divider />
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Chip label="Original records are preserved" />
        <Chip label="Cash sale cancellation creates CASH_REFUND / OUT" />
        <Chip label="Payout reversal creates LOTTERY_PAYOUT_REVERSAL / IN" />
      </Stack>
    </Stack>
  );
}
