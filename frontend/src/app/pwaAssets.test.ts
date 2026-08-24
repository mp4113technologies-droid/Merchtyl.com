import manifestText from '../../public/manifest.json?raw';
import offlineShell from '../../public/offline.html?raw';
import serviceWorker from '../../public/sw.js?raw';

describe('PWA public assets', () => {
  it('defines an installable manifest with generated app icons', () => {
    const manifest = JSON.parse(manifestText) as {
      display: string;
      start_url: string;
      icons: Array<{ src: string; sizes: string; purpose?: string }>;
    };

    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    expect(manifest.icons).toEqual(expect.arrayContaining([
      expect.objectContaining({ src: '/icon.svg', sizes: 'any' }),
      expect.objectContaining({ src: '/icon-192.svg', sizes: '192x192', purpose: 'any maskable' }),
      expect.objectContaining({ src: '/icon-512.svg', sizes: '512x512', purpose: 'any maskable' })
    ]));
  });

  it('provides an offline shell that excludes checkout and live register actions', () => {
    expect(offlineShell).toContain('Merchtyl is offline');
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
