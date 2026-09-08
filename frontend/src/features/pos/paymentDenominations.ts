export type CashDenomination = { label: string; cents: number };
export type CurrencyDenominations = { bills: CashDenomination[]; coins: CashDenomination[] };

const DENOMINATIONS: Record<string, CurrencyDenominations> = {
  CAD: {
    bills: [
      { label: '$100', cents: 10_000 }, { label: '$50', cents: 5_000 },
      { label: '$20', cents: 2_000 }, { label: '$10', cents: 1_000 }, { label: '$5', cents: 500 }
    ],
    coins: [
      { label: '$2', cents: 200 }, { label: '$1', cents: 100 },
      { label: '25¢', cents: 25 }, { label: '10¢', cents: 10 }, { label: '5¢', cents: 5 }
    ]
  },
  USD: {
    bills: [
      { label: '$100', cents: 10_000 }, { label: '$50', cents: 5_000 },
      { label: '$20', cents: 2_000 }, { label: '$10', cents: 1_000 }, { label: '$5', cents: 500 },
      { label: '$1', cents: 100 }
    ],
    coins: [
      { label: '25¢', cents: 25 }, { label: '10¢', cents: 10 },
      { label: '5¢', cents: 5 }, { label: '1¢', cents: 1 }
    ]
  }
};

export function cashDenominations(currencyCode: string) {
  return DENOMINATIONS[currencyCode.toUpperCase()] ?? null;
}

export function moneyToCents(value: number) {
  return Number.isFinite(value) ? Math.max(0, Math.round(value * 100)) : 0;
}

export function decimalInputToCents(value: string) {
  const normalized = value.trim();
  if (!/^\d*(?:\.\d{0,2})?$/.test(normalized) || normalized === '' || normalized === '.') return null;
  const [whole = '0', fraction = ''] = normalized.split('.');
  return Number.parseInt(whole || '0', 10) * 100 + Number.parseInt(fraction.padEnd(2, '0') || '0', 10);
}

export function centsToInput(cents: number) {
  return (cents / 100).toFixed(2);
}
