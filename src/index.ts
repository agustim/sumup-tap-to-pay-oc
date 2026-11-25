import { registerPlugin } from '@capacitor/core';

import type { SumUpTapToPayPlugin } from './definitions';

const SumUpTapToPay = registerPlugin<SumUpTapToPayPlugin>('SumUpTapToPay', {
  web: () => import('./web').then(m => new m.SumUpTapToPayWeb()),
});

export * from './definitions';
export { SumUpTapToPay };
