import { PosPrintQueue } from './posPrintQueue';

describe('PosPrintQueue', () => {
  it('prints kitchen then customer sequentially and cleans between jobs', async () => {
    const queue = new PosPrintQueue();
    const events: string[] = [];
    let active = 0;
    const job = (type: 'KITCHEN_TICKET' | 'CUSTOMER_RECEIPT') => ({
      transactionId: 'order-123', type,
      print: async () => {
        expect(active).toBe(0);
        active += 1;
        events.push(`${type}:start`);
        await Promise.resolve();
        events.push(`${type}:complete`);
        active -= 1;
      },
      cleanup: () => { events.push(`${type}:cleanup`); }
    });

    await queue.printMany([job('KITCHEN_TICKET'), job('CUSTOMER_RECEIPT')]);

    expect(events).toEqual([
      'KITCHEN_TICKET:start', 'KITCHEN_TICKET:complete', 'KITCHEN_TICKET:cleanup',
      'CUSTOMER_RECEIPT:start', 'CUSTOMER_RECEIPT:complete', 'CUSTOMER_RECEIPT:cleanup'
    ]);
  });

  it('serializes batches submitted while another print is active', async () => {
    const queue = new PosPrintQueue();
    const events: string[] = [];
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    const first = queue.printMany([{ transactionId: 'one', type: 'KITCHEN_TICKET', print: async () => { events.push('one'); await gate; } }]);
    const second = queue.printMany([{ transactionId: 'two', type: 'CUSTOMER_RECEIPT', print: async () => { events.push('two'); } }]);
    await vi.waitFor(() => expect(events).toEqual(['one']));
    release();
    await Promise.all([first, second]);
    expect(events).toEqual(['one', 'two']);
  });
});
