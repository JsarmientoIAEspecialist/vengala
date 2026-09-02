# 🎆 Bengala

**Mesh Bluetooth para fiestas. Sin internet. Sin señal. Sin problema.**

App Android para comunicarte y encontrar a tu parche en raves y festivales donde
la red celular colapsa. Todo funciona 100% offline con tecnología P2P: los
teléfonos forman una **red mesh por Bluetooth Low Energy** y los mensajes saltan
de teléfono en teléfono hasta llegar a su destino.

## Qué hace

- 💬 **Chat del parche**: mensajes que se propagan por el mesh (hasta 7 saltos).
  Si alguien estaba lejos, los mensajes recientes se le entregan al reconectarse
  (*store-and-forward*).
- 📡 **Radar de amigos**: cada persona aparece como un punto neón con dirección
  real y distancia. Apuntas el teléfono, caminas hacia el punto y llegas. Usa GPS
  puro (los satélites no necesitan internet) + la brújula del teléfono. Sin mapas.
- 👥 **Gente**: quién está en el mesh, hace cuánto se vio, su batería y distancia.
- 🔐 **Cifrado real**: AES-256-GCM con clave derivada del "código de fiesta"
  (PBKDF2). Quien no tiene el código solo retransmite bytes que no puede leer.
- 📲 **La app se comparte por Bluetooth**: botón en Ajustes que envía el APK a
  otro teléfono. La app se propaga en plena fiesta sin Play Store ni datos.

## Arquitectura técnica

```
┌─────────────────────────── UI (Jetpack Compose) ───────────────────────────┐
│   ChatScreen      RadarScreen       PeersScreen        SettingsScreen      │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │ StateFlow
┌───────────────────────────────▼────────────────────────────────────────────┐
│                     MeshRepository (estado observable)                     │
└───────────────────────────────▲────────────────────────────────────────────┘
                                │
┌───────────────────────────────┴────────────────────────────────────────────┐
│                  MeshService (foreground service)                          │
│  ┌──────────────┐  ┌───────────────────────────────┐  ┌────────────────┐   │
│  │ LocationEngine│  │         MeshRouter            │  │   CryptoBox    │   │
│  │ (GPS puro)   │  │ flooding + TTL + dedupe LRU   │  │ AES-256-GCM    │   │
│  └──────────────┘  │ + store-and-forward           │  │ PBKDF2(código) │   │
│                    └──────┬─────────────────┬──────┘  └────────────────┘   │
│         ┌─────────────────┤                 ├──────────────────┐           │
│  ┌──────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐  ┌────────▼───────┐   │
│  │BleAdvertiser│  │  BleScanner  │  │  GattServer  │  │  GattClient(s) │   │
│  │ (nos anuncia)│ │ (descubre)   │  │ (rol perif.) │  │ (rol central)  │   │
│  └─────────────┘  └──────────────┘  └──────────────┘  └────────────────┘   │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Cada teléfono juega ambos roles BLE a la vez**: anuncia el servicio Bengala
  (periférico + servidor GATT) y escanea/conecta a otros (central + cliente GATT,
  hasta 5 enlaces salientes). Así el grafo se teje solo.
- **Protocolo binario propio** ([Protocol.kt](app/src/main/java/com/bengala/app/mesh/Protocol.kt)):
  cabecera de 30 bytes con versión, tipo, TTL, messageId aleatorio (deduplicación),
  senderId estable, timestamp y payload ≤ 470 bytes (cabe en un write con MTU 512).
- **Enrutamiento por inundación controlada** ([MeshRouter.kt](app/src/main/java/com/bengala/app/mesh/MeshRouter.kt)):
  caché LRU de 4000 ids vistos mata duplicados y ciclos; TTL de 7 saltos limita el
  radio; los paquetes de chat de los últimos 30 min se re-entregan a cada peer
  nuevo que se conecta.
- **Beacons periódicos**: ubicación cada 30 s (si la compartes), perfil cada 60 s.
- **Sin dependencias de Google Play Services**: GPS vía `LocationManager`,
  cripto vía JCA del sistema. Funciona en cualquier Android 8.0+ con BLE.

## Cómo compilar

1. Instala [Android Studio](https://developer.android.com/studio) (incluye JDK y SDK).
2. Abre la carpeta del proyecto (`d:\Apps\bengala`) con **File → Open**.
3. Espera el Gradle Sync (descarga dependencias la primera vez — eso sí necesita internet).
4. Conecta tu teléfono con depuración USB activada y dale **Run ▶**.

O por línea de comandos (con `JAVA_HOME` apuntando a un JDK 17):

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

Para repartir la app conviene un APK **release** firmado (los debug caducan menos
problemas, pero release pesa menos): crea un keystore en Android Studio
(**Build → Generate Signed App Bundle/APK → APK**) y comparte ese APK.

## Cómo se usa en la fiesta

1. Antes de que muera la señal: uno del parche instala Bengala y se la pasa a los
   demás con **Ajustes → Compartir la app por Bluetooth** (el receptor debe
   permitir "instalar apps desconocidas").
2. Todos escriben el **mismo código de fiesta** en Ajustes y su nombre.
3. Listo. Con la app abierta (o en segundo plano — hay notificación fija), los
   teléfonos se encuentran solos a 10–30 m y forman el mesh.
4. ¿Perdiste a alguien? Pestaña **Radar**: apunta el teléfono y camina hacia su punto.

## Límites honestos (v1)

- **Alcance BLE**: ~10–30 m por salto entre multitudes; el mesh multiplica eso por
  7 saltos, pero necesita gente con la app en medio. Cuanta más gente, mejor funciona.
- **GPS en interiores** falla; el radar es para festivales al aire libre.
- **iPhone**: iOS no permite compartir apps por Bluetooth ni mesh BLE en segundo
  plano de forma comparable. Esta v1 es Android.
- Los fabricantes agresivos con la batería (Xiaomi, Huawei…) pueden matar el
  servicio: excluye Bengala de la optimización de batería.
- El chat es un canal grupal (todos los del mismo código). Mensajes directos
  privados: hoja de ruta v2, junto con fragmentación de paquetes grandes y
  Wi-Fi Direct como transporte de alta capacidad.

## Privacidad

Nada sale a internet (la app ni siquiera pide permiso de red). La ubicación viaja
cifrada solo hacia quienes tienen tu código de fiesta, y puedes apagarla en
Ajustes. El id de nodo es aleatorio, no derivado de tu hardware.
