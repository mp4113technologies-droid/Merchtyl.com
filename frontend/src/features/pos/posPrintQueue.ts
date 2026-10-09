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
        try {
          await job.print();
        } finally {
          await job.cleanup?.();
        }
      }
    });
    this.tail = batch;
    return batch;
  }
}

export const posPrintQueue = new PosPrintQueue();
