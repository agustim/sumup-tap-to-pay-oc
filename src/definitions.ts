import type { PluginListenerHandle } from '@capacitor/core';

export interface SumUpTapToPayPlugin {
  /**
   * Indica si hi ha un token de SumUp desat al dispositiu.
   *
   * @since 0.1.0
   */
  isConfigured(): Promise<ConfiguredResult>;

  /**
   * Obre la pantalla nativa de configuració (token de SumUp i affiliate key).
   * El token mai surt del dispositiu ni arriba al codi web.
   *
   * @since 0.1.0
   */
  openConfiguration(): Promise<ConfigurationResult>;

  /**
   * Inicialitza l'SDK de Tap to Pay. Cal cridar-ho abans de `startPayment`.
   * Només es pot inicialitzar un cop per sessió: si ja ho està, resol
   * immediatament amb `ready: true`.
   *
   * @since 0.1.0
   */
  initialize(): Promise<InitializeResult>;

  /**
   * Demana els permisos d'execució que necessita l'SDK (càmera i ubicació).
   * `startPayment` ja els demana si falten; aquest mètode serveix per fer-ho
   * abans, per exemple en arrencar l'app.
   *
   * @since 0.1.2
   */
  ensurePermissions(): Promise<PermissionsResult>;

  /**
   * Inicia un cobrament amb targeta sense contacte.
   *
   * Els esdeveniments intermedis (`CardRequested`, `CardPresented`,
   * `CVMRequested`, `CVMPresented`) arriben pel listener `paymentEvent`;
   * la promesa resol amb el primer esdeveniment terminal.
   *
   * @since 0.1.0
   */
  startPayment(options: StartPaymentOptions): Promise<PaymentResult>;

  /**
   * Tanca la sessió de l'SDK (per exemple, en fer logout).
   *
   * No cal cridar-ho per canviar el token: el proveïdor el llegeix de les
   * preferències a cada petició. Cal tornar a cridar `initialize()` després.
   *
   * @since 0.1.0
   */
  tearDown(): Promise<void>;

  /**
   * Escolta els esdeveniments del flux de pagament.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'paymentEvent',
    listener: (event: PaymentEventPayload) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Elimina tots els listeners registrats.
   *
   * @since 0.1.0
   */
  removeAllListeners(): Promise<void>;
}

export interface ConfiguredResult {
  configured: boolean;
  affiliateKey: string | null;
}

export interface ConfigurationResult {
  saved: boolean;
}

export interface InitializeResult {
  ready: boolean;
}

export interface PermissionsResult {
  granted: boolean;
}

export interface StartPaymentOptions {
  /** Import total en cèntims. Ha de ser més gran que zero. */
  total: number;
  /** Omet la pantalla d'èxit de l'SDK. Per defecte `true`. */
  skipSuccessScreen?: boolean;
  /** Segons d'espera de la targeta (60-240). Si falta, s'usa el valor de l'SDK (240). */
  timeoutCardWaitSeconds?: number;
  /** Identificador de la transacció per a l'app. Si falta, se'n genera un (UUID). */
  foreignTransactionId?: string;
}

/**
 * Resultat del cobrament.
 *
 * Codis propis del plugin: `1` completat, `2` fallit, `3` cancel·lat,
 * `15` resultat desconegut (cal consultar el moviment al compte de SumUp).
 */
export interface PaymentResult {
  code: 1 | 2 | 3 | 15;
  message: string;
  foreignTransactionId: string;
  /** Codi de `TapToPayException` de l'SDK (101-305). `0` si no n'hi ha. */
  tapToPayCode: number | null;
  txCode: string | null;
  serverTransactionId: string | null;
  cardType: string | null;
  lastFour: string | null;
  merchantCode: string | null;
  cardScheme: string | null;
}

export interface PaymentOutputPayload {
  txCode: string;
  serverTransactionId: string;
  cardType?: string | null;
  lastFour?: string | null;
  merchantCode?: string | null;
  cardScheme?: string | null;
}

export interface PaymentEventPayload {
  type:
    | 'CardRequested'
    | 'CardPresented'
    | 'CVMRequested'
    | 'CVMPresented'
    | 'TransactionDone'
    | 'TransactionFailed'
    | 'TransactionCanceled'
    | 'TransactionResultUnknown'
    | 'PaymentFlowClosedSuccessfully';
  output?: PaymentOutputPayload | null;
  tapToPayCode?: number | null;
  message?: string | null;
}
