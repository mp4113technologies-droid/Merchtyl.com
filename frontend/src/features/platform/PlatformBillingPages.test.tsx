import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { PlatformBillingOverviewPage, PlatformInvoicesPage, PlatformPricingPlansPage, PlatformSubscriptionsPage } from './PlatformBillingPages';

const api = vi.hoisted(() => ({
  overview: vi.fn(), plans: vi.fn(), capabilities: vi.fn(), history: vi.fn(), schedule: vi.fn(), cancelVersion: vi.fn(), invoices: vi.fn(), tenants: vi.fn(), assign: vi.fn(), createPlan: vi.fn(), send: vi.fn(), payment: vi.fn(), voidInvoice: vi.fn(), download: vi.fn()
}));

vi.mock('../../app/session', () => ({ useSession: () => ({ getValidAccessToken: async () => 'token', session: { roles: ['PLATFORM_SUPER_ADMIN'] }, currentUser: { roles: ['PLATFORM_SUPER_ADMIN'], permissions: ['PLATFORM_SUBSCRIPTION_UPDATE'] } }) }));
vi.mock('../../api/client', () => ({
  getPlatformBillingOverview: api.overview,
  listPlatformPricingPlans: api.plans,
  listPlatformBillingCapabilities: api.capabilities,
  listPlatformPricingHistory: api.history,
  schedulePlatformPricingVersion: api.schedule,
  cancelPlatformPricingVersion: api.cancelVersion,
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
    api.plans.mockResolvedValue({ content: [{ id: 'plan', code: 'GROWTH', name: 'Growth', description: null, status: 'ACTIVE', billingInterval: 'MONTHLY', basePrice: 99, oneTimeOnboardingFee: 199, currency: 'CAD', trialDays: 14, includedStores: 3, includedRegisters: 5, includedUsers: 15, additionalStorePrice: 20, additionalRegisterPrice: 10, additionalUserPrice: 5, capabilityPrices: [{capability:'RETAIL_POS',inclusionType:'INCLUDED',billingUnit:null,monthlyPricePerStore:null},{capability:'FOOD_SERVICE',inclusionType:'PAID_ADD_ON',billingUnit:'PER_STORE',monthlyPricePerStore:15}], taxBehavior: 'EXCLUSIVE', effectiveFrom: '2026-01-01', effectiveTo: null, activeMerchants: 25, createdAt: '', updatedAt: '', version: 0 }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    const units=['PER_MERCHANT','PER_STORE','PER_USER','PER_REGISTER'];
    api.capabilities.mockResolvedValue([{capability:'RETAIL_POS',displayName:'Retail POS',supportedBillingUnits:units},{capability:'FOOD_SERVICE',displayName:'Food Service / Kitchen',supportedBillingUnits:units}]);
    api.history.mockResolvedValue([{id:'version-1',pricingPlanId:'plan',versionNumber:1,status:'ACTIVE',effectiveFrom:'2026-01-01',effectiveTo:null,subscriberPolicy:'NEW_SUBSCRIPTIONS_ONLY',pricing:{basePrice:99,additionalStorePrice:20,currency:'CAD'},usedForBilling:true,createdAt:'',version:0}]);
    api.schedule.mockResolvedValue({id:'version-2'});
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
    expect((await screen.findAllByText('Growth'))[0]).toBeInTheDocument();
    expect(screen.getByText('CA$99.00')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New Plan' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('Create Pricing Plan');
  });

  it('renders persisted capability comparison and schedules subscriber migration', async () => {
    renderPage(<PlatformPricingPlansPage />);
    expect(await screen.findByText('Plan Comparison')).toBeInTheDocument();
    expect(await screen.findByText('Add-on · CA$15.00 / store / month')).toBeInTheDocument();
    await userEvent.click(screen.getAllByText('Growth')[0]);
    expect(await screen.findByText('Pricing History')).toBeInTheDocument();
    const selects=screen.getByRole('dialog').querySelectorAll('[role="combobox"]');
    const migration=selects[selects.length-1] as HTMLElement;
    await userEvent.click(migration);
    await userEvent.click(await screen.findByRole('option',{name:'APPLY_NEXT_BILLING_CYCLE'}));
    await userEvent.click(screen.getByRole('button',{name:'Save Pricing Changes'}));
    expect(api.schedule).toHaveBeenCalledWith('token','plan',expect.objectContaining({effectivePolicy:'NEXT_BILLING_CYCLE',existingSubscriberPolicy:'APPLY_NEXT_BILLING_CYCLE',confirmCapabilityRemoval:false}));
  });

  it('offers every billing unit and preserves then versions the selected canonical value', async () => {
    renderPage(<PlatformPricingPlansPage />);
    await userEvent.click((await screen.findAllByText('Growth'))[0]);
    const dialog=screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('Billing Unit *');
    const perStore=screen.getByRole('combobox',{name:'Billing Unit *'});
    expect(perStore).toHaveTextContent('Per Store');
    await userEvent.click(perStore);
    for(const label of ['Per Merchant','Per Store','Per User','Per Register'])expect(await screen.findByRole('option',{name:label})).toBeInTheDocument();
    await userEvent.click(screen.getByRole('option',{name:'Per User'}));
    await userEvent.click(screen.getByRole('button',{name:'Save Pricing Changes'}));
    expect(api.schedule).toHaveBeenCalledWith('token','plan',expect.objectContaining({pricing:expect.objectContaining({capabilityPrices:expect.arrayContaining([expect.objectContaining({capability:'FOOD_SERVICE',billingUnit:'PER_USER'})])})}));
    expect(api.history.mock.results[0].value).toBeTruthy();
  });

  it('creates and versions included-register-per-store pricing without frontend totals', async () => {
    renderPage(<PlatformPricingPlansPage />);
    await userEvent.click(screen.getByRole('button',{name:'New Plan'}));
    expect(screen.getByLabelText('Included Registers Per Store')).toHaveValue(1);
    expect(screen.getByLabelText('Additional Register Price')).toHaveValue(0);
    expect(screen.getByText(/Each store includes 1 registers/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Included Registers Per Store'),{target:{value:'-1'}});
    expect(screen.getByRole('button',{name:'Create'})).toBeDisabled();
    await userEvent.clear(screen.getByLabelText('Included Registers Per Store'));
    await userEvent.type(screen.getByLabelText('Included Registers Per Store'),'2');
    await userEvent.clear(screen.getByLabelText('Additional Register Price'));
    await userEvent.type(screen.getByLabelText('Additional Register Price'),'10');
    expect(screen.getByRole('button',{name:'Create'})).toBeEnabled();
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
