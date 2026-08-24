import {
  applicationDeviceIdentifierKey,
  getApplicationDeviceIdentifier
} from './deviceIdentity';

const originalCrypto = window.crypto;

function replaceCrypto(crypto: Partial<Crypto>) {
  Object.defineProperty(window, 'crypto', {
    value: crypto,
    configurable: true
  });
}

describe('device identity', () => {
  afterEach(() => {
    window.localStorage.clear();
    Object.defineProperty(window, 'crypto', {
      value: originalCrypto,
      configurable: true
    });
    vi.restoreAllMocks();
  });

  it('generates and stores a random browser device identifier', () => {
    const randomUUID = vi.fn(() => '00000000-0000-4000-8000-000000000001');
    replaceCrypto({ randomUUID } as Partial<Crypto>);

    const identifier = getApplicationDeviceIdentifier();

    expect(identifier).toBe('browser:00000000-0000-4000-8000-000000000001');
    expect(window.localStorage.getItem(applicationDeviceIdentifierKey)).toBe(identifier);
    expect(randomUUID).toHaveBeenCalledTimes(1);
  });

  it('reuses the existing identifier without generating a new one', () => {
    const randomUUID = vi.fn(() => '00000000-0000-4000-8000-000000000002');
    window.localStorage.setItem(applicationDeviceIdentifierKey, 'browser:stored-device');
    replaceCrypto({ randomUUID } as Partial<Crypto>);

    expect(getApplicationDeviceIdentifier()).toBe('browser:stored-device');
    expect(randomUUID).not.toHaveBeenCalled();
  });
});
