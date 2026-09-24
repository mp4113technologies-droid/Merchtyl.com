import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LotteryReportsPage } from './LotteryReportsPage';

vi.mock('../../app/session', () => ({ useSession: () => ({ getValidAccessToken: async () => 'token' }) }));
vi.mock('../../api/client', () => ({
  listStores: async () => ({ content: [] }), listRegisters: async () => ({ content: [] }), listUsers: async () => ({ content: [] }),
  getLotterySalesReport: async () => ({ storeId: null, registerId: null, cashierId: null, dateFrom: '2026-09-15', dateTo: '2026-09-15',
    type: 'ALL', source: 'ALL', physicalTicketSales: 200, manualLotterySold: 300, totalLotterySold: 500,
    lotteryWins: 125, netLottery: 375, actualCashPayouts: 50, currencyCode: 'CAD', generatedAt: '2026-09-15T15:00:00Z', activities: [
      { occurredAt: '2026-09-15T14:30:00Z', register: 'REG-01', cashier: 'John', type: 'SOLD', source: 'PHYSICAL_TICKET', description: '$5 Scratch Ticket × 2', amount: 10, receiptNumber: 'RCT-1001' },
      { occurredAt: '2026-09-15T14:40:00Z', register: 'REG-02', cashier: 'Mary', type: 'WIN', source: 'MANUAL', description: 'Lottery Win', amount: 20, receiptNumber: 'RCT-1002' }
    ] })
}));

describe('Lottery Sales report', () => {
  it('shows one unified summary and transaction sources', async () => {
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LotteryReportsPage /></QueryClientProvider>);
    expect(await screen.findByRole('heading', { name: 'Lottery Sales' })).toBeVisible();
    expect(await screen.findByText('Physical Ticket Sales')).toBeVisible();
    expect(screen.getByText('Manual Lottery Sold')).toBeVisible();
    expect(screen.getByText('Total Lottery Sold')).toBeVisible();
    expect(screen.getByText('Lottery Wins')).toBeVisible();
    expect(screen.getByText('Net Lottery')).toBeVisible();
    expect(screen.getByText('Actual Cash Payouts')).toBeVisible();
    expect(screen.getByText('$5 Scratch Ticket × 2')).toBeVisible();
    expect(screen.getByText('RCT-1001')).toBeVisible();
    expect(screen.getByText('Physical Ticket')).toBeVisible();
  });
});
