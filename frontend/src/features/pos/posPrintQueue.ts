export type PosPrintDocumentType = 'RETAIL_RECEIPT' | 'KITCHEN_TICKET' | 'CUSTOMER_RECEIPT';

export type PosPrintJob = {
  transactionId: string;
  type: PosPrintDocumentType;
  print: () => Promise<void>;
  cleanup?: () => void | Promise<void>;
};

export class PosPrintQueue {
  private tail: Promise<void> = Promise.resolve();

  printMany(jobs: PosPrintJob[]) {
    const batch = this.tail.catch(() => undefined).then(async () => {
      for (const job of jobs) {
        if (import.meta.env.DEV) console.debug('POS_PRINT_STARTED', { transactionId: job.transactionId, documentType: job.type });
        try {
          await job.print();
        } finally {
          await job.cleanup?.();
          if (import.meta.env.DEV) console.debug('POS_PRINT_CLEANED_UP', { transactionId: job.transactionId, documentType: job.type });
        }
      }
    });
    this.tail = batch;
    return batch;
  }
}

export const posPrintQueue = new PosPrintQueue();
