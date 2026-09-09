export const brandAssets = {
  primaryLogo: '/branding/Full main.svg',
  blackLogo: '/branding/Full black.svg',
  whiteLogo: '/branding/Full white.svg',
  icon: '/branding/512_512.svg',
  favicon: '/branding/32_32.svg',
  faviconPng: '/branding/32_32.png',
  appleTouchIcon: '/branding/128_128.png',
  pwaIcon256: '/branding/256_256.png',
  pwaIcon512: '/branding/512_512.png'
} as const;

export type BrandLogoVariant = 'primary' | 'black' | 'white' | 'icon';

export function brandLogoSource(variant: BrandLogoVariant) {
  if (variant === 'black') return brandAssets.blackLogo;
  if (variant === 'white') return brandAssets.whiteLogo;
  if (variant === 'icon') return brandAssets.icon;
  return brandAssets.primaryLogo;
}
