import Dexie, { type Table } from 'dexie';
import type { Sale, SaleItem } from '../../api/types';

const currentDraftId = 'current';

export type DraftCartLine = Pick<
  SaleItem,
  | 'id'
  | 'productId'
  | 'lineNumber'
  | 'productSku'
  | 'productName'
  | 'quantity'
  | 'unitPrice'
  | 'discountAmount'
  | 'priceOverride'
  | 'ageVerified'
  | 'serialNumber'
  | 'externalReference'
  | 'customerId'
  | 'paymentMethodCode'
  | 'lineSubtotal'
  | 'estimatedTaxAmount'
  | 'lineTotal'
  | 'version'
>;

export type DraftCartRecoveryRecord = {
  id: typeof currentDraftId;
  saleId: string;
  storeId: string;
  registerId: string;
  registerSessionId: string;
  createdBy: string;
  customerId: string | null;
  businessDate: string;
  saleChannel: string | null;
  currencyCode: string;
  pricesIncludeTax: boolean;
  subtotalAmount: number;
  discountAmount: number;
  estimatedTaxAmount: number;
  totalAmount: number;
  balanceDue: number;
  items: DraftCartLine[];
  updatedAt: string;
  version: number;
};

class DraftCartRecoveryDatabase extends Dexie {
  draftCarts!: Table<DraftCartRecoveryRecord, string>;

  constructor() {
    super('merchtylDraftCartRecovery');
    this.version(1).stores({
      draftCarts: 'id, saleId, registerSessionId, updatedAt'
    });
  }
}

export const draftCartRecoveryDatabase = new DraftCartRecoveryDatabase();

export function draftCartRecordFromSale(sale: Sale): DraftCartRecoveryRecord | null {
  if (sale.status !== 'DRAFT' || sale.payments.length > 0) {
    return null;
  }

  return {
    id: currentDraftId,
    saleId: sale.id,
    storeId: sale.storeId,
    registerId: sale.registerId,
    registerSessionId: sale.registerSessionId,
    createdBy: sale.createdBy,
    customerId: sale.customerId,
    businessDate: sale.businessDate,
    saleChannel: sale.saleChannel,
    currencyCode: sale.currencyCode,
    pricesIncludeTax: sale.pricesIncludeTax,
    subtotalAmount: sale.subtotalAmount,
    discountAmount: sale.discountAmount,
    estimatedTaxAmount: sale.estimatedTaxAmount,
    totalAmount: sale.totalAmount,
    balanceDue: sale.balanceDue,
    items: sale.items.map((item) => ({
      id: item.id,
      productId: item.productId,
      lineNumber: item.lineNumber,
      productSku: item.productSku,
      productName: item.productName,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      discountAmount: item.discountAmount,
      priceOverride: item.priceOverride,
      ageVerified: item.ageVerified,
      serialNumber: item.serialNumber,
      externalReference: item.externalReference,
      customerId: item.customerId,
      paymentMethodCode: item.paymentMethodCode,
      lineSubtotal: item.lineSubtotal,
      estimatedTaxAmount: item.estimatedTaxAmount,
      lineTotal: item.lineTotal,
      version: item.version
    })),
    updatedAt: sale.updatedAt,
    version: sale.version
  };
}

export function saleFromDraftCartRecord(record: DraftCartRecoveryRecord): Sale {
  return {
    id: record.saleId,
    storeId: record.storeId,
    registerId: record.registerId,
    registerSessionId: record.registerSessionId,
    createdBy: record.createdBy,
    customerId: record.customerId,
    status: 'DRAFT',
    businessDate: record.businessDate,
    saleChannel: record.saleChannel,
    currencyCode: record.currencyCode,
    pricesIncludeTax: record.pricesIncludeTax,
    subtotalAmount: record.subtotalAmount,
    discountAmount: record.discountAmount,
    estimatedTaxAmount: record.estimatedTaxAmount,
    totalAmount: record.totalAmount,
    heldAt: null,
    cancelledAt: null,
    completedBy: null,
    completedAt: null,
    items: record.items.map((item) => ({
      ...item,
      completedProductCost: null,
      completedProductPrice: null,
      completedProductCapabilities: null
    })),
    payments: [],
    paidAmount: 0,
    balanceDue: record.balanceDue,
    changeDue: 0,
    paymentComplete: false,
    createdAt: record.updatedAt,
    updatedAt: record.updatedAt,
    version: record.version
  };
}

export async function saveDraftCartRecovery(sale: Sale) {
  const record = draftCartRecordFromSale(sale);
  if (!record) {
    await clearDraftCartRecovery();
    return;
  }
  await draftCartRecoveryDatabase.draftCarts.put(record);
}

export async function loadDraftCartRecovery(registerSessionId?: string) {
  const record = await draftCartRecoveryDatabase.draftCarts.get(currentDraftId);
  if (!record) {
    return null;
  }
  if (registerSessionId && record.registerSessionId !== registerSessionId) {
    await clearDraftCartRecovery();
    return null;
  }
  return record;
}

export async function clearDraftCartRecovery() {
  await draftCartRecoveryDatabase.draftCarts.delete(currentDraftId);
}
