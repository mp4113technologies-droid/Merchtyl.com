import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PlatformBillingOverviewPage, PlatformInvoicesPage, PlatformPricingPlansPage, PlatformSubscriptionsPage } from './PlatformBillingPages';

const api = vi.hoisted(() => ({
  overview: vi.fn(), plans: vi.fn(), invoices: vi.fn(), tenants: vi.fn(), assign: vi.fn(), createPlan: vi.fn(), send: vi.fn(), payment: vi.fn(), voidInvoice: vi.fn(), download: vi.fn()
}));

vi.mock('../../app/session', () => ({ useSession: () => ({ getValidAccessToken: async () => 'token', session: { roles: ['PLATFORM_SUPER_ADMIN'] }, currentUser: { roles: ['PLATFORM_SUPER_ADMIN'], permissions: ['PLATFORM_SUBSCRIPTION_UPDATE'] } }) }));
vi.mock('../../api/client', () => ({
  getPlatformBillingOverview: api.overview,
  listPlatformPricingPlans: api.plans,
  listPlatformInvoices: api.invoices,
  createPlatformPricingPlan: api.createPlan,
  sendPlatformInvoice: api.send,
  recordPlatformInvoicePayment: api.payment,
  voidPlatformInvoice: api.voidInvoice,
  downloadPlatformInvoicePdf: api.download,
  listPlatformTenants: api.tenants,
  assignPlatformBillingSubscription: api.assign,
  getPlatformBillingSettings: vi.fn(),
  updatePlatformBillingSettings: vi.fn(),
  generatePlatformInvoice: vi.fn()
}));

function renderPage(node: React.ReactNode) {
  return render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{node}</QueryClientProvider></MemoryRouter>);
}

describe('platform billing pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.overview.mockResolvedValue({ activeSubscriptions: 42, trialSubscriptions: 6, monthlyRecurringRevenue: 4850, invoicesThisMonth: 42, outstandingBalance: 400, pastDueInvoices: 2, paidThisMonth: 3600, subscriptionsCancelling: 1, currency: 'CAD' });
    api.plans.mockResolvedValue({ content: [{ id: 'plan', code: 'GROWTH', name: 'Growth', description: null, status: 'ACTIVE', billingInterval: 'MONTHLY', basePrice: 99, oneTimeOnboardingFee: 199, currency: 'CAD', trialDays: 14, includedStores: 3, includedRegisters: 5, includedUsers: 15, additionalStorePrice: 20, additionalRegisterPrice: 10, additionalUserPrice: 5, taxBehavior: 'EXCLUSIVE', effectiveFrom: '2026-01-01', effectiveTo: null, activeMerchants: 25, createdAt: '', updatedAt: '', version: 0 }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    api.tenants.mockResolvedValue({ content: [{ id: 'tenant', displayName: 'ABC Convenience', operatingName: 'ABC Convenience' }], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    api.assign.mockResolvedValue({ id: 'subscription' });
    api.invoices.mockResolvedValue({ content: [{ id: 'invoice', invoiceNumber: 'MTL-2026-000001', tenantId: 'tenant', merchantName: 'ABC Convenience', subscriptionId: 'subscription', pricingPlanId: 'plan', planCode: 'GROWTH', billingPeriodStart: '2026-08-01', billingPeriodEnd: '2026-08-31', issueDate: '2026-08-01', dueDate: '2026-08-31', currency: 'CAD', subtotal: 99, discountTotal: 20, taxTotal: 10.27, total: 89.27, amountPaid: 0, amountOutstanding: 89.27, status: 'ISSUED', billingEmail: 'billing@example.com', billingAddress: null, taxLabel: 'Tax', taxRate: 0.13, notes: null, issuedAt: '', sentAt: null, paidAt: null, voidedAt: null, lines: [] }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('renders recurring revenue from backend records with currency formatting', async () => {
    renderPage(<PlatformBillingOverviewPage />);
    expect(await screen.findByText('Monthly Recurring Revenue')).toBeInTheDocument();
    expect(screen.getByText(/4,850/)).toBeInTheDocument();
    expect(screen.getAllByText('42')).toHaveLength(2);
  });

  it('renders configured pricing without hard-coded React prices and opens create plan', async () => {
    renderPage(<PlatformPricingPlansPage />);
    expect(await screen.findByText('Growth')).toBeInTheDocument();
    expect(screen.getByText('CA$99.00')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New Plan' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Create Pricing Plan');
  });

  it('submits permission-gated merchant pricing overrides without changing the plan', async () => {
    renderPage(<PlatformSubscriptionsPage />);
    const selects = await screen.findAllByRole('combobox');
    await userEvent.click(selects[0]);
    await userEvent.click(await screen.findByRole('option', { name: 'ABC Convenience' }));
    await userEvent.click(selects[1]);
    await userEvent.click(await screen.findByRole('option', { name: /Growth/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Customize Pricing' }));
    await userEvent.type(screen.getByLabelText('Custom monthly base price'), '79');
    await userEvent.type(screen.getByLabelText('Custom onboarding fee'), '149');
    await userEvent.type(screen.getByLabelText('Custom additional store price'), '15');
    await userEvent.click(screen.getByRole('button', { name: 'Activate Subscription' }));
    expect(api.assign).toHaveBeenCalledWith('token', 'tenant', expect.objectContaining({
      pricingPlanId: 'plan', customBasePrice: 79, customOnboardingFee: 149, customAdditionalStorePrice: 15
    }));
  });

  it('renders invoice history and exposes send payment download and void actions', async () => {
    renderPage(<PlatformInvoicesPage />);
    expect(await screen.findByText('MTL-2026-000001')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send invoice' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Download invoice' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Record Payment' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Void' })).toBeEnabled();
  });
});
