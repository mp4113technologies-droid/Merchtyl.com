import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider } from '@mui/material';
import { PosDeviceGuard } from './PosDeviceGuard';
import { theme } from '../../app/theme';

const navigatorDescriptors = {
  userAgent: Object.getOwnPropertyDescriptor(window.navigator, 'userAgent'),
  platform: Object.getOwnPropertyDescriptor(window.navigator, 'platform'),
  maxTouchPoints: Object.getOwnPropertyDescriptor(window.navigator, 'maxTouchPoints')
};

function setDevice(userAgent: string, platform: string, maxTouchPoints: number) {
  Object.defineProperties(window.navigator, {
    userAgent: { configurable: true, value: userAgent },
    platform: { configurable: true, value: platform },
    maxTouchPoints: { configurable: true, value: maxTouchPoints }
  });
}

function renderGuard(path: string) {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/pos/*" element={<PosDeviceGuard><div>Operational POS mounted</div></PosDeviceGuard>} />
          <Route path="/store-menu" element={<h1>Store Menu destination</h1>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
}

afterEach(() => {
  for (const [key, descriptor] of Object.entries(navigatorDescriptors)) {
    if (descriptor) Object.defineProperty(window.navigator, key, descriptor);
  }
});

describe('PosDeviceGuard', () => {
  it.each([
    ['iPhone Retail POS', '/pos', 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', 'iPhone', 5],
    ['iPhone Restaurant POS', '/pos/food', 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', 'iPhone', 5],
    ['Android phone Retail POS', '/pos', 'Mozilla/5.0 (Linux; Android 15; Pixel 9) Mobile Safari/537.36', 'Linux armv8l', 5],
    ['Android phone Restaurant POS', '/pos/food', 'Mozilla/5.0 (Linux; Android 15; Pixel 9) Mobile Safari/537.36', 'Linux armv8l', 5],
    ['iPad Retail POS', '/pos', 'Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X)', 'iPad', 5],
    ['iPad Restaurant POS', '/pos/food', 'Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X)', 'iPad', 5],
    ['Android tablet POS', '/pos', 'Mozilla/5.0 (Linux; Android 14; SM-X910) Safari/537.36', 'Linux armv8l', 5]
  ])('blocks %s before its content mounts', (_name, path, userAgent, platform, touchPoints) => {
    setDevice(userAgent, platform, touchPoints);
    renderGuard(path);

    expect(screen.getByRole('heading', { name: 'POS unavailable on mobile' })).toBeInTheDocument();
    expect(screen.queryByText('Operational POS mounted')).not.toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Merchtyl' })).toHaveAttribute('src', '/branding/Full main.svg');
  });

  it.each([
    ['Windows', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Win32', 0],
    ['macOS', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7)', 'MacIntel', 0],
    ['Linux', 'Mozilla/5.0 (X11; Linux x86_64)', 'Linux x86_64', 0],
    ['Windows touchscreen', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Win32', 10]
  ])('allows %s POS', (_name, userAgent, platform, touchPoints) => {
    setDevice(userAgent, platform, touchPoints);
    renderGuard('/pos');

    expect(screen.getByText('Operational POS mounted')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'POS unavailable on mobile' })).not.toBeInTheDocument();
  });

  it('returns to Store Menu without changing authentication context', async () => {
    setDevice('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', 'iPhone', 5);
    renderGuard('/pos/food');

    await userEvent.click(screen.getByRole('link', { name: 'Back to Store Menu' }));
    expect(screen.getByRole('heading', { name: 'Store Menu destination' })).toBeInTheDocument();
  });
});
