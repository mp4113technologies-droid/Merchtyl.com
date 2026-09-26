import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type { AuthResponse, CurrentUserResponse, PosQuickKey } from '../../api/types';

const userId = '00000000-0000-0000-0000-000000000901';

function authResponse(): AuthResponse {
  const now = Date.now();
  return { accessToken: 'access-token', refreshToken: 'refresh-token', tokenType: 'Bearer', accessTokenExpiresAt: new Date(now + 900_000).toISOString(), refreshTokenExpiresAt: new Date(now + 604_800_000).toISOString(), userId, email: 'owner@example.local', displayName: 'Owner', roles: ['OWNER'] };
}

function currentUser(): CurrentUserResponse {
  return { userId, email: 'owner@example.local', displayName: 'Owner', roles: ['OWNER'], permissions: ['PRODUCT_VIEW', 'PRODUCT_UPDATE'] };
}

function key(id: string, name: string, order: number, available = true): PosQuickKey {
  return { id, productId: `product-${id}`, productVariantId: `variant-${id}`, productName: name, variantName: 'Regular', displayLabel: null, sku: `SKU-${id}`, price: 5, sellableType: 'STANDARD_PRODUCT', ageRestricted: false, minimumAge: null, active: true, productAvailable: available, displayOrder: order, version: 0 };
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('PosQuickKeysPage', () => {
  beforeEach(() => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.restoreAllMocks();
  });

  it('renders merchant configuration, flags unavailable products, and persists deterministic reordering', async () => {
    let configured = [key('coffee', 'Coffee', 0), key('milk', 'Milk', 1, false)];
    let updateBody: Record<string, unknown> | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.pathname.endsWith('/api/v1/pos/quick-keys/configuration') && !init?.method) return jsonResponse(configured);
      if (url.pathname.endsWith('/api/v1/pos/quick-keys/configuration/milk') && init?.method === 'PUT') {
        updateBody = JSON.parse(String(init.body));
        configured = [configured[1], configured[0]].map((item, displayOrder) => ({ ...item, displayOrder }));
        return jsonResponse(configured[0]);
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    render(<App initialEntries={['/settings/pos-quick-keys']} />);
    expect(await screen.findByRole('heading', { name: 'Retail POS Quick Keys' })).toBeVisible();
    expect(await screen.findByText('2 / 20')).toBeVisible();
    expect(await screen.findByText(/Product unavailable/)).toBeVisible();
    expect(screen.getByRole('checkbox', { name: 'Enable Milk Regular' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Move Milk up' }));
    await waitFor(() => expect(updateBody).toMatchObject({ displayOrder: 0, active: true }));
    await waitFor(() => {
      const rows = screen.getAllByText(/Coffee — Regular|Milk — Regular/).map(element => element.textContent);
      expect(rows).toEqual(['Milk — Regular', 'Coffee — Regular']);
    });
  });

  it('shows a clean configuration empty state', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.pathname.endsWith('/api/v1/pos/quick-keys/configuration')) return jsonResponse([]);
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });
    render(<App initialEntries={['/settings/pos-quick-keys']} />);
    expect(await screen.findByText('No quick products configured.')).toBeVisible();
    expect(within(screen.getByRole('main')).getByRole('textbox', { name: 'Find product variant' })).toBeVisible();
  });
});
