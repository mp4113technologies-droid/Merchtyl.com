import CasinoOutlinedIcon from '@mui/icons-material/CasinoOutlined';
import DiscountOutlinedIcon from '@mui/icons-material/DiscountOutlined';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import PriceCheckOutlinedIcon from '@mui/icons-material/PriceCheckOutlined';
import RemoveShoppingCartOutlinedIcon from '@mui/icons-material/RemoveShoppingCartOutlined';
import ReplayOutlinedIcon from '@mui/icons-material/ReplayOutlined';
import { Button, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { posTokens } from '../../app/theme';

export type PosQuickAction = {
  key: string;
  label: string;
  icon: ReactNode;
  onClick?: () => void;
  to?: string;
  disabled?: boolean;
  tone?: 'default' | 'warning' | 'danger';
};

export const posActionIcons = {
  discount: <DiscountOutlinedIcon />,
  hold: <PauseCircleOutlineIcon />,
  lottery: <CasinoOutlinedIcon />,
  payout: <PaymentsOutlinedIcon />,
  priceCheck: <PriceCheckOutlinedIcon />,
  recall: <PlayCircleOutlineIcon />,
  return: <ReplayOutlinedIcon />,
  taxable: <PriceCheckOutlinedIcon />,
  noTax: <LockOpenOutlinedIcon />,
  void: <RemoveShoppingCartOutlinedIcon />
};

function PosActionButton({ action }: { action: PosQuickAction }) {
  const color = action.tone === 'danger' ? 'error' : action.tone === 'warning' ? 'warning' : 'primary';
  return (
    <Button
      component={action.to ? Link : 'button'}
      to={action.to}
      onClick={action.onClick}
      disabled={action.disabled}
      color={color}
      variant={action.tone ? 'outlined' : 'contained'}
      startIcon={action.icon}
      sx={{
        minHeight: 48,
        px: 1.25,
        justifyContent: 'flex-start',
        whiteSpace: 'nowrap',
        fontSize: 13,
        fontWeight: 800,
        boxShadow: action.tone ? 'none' : '0 2px 5px rgba(10, 61, 98, 0.18)',
        bgcolor: action.tone ? posTokens.colors.card : undefined
      }}
    >
      {action.label}
    </Button>
  );
}

export function PosQuickActions({ actions }: { actions: PosQuickAction[] }) {
  if (actions.length === 0) return null;
  return (
    <Paper
      component="section"
      aria-labelledby="pos-quick-actions-title"
      variant="outlined"
      sx={{ p: 0.75, flexShrink: 0, borderColor: posTokens.colors.border, borderRadius: `${posTokens.radius.card}px` }}
    >
      <Stack spacing={0.5}>
        <Typography id="pos-quick-actions-title" variant="overline" color="text.secondary" fontWeight={800} lineHeight={1.2}>
          Quick actions
        </Typography>
        <Stack
          direction="row"
          sx={{
            display: 'grid',
            gridTemplateColumns: {
              xs: 'repeat(4, minmax(0, 1fr))',
              md: 'repeat(5, minmax(0, 1fr))',
              xl: 'repeat(10, minmax(0, 1fr))'
            },
            gap: 0.75
          }}
        >
          {actions.map((action) => <PosActionButton key={action.key} action={action} />)}
        </Stack>
      </Stack>
    </Paper>
  );
}
