# IoT Link — TI3042 Unidad 2 (evaluación formativa)

App Android (Kotlin, Android Studio) que interconecta **dos teléfonos por Bluetooth clásico (RFCOMM)** simulando
**monitoreo y control** de un dispositivo IoT/OT: un teléfono envía comandos (Activador) y el otro muestra
telemetría y el estado del actuador (Monitoreo). Ambos roles existen en la misma APK.

> **Supuesto:** como no conozco la problemática exacta de la Unidad 1, la app usa un escenario genérico
> (actuador con encendido, intensidad y modo + telemetría de temperatura y nivel). Adáptalo a tu problemática
> cambiando los textos y los tipos de mensaje (`ALLOWED` en `BtLink.kt`).

## Cómo ejecutar
1. Android Studio (Koala o superior) → *Open* → carpeta `IoTLinkApp` → esperar el *Gradle Sync*.
2. Instalar la APK en **dos teléfonos Android 8.0+** (`Run` en cada uno).
3. En los ajustes de Bluetooth, **empareja** ambos teléfonos (la app solo usa dispositivos emparejados).
4. En ambos: crear cuenta (primer uso) → Ingresar.
5. Teléfono A: escribir clave compartida (misma en ambos, mín. 8) → **Esperar conexión (servidor)**.
6. Teléfono B: elegir a A en la lista, misma clave → **Conectar (cliente)**.
7. B → *Activador*: mover switch, slider, modo, enviar mensaje, *ping*. A → *Monitoreo*: ver estados y log.
   Activa en A "Enviar telemetría" para ver temperatura/nivel reales de la batería en B.

## Cobertura de la lista de cotejo
| Criterio | Dónde está |
|---|---|
| 2.1.1.1 Login | `LoginActivity.kt` (hash PBKDF2 + sal, bloqueo tras 5 fallos) |
| 2.1.1.2 Monitoreo y Activador, Material Design | `MonitoreoActivity.kt`, `ActivadorActivity.kt`, layouts (Material 3: Switch, Slider, RadioButton, EditText, Cards) |
| 2.1.2.3 Conexión inalámbrica eficiente y segura | `BtLink.kt`: RFCOMM seguro, solo emparejados, permisos mínimos, hilos separados, reintentos con espera creciente |
| 2.1.2.4 Solución innovadora/efectiva | Telemetría real del equipo, estado del actuador sincronizado, medición de latencia (ping/pong) |
| 2.1.3.5 Seguridad ISO | `Security.kt` + `docs/SEGURIDAD.md` (ISO/IEC 27001, 27400) |
| 2.1.3.6 Lineamientos OT documentados | `docs/SEGURIDAD.md` (IEC 62443) |
| 2.1.4.7 Interconexión entre apps | Dos instancias de la app, servidor/cliente |
| 2.1.4.8 Estabilidad y eficiencia | Contadores + RTT en Monitoreo; plan en `docs/PRUEBAS.md` |

## Para subir a GitHub
```
git init && git add . && git commit -m "TI3042 U2 - IoT Link"
git remote add origin <tu-repo> && git push -u origin main
```
Luego pega el link en un bloc de notas (.txt) y súbelo al AAI.
