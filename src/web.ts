import { WebPlugin } from '@capacitor/core';

import type {
  ConfigurationResult,
  ConfiguredResult,
  InitializeResult,
  PaymentResult,
  PermissionsResult,
  StartPaymentOptions,
  SumUpTapToPayPlugin,
} from './definitions';

export class SumUpTapToPayWeb extends WebPlugin implements SumUpTapToPayPlugin {
  constructor() {
    super({
      name: 'SumUpTapToPay',
      platforms: ['web'],
    });
  }

  async isConfigured(): Promise<ConfiguredResult> {
    throw this.unimplemented('SumUp Tap to Pay només funciona en Android.');
  }

  async openConfiguration(): Promise<ConfigurationResult> {
    throw this.unimplemented('SumUp Tap to Pay només funciona en Android.');
  }

  async initialize(): Promise<InitializeResult> {
    throw this.unimplemented('SumUp Tap to Pay només funciona en Android.');
  }

  async ensurePermissions(): Promise<PermissionsResult> {
    throw this.unimplemented('SumUp Tap to Pay només funciona en Android.');
  }

  async startPayment(options: StartPaymentOptions): Promise<PaymentResult> {
    console.log('startPayment', options);
    throw this.unimplemented('SumUp Tap to Pay només funciona en Android.');
  }

  async tearDown(): Promise<void> {
    throw this.unimplemented('SumUp Tap to Pay només funciona en Android.');
  }
}
