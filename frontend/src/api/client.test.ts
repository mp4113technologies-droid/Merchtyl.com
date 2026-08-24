import {
  getDevice,
  getSaleReceipt,
  heartbeatDevice,
  listDevices,
  registerDevice,
  reprintSaleReceipt,
  updateDevice,
  updateDeviceStatus
} from './client';
import type { Device, DeviceListResponse, Receipt } from './types';

function device(overrides: Partial<Device> = {}): Device {
  return {
    id: '00000000-0000-0000-0000-000000000701',
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    deviceIdentifier: 'browser:00000000-0000-4000-8000-000000000001',
    displayName: 'Front counter browser',
    deviceType: 'BROWSER',
    registeredAt: '2026-07-22T12:00:00Z',
    lastSeenAt: '2026-07-22T12:00:00Z',
    active: true,
    version: 0,
    ...overrides
  };
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

function devicePage(content: Device[]): DeviceListResponse {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true
  };
}

function receipt(): Receipt {
  return {
    id: '00000000-0000-0000-0000-000000000940',
    saleId: '00000000-0000-0000-0000-000000000900',
    receiptNumber: 'RCT-2026-07-27-00000000',
    generatedAt: '2026-07-27T12:30:00Z',
    reprintCount: 0,
    lastReprintedAt: null,
    createdAt: '2026-07-27T12:30:00Z',
    updatedAt: '2026-07-27T12:30:00Z',
    version: 0,
    document: {
      brandName: 'Merchtyl',
      brandTagline: 'Point of sale receipt',
      store: {
        id: '00000000-0000-0000-0000-000000000901',
        code: 'MAIN',
        name: 'Main Store',
        legalName: null,
        address: '100 Market Street',
        phone: null,
        email: null
      },
      register: {
        id: '00000000-0000-0000-0000-000000000902',
        code: 'FRONT-1',
        name: 'Front Register'
      },
      cashier: {
        id: '00000000-0000-0000-0000-000000000904',
        displayName: 'Cashier One',
        email: 'cashier@example.local'
      },
      receiptNumber: 'RCT-2026-07-27-00000000',
      saleId: '00000000-0000-0000-0000-000000000900',
      saleNumber: '00000000-0000-0000-0000-000000000900',
      businessDate: '2026-07-27',
      completedAt: '2026-07-27T12:30:00Z',
      currencyCode: 'USD',
      items: [],
      subtotalAmount: 0,
      discountAmount: 0,
      taxSummaries: [],
      taxAmount: 0,
      totalAmount: 0,
      payments: [],
      cashTendered: 0,
      changeDue: 0
    }
  };
}

describe('device API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('lists devices with filters and pagination', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => jsonResponse(devicePage([device()])));

    await expect(listDevices('access-token', {
      storeId: '00000000-0000-0000-0000-000000000901',
      registerId: '00000000-0000-0000-0000-000000000902',
      deviceType: 'BROWSER',
      active: true,
      page: 1,
      size: 25
    })).resolves.toMatchObject({ totalElements: 1 });

    const [input, init] = fetchMock.mock.calls[0];
    const url = new URL(String(input), window.location.origin);
    expect(url.pathname).toBe('/api/v1/devices');
    expect(url.searchParams.get('storeId')).toBe('00000000-0000-0000-0000-000000000901');
    expect(url.searchParams.get('registerId')).toBe('00000000-0000-0000-0000-000000000902');
    expect(url.searchParams.get('deviceType')).toBe('BROWSER');
    expect(url.searchParams.get('active')).toBe('true');
    expect(url.searchParams.get('page')).toBe('1');
    expect(url.searchParams.get('size')).toBe('25');
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer access-token');
  });

  it('calls each device mutation endpoint with the expected method', async () => {
    const current = device();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/devices/register') && init?.method === 'POST') {
        return jsonResponse(current, 201);
      }
      if (url.pathname.endsWith(`/api/v1/devices/${current.id}`) && init?.method === undefined) {
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/devices/${current.id}`) && init?.method === 'PUT') {
        return jsonResponse(device({ version: 1 }));
      }
      if (url.pathname.endsWith(`/api/v1/devices/${current.id}/status`) && init?.method === 'PATCH') {
        return jsonResponse(device({ active: false, version: 2 }));
      }
      if (url.pathname.endsWith(`/api/v1/devices/${current.id}/heartbeat`) && init?.method === 'POST') {
        return jsonResponse(device({ version: 3 }));
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    await registerDevice('access-token', {
      storeId: current.storeId,
      registerId: current.registerId,
      deviceIdentifier: current.deviceIdentifier,
      displayName: current.displayName,
      deviceType: current.deviceType
    });
    await getDevice('access-token', current.id);
    await updateDevice('access-token', current.id, {
      storeId: current.storeId,
      registerId: current.registerId,
      deviceIdentifier: current.deviceIdentifier,
      displayName: 'Updated browser',
      deviceType: current.deviceType,
      active: true,
      version: 0
    });
    await updateDeviceStatus('access-token', current.id, { active: false, version: 1 });
    await heartbeatDevice('access-token', current.id);

    expect(fetchMock.mock.calls.map(([input, init]) => ({
      path: new URL(String(input), window.location.origin).pathname,
      method: init?.method ?? 'GET'
    }))).toEqual([
      { path: '/api/v1/devices/register', method: 'POST' },
      { path: `/api/v1/devices/${current.id}`, method: 'GET' },
      { path: `/api/v1/devices/${current.id}`, method: 'PUT' },
      { path: `/api/v1/devices/${current.id}/status`, method: 'PATCH' },
      { path: `/api/v1/devices/${current.id}/heartbeat`, method: 'POST' }
    ]);
  });

  it('retrieves and reprints sale receipts', async () => {
    const saleId = '00000000-0000-0000-0000-000000000900';
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) {
        return jsonResponse(receipt());
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt/reprint`) && init?.method === 'POST') {
        return jsonResponse({ ...receipt(), reprintCount: 1 });
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    await expect(getSaleReceipt('access-token', saleId)).resolves.toMatchObject({ receiptNumber: 'RCT-2026-07-27-00000000' });
    await expect(reprintSaleReceipt('access-token', saleId)).resolves.toMatchObject({ reprintCount: 1 });

    expect(fetchMock.mock.calls.map(([input, init]) => ({
      path: new URL(String(input), window.location.origin).pathname,
      method: init?.method ?? 'GET'
    }))).toEqual([
      { path: `/api/v1/sales/${saleId}/receipt`, method: 'GET' },
      { path: `/api/v1/sales/${saleId}/receipt/reprint`, method: 'POST' }
    ]);
  });
});
