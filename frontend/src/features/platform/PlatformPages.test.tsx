import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  EmailDelivery,
  OwnerActivationStatus,
  TenantDetail,
  TenantStatusHistory
} from '../../api/types';

const tenantId = '00000000-0000-0000-0000-000000008201';
const ownerId = '00000000-0000-0000-0000-000000008202';
const invitationId = '00000000-0000-0000-0000-000000008203';
const deliveryId = '00000000-0000-0000-0000-000000008204';

function authResponse(): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'platform-token',
    refreshToken: '',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now).toISOString(),
    userId: '00000000-0000-0000-0000-000000008200',
    email: 'platform@example.local',
    displayName: 'Platform Admin',
    roles: ['PLATFORM_SUPER_ADMIN']
  };
}

function currentUser(): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000008200',
    email: 'platform@example.local',
    displayName: 'Platform Admin',
    roles: ['PLATFORM_SUPER_ADMIN']
  };
}

function tenantDetail(): TenantDetail {
  return {
    tenant: {
      id: tenantId,
      tenantCode: 'ACME',
      legalName: 'Acme Market LLC',
      displayName: 'Acme Market',
      status: 'PENDING_OWNER_ACTIVATION',
      countryCode: 'US',
      administrativeDivisionCode: 'CA',
      defaultCurrencyCode: 'USD',
      primaryTimezone: 'America/Los_Angeles',
      defaultTaxRegionCode: 'CA',
      primaryOwnerEmail: 'owner@example.local',
      subscriptionPlan: 'TRIAL',
      onboardingStage: 'OWNER_INVITATION',
      storeCount: 0,
      userCount: 1,
      createdAt: '2026-08-03T18:00:00Z',
      activatedAt: null,
      suspendedAt: null,
      suspendedByPlatformUserId: null,
      suspensionReason: null,
      closedAt: null,
      closedByPlatformUserId: null,
      closureReason: null,
      reactivatedAt: null,
      reactivatedByPlatformUserId: null,
      version: 0
    },
    merchantProfile: {
      id: '00000000-0000-0000-0000-000000008205',
      tenantId,
      legalBusinessName: 'Acme Market LLC',
      operatingName: 'Acme Market',
      businessNumber: null,
      contactName: 'Owner One',
      contactEmail: 'owner@example.local',
      contactPhone: null,
      billingAddress: null,
      countryCode: 'US',
      administrativeDivisionCode: 'CA',
      defaultCurrencyCode: 'USD',
      primaryTimezone: 'America/Los_Angeles',
      defaultTaxRegionCode: 'CA',
      postalCode: null,
      industryType: null,
      estimatedStoreCount: 1,
      notes: null,
      version: 0
    },
    subscription: {
      id: '00000000-0000-0000-0000-000000008206',
      tenantId,
      planCode: 'TRIAL',
      status: 'TRIAL',
      startsAt: '2026-08-03T18:00:00Z',
      trialEndsAt: null,
      renewsAt: null,
      cancelledAt: null,
      maximumStores: 1,
      maximumUsers: 5,
      features: { pos: true },
      version: 0
    },
    onboarding: {
      tenantId,
      currentStage: 'OWNER_INVITATION',
      completedAt: null,
      stages: [
        { stage: 'MERCHANT_DETAILS', completedAt: '2026-08-03T18:00:00Z' },
        { stage: 'OWNER_ACCOUNT', completedAt: '2026-08-03T18:00:00Z' },
        { stage: 'OWNER_INVITATION', completedAt: null }
      ]
    }
  };
}

function ownerActivation(overrides: Partial<OwnerActivationStatus> = {}): OwnerActivationStatus {
  return {
    tenantId,
    ownerId,
    ownerName: 'Owner One',
    ownerEmail: 'owner@example.local',
    ownerAccountStatus: 'PENDING_ACTIVATION',
    invitationStatus: 'EXPIRED',
    invitationId,
    invitationCreatedAt: '2026-08-01T18:00:00Z',
    invitationExpiresAt: '2026-08-03T18:00:00Z',
    emailProvider: 'RESEND',
    latestEmailDeliveryStatus: 'FAILED',
    latestAttemptAt: '2026-08-03T20:42:00Z',
    emailSentAt: null,
    attemptCount: 2,
    sanitizedFailureMessage: 'Provider rejected the message.',
    activationCompletedAt: null,
    temporaryCredentialsIssuedAt: '2026-08-03T18:00:00Z',
    temporaryCredentialsExpiresAt: '2026-08-04T18:00:00Z',
    credentialsDeliveryStatus: 'SENT',
    firstLoginAt: null,
    passwordChangedAt: null,
    temporaryCredentialsExpired: false,
    activationUrl: null,
    canResend: true,
    canRetry: false,
    retryDeliveryId: null,
    canCopyActivationLink: false,
    canResendTemporaryCredentials: true,
    ...overrides
  };
}

function delivery(): EmailDelivery {
  return {
    id: deliveryId,
    tenantId,
    invitationId,
    recipient: 'owner@example.local',
    templateCode: 'MERCHANT_OWNER_INVITATION_RESEND',
    provider: 'RESEND',
    providerMessageId: null,
    status: 'FAILED',
    attemptCount: 2,
    lastAttemptAt: '2026-08-03T20:42:00Z',
    sentAt: null,
    failedAt: '2026-08-03T20:42:00Z',
    nextRetryAt: null,
    failureCode: 'provider_rejected',
    failureMessageSanitized: 'Provider rejected the message.',
    correlationId: 'correlation-1',
    requestedByPlatformUserId: '00000000-0000-0000-0000-000000008200',
    requestedReason: 'Merchant owner did not receive the original activation email',
    requestedNotes: null,
    createdAt: '2026-08-03T20:42:00Z',
    updatedAt: '2026-08-03T20:42:00Z',
    version: 0
  };
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

describe('Platform merchant owner activation', () => {
  beforeEach(() => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.restoreAllMocks();
  });

  it('shows owner activation status and submits a confirmed resend reason', async () => {
    let resent = false;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.endsWith(`/api/v1/platform/tenants/${tenantId}`)) {
        return jsonResponse(tenantDetail());
      }
      if (url.endsWith(`/api/v1/platform/tenants/${tenantId}/owner-invitation`)) {
        return jsonResponse(resent
          ? ownerActivation({
            invitationStatus: 'SENT',
            invitationExpiresAt: '2026-08-05T18:00:00Z',
            latestEmailDeliveryStatus: 'SENT',
            latestAttemptAt: '2026-08-03T21:00:00Z',
            emailSentAt: '2026-08-03T21:00:00Z',
            attemptCount: 1,
            sanitizedFailureMessage: null
          })
          : ownerActivation());
      }
      if (url.endsWith(`/api/v1/platform/tenants/${tenantId}/status-history`)) {
        return jsonResponse([] satisfies TenantStatusHistory[]);
      }
      if (url.endsWith(`/api/v1/platform/tenants/${tenantId}/email-deliveries`)) {
        return jsonResponse(resent ? [{ ...delivery(), status: 'SENT', providerMessageId: 'resend-message-id', attemptCount: 1 }] : [delivery()]);
      }
      if (url.endsWith(`/api/v1/platform/tenants/${tenantId}/owners/resend-invitation`) && init?.method === 'POST') {
        const body = JSON.parse(String(init.body)) as { reason: string; notes?: string };
        resent = true;
        return jsonResponse({
          tenantId,
          ownerId,
          ownerEmail: 'owner@example.local',
          invitationStatus: 'SENT',
          invitationExpiresAt: '2026-08-05T18:00:00Z',
          delivery: {
            deliveryId,
            provider: 'RESEND',
            providerMessageId: 'resend-message-id',
            status: 'SENT',
            attemptCount: 1,
            lastAttemptAt: '2026-08-03T21:00:00Z'
          },
          receivedReason: body.reason,
          receivedNotes: body.notes
        });
      }
      if (url.includes('/api/v1/platform/tenants?')) {
        return jsonResponse({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true });
      }
      return jsonResponse({ message: `Unexpected request: ${url}` }, 500);
    });

    render(<App initialEntries={[`/platform/merchants/${tenantId}`]} />);

    expect(await screen.findByRole('heading', { name: 'Acme Market' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Owner Activation' })).toBeInTheDocument();
    expect(screen.getAllByText('Provider rejected the message.').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Resend Activation Email' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Copy Activation Link' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Resend Activation Email' }));
    const dialog = await screen.findByRole('dialog', { name: 'Resend Activation Email' });
    expect(within(dialog).getByLabelText('Owner email')).toHaveValue('owner@example.local');
    expect(within(dialog).getByRole('button', { name: 'Resend Activation Email' })).toBeDisabled();

    await userEvent.type(within(dialog).getByRole('textbox', { name: /Reason/ }), 'Merchant owner did not receive the original activation email');
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'I understand the previous unused activation link will stop working' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Resend Activation Email' }));

    expect(await screen.findByText('Activation email sent successfully. A new invitation link was generated and the previous link was invalidated.')).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining(`/api/v1/platform/tenants/${tenantId}/owners/resend-invitation`),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('Merchant owner did not receive the original activation email')
        })
      );
    });
  });
});

describe('Platform merchant server pagination', () => {
  beforeEach(() => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.restoreAllMocks();
  });

  it('requests only page zero with size ten, then requests the next page', async () => {
    const merchantRequests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.includes('/api/v1/reference/countries?')) return jsonResponse([]);
      if (url.includes('/api/v1/platform/tenants?')) {
        merchantRequests.push(url);
        const requestUrl = new URL(url, 'http://localhost');
        const page = Number(requestUrl.searchParams.get('page'));
        return jsonResponse({
          content: [{ ...tenantDetail().tenant, id: `${tenantId.slice(0, -1)}${page + 1}`, displayName: page === 0 ? 'Newest Merchant' : 'Older Merchant' }],
          page,
          size: 10,
          totalElements: 11,
          totalPages: 2,
          first: page === 0,
          last: page === 1
        });
      }
      return jsonResponse({ message: `Unexpected request: ${url}` }, 500);
    });

    render(<App initialEntries={['/platform/merchants']} />);

    expect(await screen.findByText('Newest Merchant')).toBeInTheDocument();
    expect(merchantRequests[0]).toContain('page=0');
    expect(merchantRequests[0]).toContain('size=10');
    expect(merchantRequests[0]).toContain('sort=createdAt%2Cdesc');
    expect(screen.getByRole('button', { name: 'Go to previous page' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Go to next page' }));
    expect(await screen.findByText('Older Merchant')).toBeInTheDocument();
    expect(merchantRequests.at(-1)).toContain('page=1');
    expect(screen.getByRole('button', { name: 'Go to next page' })).toBeDisabled();
  });

  it('debounces search and resets an existing page to zero', async () => {
    const merchantRequests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.includes('/api/v1/reference/countries?')) return jsonResponse([]);
      if (url.includes('/api/v1/platform/tenants?')) {
        merchantRequests.push(url);
        return jsonResponse({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true });
      }
      return jsonResponse({ message: `Unexpected request: ${url}` }, 500);
    });

    render(<App initialEntries={['/platform/merchants?page=5']} />);
    await screen.findByText('No merchants yet.');
    await userEvent.type(screen.getByLabelText('Search merchants...'), 'market');

    await waitFor(() => expect(merchantRequests.some((url) => url.includes('search=market') && url.includes('page=0'))).toBe(true), { timeout: 1500 });
    expect(merchantRequests.filter((url) => url.includes('search=')).length).toBe(1);
  });

  it('keeps the selected country visible and sends its canonical code atomically', async () => {
    const merchantRequests: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.includes('/api/v1/reference/countries?')) return jsonResponse([
        { id: 'country-ca', alpha2Code: 'CA', alpha3Code: 'CAN', name: 'Canada', defaultCurrencyCode: 'CAD', defaultLanguageCode: 'en', active: true, displayOrder: 1 },
        { id: 'country-us', alpha2Code: 'US', alpha3Code: 'USA', name: 'United States', defaultCurrencyCode: 'USD', defaultLanguageCode: 'en', active: true, displayOrder: 2 }
      ]);
      if (url.includes('/api/v1/reference/countries/CA/administrative-divisions?')) return jsonResponse([]);
      if (url.includes('/api/v1/platform/tenants?')) {
        merchantRequests.push(url);
        const requestUrl = new URL(url, 'http://localhost');
        const page = Number(requestUrl.searchParams.get('page'));
        return jsonResponse({ content: [tenantDetail().tenant], page, size: 10, totalElements: 20, totalPages: 2, first: page === 0, last: page === 1 });
      }
      return jsonResponse({ message: `Unexpected request: ${url}` }, 500);
    });

    render(<App initialEntries={['/platform/merchants?page=1&status=ACTIVE&province=NB']} />);
    await screen.findByText('Acme Market');

    await userEvent.click(screen.getByRole('combobox', { name: 'Country' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Canada' }));

    expect(screen.getByRole('combobox', { name: 'Country' })).toHaveTextContent('Canada');
    await waitFor(() => expect(merchantRequests.some((url) => {
      const params = new URL(url, 'http://localhost').searchParams;
      return params.get('country') === 'CA' && params.get('page') === '0' && params.get('status') === 'ACTIVE' && !params.has('province');
    })).toBe(true));

    await userEvent.click(screen.getByRole('button', { name: 'Go to next page' }));
    await waitFor(() => expect(merchantRequests.some((url) => new URL(url, 'http://localhost').searchParams.get('page') === '1'
      && new URL(url, 'http://localhost').searchParams.get('country') === 'CA')).toBe(true));
    expect(screen.getByRole('combobox', { name: 'Country' })).toHaveTextContent('Canada');

    await userEvent.click(screen.getByRole('combobox', { name: 'Country' }));
    await userEvent.click(await screen.findByRole('option', { name: 'All countries' }));
    await waitFor(() => expect(merchantRequests.some((url) => {
      const params = new URL(url, 'http://localhost').searchParams;
      return params.get('page') === '0' && !params.has('country');
    })).toBe(true));
  });
});

describe('Pricing-plan-driven merchant onboarding', () => {
  beforeEach(() => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.restoreAllMocks();
  });

  it('selects an active plan, displays its pricing, and submits pricingPlanId', async () => {
    let submitted: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.includes('/api/v1/reference/countries?')) return jsonResponse([{ id: 'ca', alpha2Code: 'CA', alpha3Code: 'CAN', name: 'Canada', defaultCurrencyCode: 'CAD', defaultLanguageCode: 'en', active: true, displayOrder: 1 }]);
      if (url.includes('/api/v1/reference/countries/CA/administrative-divisions?')) return jsonResponse([{ id: 'nb', countryCode: 'CA', code: 'NB', name: 'New Brunswick', divisionType: 'PROVINCE', defaultTimezone: 'America/Moncton', defaultTaxRegionCode: 'CA-NB', active: true, displayOrder: 1 }]);
      if (url.includes('/api/v1/reference/countries/CA/currencies')) return jsonResponse([{ id: 'cad', code: 'CAD', name: 'Canadian Dollar', symbol: '$', decimalPlaces: 2, active: true, primaryForCountry: true }]);
      if (url.includes('/api/v1/reference/administrative-divisions/nb/timezones')) return jsonResponse([{ id: 'tz', ianaName: 'America/Moncton', displayName: 'Atlantic', countryCode: 'CA', active: true, defaultForDivision: true }]);
      if (url.includes('/api/v1/reference/administrative-divisions/nb/tax-regions')) return jsonResponse([{ id: 'tax', code: 'CA-NB', name: 'New Brunswick', countryCode: 'CA', administrativeDivisionCode: 'NB', active: true, defaultForDivision: true }]);
      if (url.endsWith('/api/v1/platform/billing/plans/options')) return jsonResponse([{
        id: 'plan-growth', code: 'GROWTH', name: 'Growth', description: null, status: 'ACTIVE', billingInterval: 'MONTHLY',
        basePrice: 99, oneTimeOnboardingFee: 199, currency: 'CAD', trialDays: 14, includedStores: 1,
        includedRegisters: null, includedUsers: null, additionalStorePrice: 25, additionalRegisterPrice: null,
        additionalUserPrice: null, taxBehavior: 'EXCLUSIVE', effectiveFrom: '2026-08-01', effectiveTo: null,
        activeMerchants: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z', version: 0
      }]);
      if (url.endsWith('/api/v1/platform/tenants') && init?.method === 'POST') {
        submitted = JSON.parse(String(init.body));
        return jsonResponse(tenantDetail(), 201);
      }
      return jsonResponse({ message: `Unexpected request: ${url}` }, 500);
    });

    render(<App initialEntries={['/platform/merchants/new']} />);
    await userEvent.type(await screen.findByLabelText(/^Legal business name/), 'Acme Market LLC');
    await userEvent.type(screen.getByLabelText(/^Operating name/), 'Acme Market');
    await userEvent.type(screen.getByLabelText(/^Tenant code/), 'ACME');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await userEvent.click(screen.getByRole('combobox', { name: 'Country' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Canada' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Province / Territory' }));
    await userEvent.click(await screen.findByRole('option', { name: 'New Brunswick' }));
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Default currency' })).toHaveTextContent('CAD'));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await userEvent.type(screen.getByLabelText(/^Owner first name/), 'Owner');
    await userEvent.type(screen.getByLabelText(/^Owner last name/), 'One');
    await userEvent.type(screen.getByLabelText(/^Owner email/), 'owner@example.local');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    expect(screen.queryByRole('textbox', { name: 'Plan' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('combobox', { name: 'Subscription Plan' }));
    await userEvent.click(await screen.findByRole('option', { name: /Growth/ }));
    expect(screen.getByText(/Monthly Base:/)).toHaveTextContent('$99.00');
    expect(screen.getByText(/One-Time Onboarding:/)).toHaveTextContent('$199.00');
    expect(screen.getByText(/Additional Store:/)).toHaveTextContent('$25.00');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create merchant' }));

    await waitFor(() => expect(submitted).toBeDefined());
    expect(submitted?.pricingPlanId).toBe('plan-growth');
    expect(submitted).not.toHaveProperty('subscriptionPlan');
  });
});
