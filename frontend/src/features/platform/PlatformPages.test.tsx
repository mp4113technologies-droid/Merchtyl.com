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
