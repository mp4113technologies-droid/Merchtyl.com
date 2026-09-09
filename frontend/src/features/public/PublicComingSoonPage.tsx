import AnalyticsOutlinedIcon from '@mui/icons-material/AnalyticsOutlined';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined';
import { Box, Button, Stack, Typography } from '@mui/material';
import { keyframes } from '@mui/material/styles';
import { useEffect } from 'react';
import { MerchtylLogo } from '../../app/MerchtylLogo';
import { merchtylTokens } from '../../app/theme';

const rise = keyframes`from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: translateY(0); }`;
const float = keyframes`from { transform: translateY(-4px) rotate(var(--rotation)); } to { transform: translateY(7px) rotate(calc(var(--rotation) + 2deg)); }`;
const drift = keyframes`from { transform: translate3d(-1%, -1%, 0) scale(.98); } to { transform: translate3d(2%, 2%, 0) scale(1.03); }`;
const blink = keyframes`0%, 45% { opacity: 1; } 46%, 100% { opacity: 0; }`;

const reducedMotion = {
  '@media (prefers-reduced-motion: reduce)': {
    animation: 'none',
    transition: 'none'
  }
} as const;

const features = [
  { label: 'Modern Commerce', Icon: StorefrontOutlinedIcon },
  { label: 'Smarter Operations', Icon: AnalyticsOutlinedIcon },
  { label: 'Growing Merchants', Icon: GroupsOutlinedIcon }
];

function FloatingDecorations() {
  const cards = [
    { label: 'Commerce analytics', Icon: AnalyticsOutlinedIcon, top: '17%', left: '7%', rotation: '-7deg', duration: '7s' },
    { label: 'Connected storefront', Icon: StorefrontOutlinedIcon, top: '14%', right: '7%', rotation: '6deg', duration: '8.5s' },
    { label: 'Receipt operations', Icon: ReceiptLongOutlinedIcon, bottom: '13%', left: '10%', rotation: '5deg', duration: '6.5s' },
    { label: 'Merchant growth', Icon: GroupsOutlinedIcon, bottom: '14%', right: '9%', rotation: '-6deg', duration: '9s' }
  ];

  return <Box aria-hidden="true" sx={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none' }}>
    <Box sx={{ position: 'absolute', width: 420, height: 420, borderRadius: '50%', bgcolor: merchtylTokens.colors.blueLight, filter: 'blur(28px)', opacity: .7, top: -220, right: -100, animation: `${drift} 14s ease-in-out infinite alternate`, ...reducedMotion }} />
    <Box sx={{ position: 'absolute', width: 360, height: 360, borderRadius: '45%', bgcolor: merchtylTokens.colors.blueLight, filter: 'blur(38px)', opacity: .55, bottom: -220, left: -120, animation: `${drift} 17s ease-in-out infinite alternate-reverse`, ...reducedMotion }} />
    <Box component="svg" viewBox="0 0 1200 700" preserveAspectRatio="none" sx={{ position: 'absolute', inset: '5% 0', width: '100%', height: '90%', opacity: .24 }}>
      <path d="M-40 480 C 220 260, 350 620, 650 370 S 1020 100, 1260 260" fill="none" stroke={merchtylTokens.colors.blue} strokeWidth="1.5" />
      <circle cx="260" cy="404" r="5" fill={merchtylTokens.colors.blue} />
      <circle cx="784" cy="293" r="4" fill={merchtylTokens.colors.blue} />
      <circle cx="1050" cy="166" r="6" fill={merchtylTokens.colors.blue} />
    </Box>
    {cards.map(({ label, Icon, rotation, duration, ...position }) => <Box
      key={label}
      title={label}
      sx={{
        position: 'absolute', ...position, '--rotation': rotation,
        width: 64, height: 64, display: { xs: 'none', md: 'grid' }, placeItems: 'center',
        color: merchtylTokens.colors.blue, bgcolor: 'rgba(255,255,255,.78)',
        border: `1px solid ${merchtylTokens.colors.border}`, borderRadius: '20px',
        boxShadow: '0 16px 44px rgba(8,47,87,.10)', backdropFilter: 'blur(10px)',
        animation: `${float} ${duration} ease-in-out infinite alternate`, ...reducedMotion
      }}
    ><Icon sx={{ fontSize: 28 }} /></Box>)}
  </Box>;
}

export function PublicComingSoonPage() {
  useEffect(() => {
    document.title = 'Merchtyl — Coming Soon';
    let description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
    if (!description) {
      description = document.createElement('meta');
      description.name = 'description';
      document.head.appendChild(description);
    }
    description.content = 'Modern commerce operations for growing merchants.';
  }, []);

  return <Box sx={{
    position: 'relative', isolation: 'isolate', minHeight: '100dvh', width: '100%', overflow: 'hidden',
    display: 'grid', placeItems: 'center', px: { xs: 2.5, sm: 4 }, py: { xs: 4, md: 3 },
    background: `linear-gradient(145deg, ${merchtylTokens.colors.card} 12%, ${merchtylTokens.colors.blueSoft} 58%, ${merchtylTokens.colors.card} 100%)`
  }}>
    <FloatingDecorations />
    <Stack component="main" alignItems="center" textAlign="center" sx={{ position: 'relative', zIndex: 1, width: '100%', maxWidth: 980 }}>
      <Box sx={{ animation: `${rise} .55s ease-out both`, ...reducedMotion }}>
        <Box sx={{ p: 1.5, borderRadius: 4, background: 'radial-gradient(circle, rgba(20,115,230,.10), transparent 68%)' }}>
          <MerchtylLogo size="large" sx={{ width: { xs: 190, sm: 230 } }} />
        </Box>
      </Box>
      <Typography sx={{ mt: 1, color: merchtylTokens.colors.navy, fontSize: { xs: 11, sm: 12 }, fontWeight: 800, letterSpacing: { xs: '.13em', sm: '.2em' }, animation: `${rise} .55s .12s ease-out both`, ...reducedMotion }}>
        MODERN COMMERCE. REAL POSSIBILITIES.
      </Typography>
      <Typography component="h1" sx={{ mt: { xs: 3, md: 3.5 }, color: merchtylTokens.colors.navyDark, fontSize: { xs: 44, sm: 58, md: 68 }, fontWeight: 800, letterSpacing: '-.045em', lineHeight: 1, animation: `${rise} .6s .22s ease-out both`, ...reducedMotion }}>
        Coming <Box component="span" sx={{ color: merchtylTokens.colors.blue }}>Soon</Box><Box component="span" aria-hidden="true" sx={{ display: 'inline-block', width: 3, height: '.8em', ml: .75, bgcolor: merchtylTokens.colors.blue, borderRadius: 4, animation: `${blink} 1.15s .9s infinite`, ...reducedMotion }} />
      </Typography>
      <Typography sx={{ mt: 2.25, maxWidth: 620, color: merchtylTokens.colors.textSecondary, fontSize: { xs: 16, sm: 18 }, lineHeight: 1.6, animation: `${rise} .6s .34s ease-out both`, ...reducedMotion }}>
        We're building something great. Our new website is on the way and will be live soon.
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 1.25, sm: 4.5 }} sx={{ mt: { xs: 3, sm: 4 }, width: { xs: '100%', sm: 'auto' } }}>
        {features.map(({ label, Icon }, index) => <Stack key={label} direction="row" alignItems="center" spacing={1.1} sx={{ justifyContent: { xs: 'center', sm: 'flex-start' }, animation: `${rise} .5s ${.44 + index * .1}s ease-out both`, ...reducedMotion, '&:hover .feature-icon': { transform: 'translateY(-2px) scale(1.04)', bgcolor: merchtylTokens.colors.blueLight } }}>
          <Box className="feature-icon" sx={{ display: 'grid', placeItems: 'center', width: 38, height: 38, borderRadius: 2.5, bgcolor: merchtylTokens.colors.blueLight, color: merchtylTokens.colors.blue, border: `1px solid ${merchtylTokens.colors.border}`, transition: '180ms ease', ...reducedMotion }}><Icon fontSize="small" /></Box>
          <Typography sx={{ color: merchtylTokens.colors.navy, fontSize: 14, fontWeight: 700 }}>{label}</Typography>
        </Stack>)}
      </Stack>
      <Button href="mailto:mp4113technologies@gmail.com?subject=Merchtyl%20demo%20request" variant="contained" endIcon={<ArrowForwardRoundedIcon />} sx={{ mt: { xs: 3.5, sm: 4.5 }, px: 3.5, minHeight: 48, borderRadius: 99, boxShadow: '0 10px 28px rgba(20,115,230,.24)', animation: `${rise} .55s .78s ease-out both`, transition: 'transform 180ms ease, box-shadow 180ms ease', ...reducedMotion, '&:hover': { transform: 'translateY(-1px) scale(1.01)', boxShadow: '0 14px 34px rgba(20,115,230,.30)', '& .MuiButton-endIcon': { transform: 'translateX(3px)' } }, '& .MuiButton-endIcon': { transition: 'transform 180ms ease' } }}>
        Request a demo
      </Button>
      <Typography sx={{ mt: { xs: 3.5, sm: 4.5 }, color: merchtylTokens.colors.textSecondary, fontSize: 10, fontWeight: 700, letterSpacing: { xs: '.15em', sm: '.25em' }, animation: `${rise} .5s .95s ease-out both`, ...reducedMotion }}>
        PEOPLE • PRODUCTS • POSSIBILITIES
      </Typography>
    </Stack>
  </Box>;
}
