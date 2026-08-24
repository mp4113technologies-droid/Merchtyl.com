import type { Payment, Sale, SaleItem } from '../../api/types';
import {
  clearDraftCartRecovery,
  draftCartRecordFromSale,
  loadDraftCartRecovery,
  saleFromDraftCartRecord,
  saveDraftCartRecovery
} from './draftCartRecovery';

const saleId = '00000000-0000-0000-0000-000000000001';
const sessionId = '00000000-0000-0000-0000-000000000002';
const itemId = '00000000-0000-0000-0000-000000000003';

function item(overrides: Partial<SaleItem> = {}): SaleItem {
  return {
    id: itemId,
    productId: '00000000-0000-0000-0000-000000000004',
    lineNumber: 1,
    productSku: 'COFFEE',
    productName: 'Coffee',
    quantity: 2,
    unitPrice: 5,
    discountAmount: 1,
    completedProductCost: null,
    completedProductPrice: null,
    completedProductCapabilities: null,
    priceOverride: false,
    ageVerified: false,
    serialNumber: null,
    externalReference: null,
    customerId: '00000000-0000-0000-0000-000000000005',
    paymentMethodCode: null,
    lineSubtotal: 10,
    estimatedTaxAmount: 1.35,
    lineTotal: 10.35,
    version: 0,
    ...overrides
  };
}

function payment(): Payment {
  return {
    id: '00000000-0000-0000-0000-000000000006',
    method: 'CASH',
    amount: 10.35,
    currencyCode: 'USD',
    cashTendered: 20,
    changeDue: 9.65,
    reference: null,
    notes: null,
    createdBy: '00000000-0000-0000-0000-000000000007',
    completedAt: '2026-07-29T12:00:00Z',
    createdAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function sale(overrides: Partial<Sale> = {}): Sale {
  return {
    id: saleId,
    storeId: '00000000-0000-0000-0000-000000000008',
    registerId: '00000000-0000-0000-0000-000000000009',
    registerSessionId: sessionId,
    createdBy: '00000000-0000-0000-0000-000000000007',
    customerId: '00000000-0000-0000-0000-000000000005',
    status: 'DRAFT',
    businessDate: '2026-07-29',
    saleChannel: 'POS',
    currencyCode: 'USD',
    pricesIncludeTax: false,
    subtotalAmount: 10,
    discountAmount: 1,
    estimatedTaxAmount: 1.35,
    totalAmount: 10.35,
    heldAt: null,
    cancelledAt: null,
    completedBy: null,
    completedAt: null,
    items: [item()],
    payments: [],
    paidAmount: 0,
    balanceDue: 10.35,
    changeDue: 0,
    paymentComplete: false,
    createdAt: '2026-07-29T11:55:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 3,
    ...overrides
  };
}

describe('draft cart recovery', () => {
  beforeEach(async () => {
    await clearDraftCartRecovery();
  });

  it('persists only draft cart, customer selection, and discounts', async () => {
    await saveDraftCartRecovery(sale());

    const record = await loadDraftCartRecovery(sessionId);

    expect(record).toEqual(expect.objectContaining({
      saleId,
      registerSessionId: sessionId,
      customerId: '00000000-0000-0000-0000-000000000005',
      discountAmount: 1
    }));
    expect(record?.items).toEqual([expect.objectContaining({
      productSku: 'COFFEE',
      quantity: 2,
      discountAmount: 1,
      customerId: '00000000-0000-0000-0000-000000000005'
    })]);
    expect(JSON.stringify(record)).not.toContain('payments');
    expect(saleFromDraftCartRecord(record!).status).toBe('DRAFT');
  });

  it('does not create recovery records for paid or completed sales', async () => {
    await saveDraftCartRecovery(sale());
    await saveDraftCartRecovery(sale({ payments: [payment()], paidAmount: 10.35, balanceDue: 0, paymentComplete: true }));

    expect(await loadDraftCartRecovery(sessionId)).toBeNull();

    await saveDraftCartRecovery(sale());
    await saveDraftCartRecovery(sale({
      status: 'COMPLETED',
      payments: [payment()],
      paidAmount: 10.35,
      balanceDue: 0,
      paymentComplete: true,
      completedBy: '00000000-0000-0000-0000-000000000007',
      completedAt: '2026-07-29T12:05:00Z'
    }));

    expect(draftCartRecordFromSale(sale({ status: 'COMPLETED' }))).toBeNull();
    expect(await loadDraftCartRecovery(sessionId)).toBeNull();
  });
});
