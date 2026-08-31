import type { SxProps, Theme } from '@mui/material/styles';

export const compactFilterBarSx: SxProps<Theme> = {
  p: 2,
  display: 'grid',
  gridTemplateColumns: {
    xs: 'minmax(0, 1fr)',
    sm: 'repeat(2, minmax(0, 1fr))',
    md: 'repeat(3, minmax(0, 1fr))',
    xl: 'repeat(6, minmax(0, 1fr))'
  },
  gap: 2,
  '& > *': {
    width: '100%',
    minWidth: '0 !important'
  }
};
