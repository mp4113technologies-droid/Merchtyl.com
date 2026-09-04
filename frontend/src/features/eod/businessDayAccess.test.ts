import { describe, expect, it } from 'vitest';
import { resolveBusinessDayAccess } from './businessDayAccess';

describe('resolveBusinessDayAccess', () => {
  it('keeps open, close, and reopen as separate permissions', () => {
    expect(resolveBusinessDayAccess(['CASHIER'], ['BUSINESS_DAY_VIEW', 'BUSINESS_DAY_OPEN'])).toEqual({
      canView: true,
      canOpen: true,
      canClose: false,
      canReopen: false
    });
  });

  it('does not infer open access from an operational role when resolved permissions deny it', () => {
    expect(resolveBusinessDayAccess(['CASHIER'], ['BUSINESS_DAY_VIEW']).canOpen).toBe(false);
  });

  it('uses each resolved management permission independently', () => {
    expect(resolveBusinessDayAccess(['MANAGER'], ['BUSINESS_DAY_VIEW', 'BUSINESS_DAY_OPEN', 'BUSINESS_DAY_CLOSE'])).toEqual({
      canView: true,
      canOpen: true,
      canClose: true,
      canReopen: false
    });
  });
});
