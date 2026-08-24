import {
  KeyboardWedgeScanner,
  normalizeBarcodeScannerPreferences
} from './barcodeScanner';

function event(key: string) {
  return {
    key,
    preventDefault: vi.fn(),
    stopPropagation: vi.fn()
  };
}

describe('KeyboardWedgeScanner', () => {
  it('detects fast keyboard-wedge scans by timing and suffix', () => {
    let now = 0;
    const scanner = new KeyboardWedgeScanner({
      minLength: 4,
      suffix: 'Enter',
      maxInterKeyDelayMs: 45,
      duplicatePreventionMs: 1500,
      now: () => now
    });

    for (const key of ['1', '2', '3', '4', '5']) {
      now += 12;
      expect(scanner.handleKeyDown(event(key))).toBeNull();
    }
    now += 12;
    const suffix = event('Enter');

    expect(scanner.handleKeyDown(suffix)).toEqual({ type: 'scan', value: '12345' });
    expect(suffix.preventDefault).toHaveBeenCalledOnce();
    expect(suffix.stopPropagation).toHaveBeenCalledOnce();
  });

  it('ignores slow manual typing so forms can submit normally', () => {
    let now = 0;
    const scanner = new KeyboardWedgeScanner({
      minLength: 4,
      maxInterKeyDelayMs: 45,
      now: () => now
    });

    for (const key of ['1', '2', '3', '4', '5']) {
      now += 150;
      scanner.handleKeyDown(event(key));
    }
    const suffix = event('Enter');

    expect(scanner.handleKeyDown(suffix)).toBeNull();
    expect(suffix.preventDefault).not.toHaveBeenCalled();
  });

  it('prevents duplicate scans inside the configured window', () => {
    let now = 0;
    const scanner = new KeyboardWedgeScanner({
      minLength: 4,
      duplicatePreventionMs: 1000,
      now: () => now
    });

    function scan(value: string) {
      for (const key of value) {
        now += 10;
        scanner.handleKeyDown(event(key));
      }
      now += 10;
      return scanner.handleKeyDown(event('Enter'));
    }

    expect(scan('12345')).toEqual({ type: 'scan', value: '12345' });
    now += 300;
    expect(scan('12345')).toEqual({ type: 'duplicate', value: '12345' });
    now += 1200;
    expect(scan('12345')).toEqual({ type: 'scan', value: '12345' });
  });

  it('normalizes scanner settings into supported bounds', () => {
    expect(normalizeBarcodeScannerPreferences({
      minLength: -4,
      suffix: 'Tab',
      maxInterKeyDelayMs: 999,
      duplicatePreventionMs: -1
    })).toMatchObject({
      minLength: 1,
      suffix: 'Tab',
      maxInterKeyDelayMs: 250,
      duplicatePreventionMs: 0
    });
  });
});
