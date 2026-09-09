import { Box, type SxProps, type Theme } from '@mui/material';
import { brandLogoSource, type BrandLogoVariant } from './brandAssets';

export type MerchtylLogoSize = 'small' | 'medium' | 'large' | 'icon';

const widths: Record<MerchtylLogoSize, number> = { small: 150, medium: 210, large: 220, icon: 30 };

export function MerchtylLogo({ variant = 'primary', size = 'medium', decorative = false, sx }: {
  variant?: BrandLogoVariant;
  size?: MerchtylLogoSize;
  decorative?: boolean;
  sx?: SxProps<Theme>;
}) {
  return <Box component="img" src={brandLogoSource(variant)} alt={decorative ? '' : 'Merchtyl'}
    aria-hidden={decorative || undefined} sx={{ display: 'block', width: widths[size], maxWidth: '100%',
      height: size === 'icon' ? widths.icon : 'auto', aspectRatio: size === 'icon' ? '1 / 1' : '754 / 140',
      objectFit: 'contain', ...sx }} />;
}
