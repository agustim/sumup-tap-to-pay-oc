# sumup-tap-to-pay-oc

Plugin de Capacitor per cobrar amb el mòbil com a datafon, amb el SDK
**SumUp Tap to Pay** (`com.sumup.tap-to-pay:utopia-sdk`).

Substitueix el datafon Bluetooth per NFC: el telèfon llegeix la targeta i el
pagament es fa contra el compte de SumUp configurat.

## Prerequisits

- Android 11 (API 30) o superior, amb hardware NFC.
- `compileSdkVersion 35` i Java 17 a l'app.
- Desugaring activat al mòdul `app` (`coreLibraryDesugaringEnabled true` +
  `com.android.tools:desugar_jdk_libs:2.1.5`).
- Credencials Maven de SumUp per resoldre l'SDK (repositori privat). Es
  llegeixen primer de les propietats de Gradle i, si no hi són, de l'entorn.
  Desa-les **fora del repo**, a `~/.gradle/gradle.properties`:

  ```properties
  tapToPayMavenUser=...
  tapToPayMavenPassword=...
  ```

  Alternativa per CI/Docker: `TAP_TO_PAY_MAVEN_USER` i
  `TAP_TO_PAY_MAVEN_PASSWORD` a l'entorn. Sense cap de les dues el build falla
  a `android/build.gradle` amb un `GradleException` explícit. El repositori
  públic `maven.sumup.com/releases` **no** serveix `utopia-sdk`.

- Per cobrar de veritat: build **no debuggable**, opcions de desenvolupador i
  USB debugging desactivats al dispositiu (si no, l'atestació falla i l'SDK
  rebutja el pagament). Per proves amb build debug cal un compte *extdev*.

## Instal·lació

```bash
npm install
npm run build          # genera dist/ (obligatori abans d'usar-lo des de l'app)
```

A l'app:

```bash
npm install file:../sumup-tap-to-pay-oc
npx cap sync android
```

## Configuració

El plugin porta un token de SumUp i una affiliate key per defecte, així que
l'app funciona sense configurar res. `openConfiguration()` permet canviar-los;
el valor desat al dispositiu té prioritat sobre el per defecte.

```ts
import { SumUpTapToPay } from 'sumup-tap-to-pay-oc';

const { configured } = await SumUpTapToPay.isConfigured();
if (!configured) await SumUpTapToPay.openConfiguration();
await SumUpTapToPay.initialize();
```

## Referències

<docgen-index>

* [`isConfigured()`](#isconfigured)
* [`openConfiguration()`](#openconfiguration)
* [`initialize()`](#initialize)
* [`ensurePermissions()`](#ensurepermissions)
* [`startPayment(...)`](#startpayment)
* [`tearDown()`](#teardown)
* [`addListener('paymentEvent', ...)`](#addlistenerpaymentevent)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### isConfigured()

```typescript
isConfigured() => Promise<ConfiguredResult>
```

Indica si hi ha un token de SumUp desat al dispositiu.

**Returns:** <code>Promise&lt;<a href="#configuredresult">ConfiguredResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### openConfiguration()

```typescript
openConfiguration() => Promise<ConfigurationResult>
```

Obre la pantalla nativa de configuració (token de SumUp i affiliate key).
El token mai surt del dispositiu ni arriba al codi web.

**Returns:** <code>Promise&lt;<a href="#configurationresult">ConfigurationResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### initialize()

```typescript
initialize() => Promise<InitializeResult>
```

Inicialitza l'SDK de Tap to Pay. Cal cridar-ho abans de `startPayment`.
Només es pot inicialitzar un cop per sessió: si ja ho està, resol
immediatament amb `ready: true`.

**Returns:** <code>Promise&lt;<a href="#initializeresult">InitializeResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### ensurePermissions()

```typescript
ensurePermissions() => Promise<PermissionsResult>
```

Demana els permisos d'execució que necessita l'SDK (càmera i ubicació).
`startPayment` ja els demana si falten; aquest mètode serveix per fer-ho
abans, per exemple en arrencar l'app.

**Returns:** <code>Promise&lt;<a href="#permissionsresult">PermissionsResult</a>&gt;</code>

**Since:** 0.1.2

--------------------


### startPayment(...)

```typescript
startPayment(options: StartPaymentOptions) => Promise<PaymentResult>
```

Inicia un cobrament amb targeta sense contacte.

Els esdeveniments intermedis (`CardRequested`, `CardPresented`,
`CVMRequested`, `CVMPresented`) arriben pel listener `paymentEvent`;
la promesa resol amb el primer esdeveniment terminal.

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code><a href="#startpaymentoptions">StartPaymentOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#paymentresult">PaymentResult</a>&gt;</code>

**Since:** 0.1.0

--------------------


### tearDown()

```typescript
tearDown() => Promise<void>
```

Tanca l'SDK. Cal cridar-ho abans de tornar a configurar el token.

**Since:** 0.1.0

--------------------


### addListener('paymentEvent', ...)

```typescript
addListener(eventName: 'paymentEvent', listener: (event: PaymentEventPayload) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Escolta els esdeveniments del flux de pagament.

| Param           | Type                                                                                    |
| --------------- | --------------------------------------------------------------------------------------- |
| **`eventName`** | <code>'paymentEvent'</code>                                                             |
| **`listener`**  | <code>(event: <a href="#paymenteventpayload">PaymentEventPayload</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

**Since:** 0.1.0

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Elimina tots els listeners registrats.

**Since:** 0.1.0

--------------------


### Interfaces


#### ConfiguredResult

| Prop               | Type                        |
| ------------------ | --------------------------- |
| **`configured`**   | <code>boolean</code>        |
| **`affiliateKey`** | <code>string \| null</code> |


#### ConfigurationResult

| Prop        | Type                 |
| ----------- | -------------------- |
| **`saved`** | <code>boolean</code> |


#### InitializeResult

| Prop        | Type                 |
| ----------- | -------------------- |
| **`ready`** | <code>boolean</code> |


#### PermissionsResult

| Prop          | Type                 |
| ------------- | -------------------- |
| **`granted`** | <code>boolean</code> |


#### PaymentResult

Resultat del cobrament.

Codis propis del plugin: `1` completat, `2` fallit, `3` cancel·lat,
`15` resultat desconegut (cal consultar el moviment al compte de SumUp).

| Prop                       | Type                           | Description                                                        |
| -------------------------- | ------------------------------ | ------------------------------------------------------------------ |
| **`code`**                 | <code>1 \| 2 \| 3 \| 15</code> |                                                                    |
| **`message`**              | <code>string</code>            |                                                                    |
| **`foreignTransactionId`** | <code>string</code>            |                                                                    |
| **`tapToPayCode`**         | <code>number \| null</code>    | Codi de `TapToPayException` de l'SDK (101-305). `0` si no n'hi ha. |
| **`txCode`**               | <code>string \| null</code>    |                                                                    |
| **`serverTransactionId`**  | <code>string \| null</code>    |                                                                    |
| **`cardType`**             | <code>string \| null</code>    |                                                                    |
| **`lastFour`**             | <code>string \| null</code>    |                                                                    |
| **`merchantCode`**         | <code>string \| null</code>    |                                                                    |
| **`cardScheme`**           | <code>string \| null</code>    |                                                                    |


#### StartPaymentOptions

| Prop                         | Type                 | Description                                                                      |
| ---------------------------- | -------------------- | -------------------------------------------------------------------------------- |
| **`total`**                  | <code>number</code>  | Import total en cèntims. Ha de ser més gran que zero.                            |
| **`skipSuccessScreen`**      | <code>boolean</code> | Omet la pantalla d'èxit de l'SDK. Per defecte `true`.                            |
| **`timeoutCardWaitSeconds`** | <code>number</code>  | Segons d'espera de la targeta (60-240). Si falta, s'usa el valor de l'SDK (240). |
| **`foreignTransactionId`**   | <code>string</code>  | Identificador de la transacció per a l'app. Si falta, se'n genera un (UUID).     |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### PaymentEventPayload

| Prop               | Type                                                                                                                                                                                                                      |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`type`**         | <code>'CardRequested' \| 'CardPresented' \| 'CVMRequested' \| 'CVMPresented' \| 'TransactionDone' \| 'TransactionFailed' \| 'TransactionCanceled' \| 'TransactionResultUnknown' \| 'PaymentFlowClosedSuccessfully'</code> |
| **`output`**       | <code><a href="#paymentoutputpayload">PaymentOutputPayload</a> \| null</code>                                                                                                                                             |
| **`tapToPayCode`** | <code>number \| null</code>                                                                                                                                                                                               |
| **`message`**      | <code>string \| null</code>                                                                                                                                                                                               |


#### PaymentOutputPayload

| Prop                      | Type                        |
| ------------------------- | --------------------------- |
| **`txCode`**              | <code>string</code>         |
| **`serverTransactionId`** | <code>string</code>         |
| **`cardType`**            | <code>string \| null</code> |
| **`lastFour`**            | <code>string \| null</code> |
| **`merchantCode`**        | <code>string \| null</code> |
| **`cardScheme`**          | <code>string \| null</code> |

</docgen-api>

## Ús

Codis d'error que retornen les promeses: `NOT_CONFIGURED`, `INIT_FAILED`,
`SDK_NOT_INITIALIZED`, `INVALID_AMOUNT`, `PAYMENT_IN_PROGRESS`, `NO_RESULT`,
`PAYMENT_ERROR`, `TEARDOWN_FAILED`.

### `startPayment(options)`

| Camp | Tipus | Per defecte |
|---|---|---|
| `total` | `number` (cèntims, > 0) | — |
| `skipSuccessScreen` | `boolean` | `true` |
| `timeoutCardWaitSeconds` | `number` (60-240) | valor de l'SDK |
| `foreignTransactionId` | `string` | UUID generat |

`PaymentResult.code`: `1` completat, `2` fallit, `3` cancel·lat, `15` resultat
desconegut (cal consultar el moviment a SumUp). Amb `1`, `2`, `3` i `15` també
arriben `txCode`, `serverTransactionId`, `cardType`, `lastFour`, `merchantCode`,
`cardScheme` i `tapToPayCode` (codi de `TapToPayException` de l'SDK).

### Esdeveniments

`CardRequested`, `CardPresented`, `CVMRequested`, `CVMPresented`,
`TransactionDone`, `TransactionFailed`, `TransactionCanceled`,
`TransactionResultUnknown`, `PaymentFlowClosedSuccessfully`.

```ts
const handle = await SumUpTapToPay.addListener('paymentEvent', (e) => {
  if (e.type === 'CardRequested') console.log('Apropa la targeta');
});
const result = await SumUpTapToPay.startPayment({ total: 100 });
await handle.remove();
```

## Notes

- `initialize()` només es pot cridar un cop per sessió; una segona crida amb
  l'SDK ja inicialitzat resol `ready: true` immediatament.
- `openConfiguration()` amb canvis desa i fa `tearDown()` de l'SDK perquè el
  token nou tingui efecte; cal tornar a cridar `initialize()`.
- En web tots els mètodes llancen `unimplemented`.
