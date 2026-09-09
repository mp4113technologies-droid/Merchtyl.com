import manifestText from '../../public/manifest.json?raw';
import offlineShell from '../../public/offline.html?raw';
import serviceWorker from '../../public/sw.js?raw';

describe('PWA public assets', () => {
  it('defines an installable manifest with official size-matched app icons', () => {
    const manifest = JSON.parse(manifestText) as {
      display: string;
      start_url: string;
      icons: Array<{ src: string; sizes: string; purpose?: string }>;
    };

    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    expect(manifest.icons).toEqual(expect.arrayContaining([
      expect.objectContaining({ src: '/branding/256_256.png', sizes: '256x256' }),
      expect.objectContaining({ src: '/branding/512_512.png', sizes: '512x512' })
    ]));
  });

  it('provides an offline shell that excludes checkout and live register actions', () => {
    expect(offlineShell).toContain('Merchtyl is offline');
    expect(offlineShell).toContain('/branding/Full main.svg');
    expect(offlineShell).toContain('checkout');
    expect(offlineShell).toContain('require a connection');
  });

  it('keeps API calls network-only while caching the app shell', () => {
    expect(serviceWorker).toContain("'/offline.html'");
    expect(serviceWorker).toContain("'SKIP_WAITING'");
    expect(serviceWorker).toContain("url.pathname.startsWith('/api/')");
    expect(serviceWorker).toContain("request.mode === 'navigate'");
    expect(serviceWorker).toContain('return;');
  });
});
