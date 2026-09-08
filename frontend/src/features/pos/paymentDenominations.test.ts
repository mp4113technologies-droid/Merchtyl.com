import { describe, expect, it } from 'vitest';
import { cashDenominations, centsToInput, decimalInputToCents, moneyToCents } from './paymentDenominations';

describe('cash denomination configuration', () => {
  it('provides Canadian bills and coins without a penny', () => {
    expect(cashDenominations('CAD')).toEqual({
      bills: [
        { cents: 10000, label: '$100' }, { cents: 5000, label: '$50' },
        { cents: 2000, label: '$20' }, { cents: 1000, label: '$10' }, { cents: 500, label: '$5' }
      ],
      coins: [
        { cents: 200, label: '$2' }, { cents: 100, label: '$1' },
        { cents: 25, label: '25¢' }, { cents: 10, label: '10¢' }, { cents: 5, label: '5¢' }
      ]
    });
  });

  it('uses integer cents for manual cash values and unsupported currencies fall back', () => {
    expect(decimalInputToCents('51.75')).toBe(5175);
    expect(moneyToCents(51.75)).toBe(5175);
    expect(centsToInput(5175)).toBe('51.75');
    expect(cashDenominations('EUR')).toBeNull();
  });
});
