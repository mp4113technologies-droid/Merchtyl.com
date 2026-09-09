import { detectDeviceEnvironment, type DeviceNavigator } from './deviceEnvironment';

const device = (userAgent: string, platform = '', maxTouchPoints = 0, mobile?: boolean): DeviceNavigator => ({
  userAgent,
  platform,
  maxTouchPoints,
  userAgentData: mobile === undefined ? undefined : { mobile }
});

describe('detectDeviceEnvironment', () => {
  it.each([
    ['iPhone', device('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', 'iPhone', 5), 'mobile'],
    ['Android phone', device('Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Mobile Safari/537.36', 'Linux armv8l', 5), 'mobile'],
    ['iPad', device('Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X)', 'iPad', 5), 'tablet'],
    ['desktop-mode iPadOS', device('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 Safari/605.1.15', 'MacIntel', 5), 'tablet'],
    ['Android tablet', device('Mozilla/5.0 (Linux; Android 14; SM-X910) AppleWebKit/537.36 Safari/537.36', 'Linux armv8l', 5), 'tablet']
  ])('classifies %s as %s', (_name, input, expected) => {
    const result = detectDeviceEnvironment(input);
    expect(result.isMobile).toBe(expected === 'mobile');
    expect(result.isTablet).toBe(expected === 'tablet');
    expect(result.isDesktop).toBe(false);
  });

  it.each([
    ['Windows', device('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0', 'Win32')],
    ['macOS', device('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) Safari/605.1.15', 'MacIntel')],
    ['Linux', device('Mozilla/5.0 (X11; Linux x86_64) Firefox/142.0', 'Linux x86_64')],
    ['touchscreen Windows', device('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0', 'Win32', 10)]
  ])('allows %s desktop', (_name, input) => {
    expect(detectDeviceEnvironment(input)).toEqual({ isMobile: false, isTablet: false, isDesktop: true });
  });

  it('uses User-Agent Client Hints when a mobile browser exposes them', () => {
    expect(detectDeviceEnvironment(device('Mozilla/5.0 AppleWebKit/537.36', 'Linux armv8l', 5, true)).isMobile).toBe(true);
  });
});
