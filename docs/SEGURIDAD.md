# Seguridad y lineamientos OT — IoT Link

Alcance: app Android que controla/monitorea un dispositivo IoT/OT por Bluetooth. Estándares usados como referencia:
**ISO/IEC 27001:2022 (Anexo A)**, **ISO/IEC 27400:2022 (guía de seguridad y privacidad IoT)** e **IEC 62443 (seguridad en sistemas de control industrial / OT)**.

## 1. Controles implementados
| Control en la app | Implementación | ISO/IEC 27001 | ISO/IEC 27400 (tema) | IEC 62443 |
|---|---|---|---|---|
| Autenticación de usuario | Login con usuario y contraseña mín. 8 | A.5.17, A.8.5 | Control de acceso | FR1 (IAC) |
| Manejo de credenciales | PBKDF2-HMAC-SHA256, 120 000 iteraciones, sal aleatoria de 16 bytes; no se guarda la contraseña; comparación en tiempo constante; contraseña borrada de memoria | A.5.17, A.8.24 | Protección de credenciales | FR1 |
| Anti fuerza bruta | Bloqueo 30 s tras 5 intentos; mensaje de error genérico (sin enumerar usuarios) | A.8.5 | Control de acceso | FR1 |
| Confidencialidad e integridad en el enlace | Cifrado de cada trama con AES-256-GCM (clave derivada por PBKDF2 de la clave compartida) además del canal RFCOMM seguro | A.8.24, A.8.20 | Protección de comunicaciones | FR3, FR4 |
| Anti-repetición | Número de secuencia por trama; se rechazan tramas repetidas o anteriores | A.8.26 | Integridad de datos | FR3 |
| Validación de entradas | Lista blanca de tipos de mensaje, límite de longitud (200), rangos de valores en comandos | A.8.26, A.8.28 | Seguridad de software | FR3 (SI) |
| Respuesta a intrusión | Tras 3 tramas inválidas consecutivas se corta el enlace; se contabilizan y muestran como "Rechazados" | A.8.15, A.8.16 | Detección de eventos | FR6 |
| Mínimo privilegio | Solo `BLUETOOTH_CONNECT`; sin ubicación, sin escaneo, sin internet | A.8.2 | Minimización | FR2 |
| Solo dispositivos conocidos | Solo se conecta a dispositivos **emparejados** (el emparejamiento exige confirmación del usuario) | A.5.15 | Autenticación de dispositivos | FR1 |
| Protección de datos en el equipo | `allowBackup=false`; sin datos sensibles en logs | A.8.12 | Privacidad | FR4 |
| Disponibilidad | Reintentos con espera creciente (máx. 5); el servidor vuelve a escuchar si el enlace cae | A.8.14 | Resiliencia | FR7 |

## 2. Lineamientos OT (verificación)
Marca cada punto tras verificarlo en tu prueba y adjunta captura o nota.
| Lineamiento OT | Cómo se cumple | Verificado (fecha / evidencia) |
|---|---|---|
| Autenticación antes de operar | No se accede a Activador/Monitoreo sin login | |
| Comunicación solo entre equipos autorizados | Emparejamiento + clave compartida; con clave distinta el enlace se rechaza | |
| Integridad de comandos | Un comando alterado o repetido es rechazado (contador "Rechazados") | |
| Seguridad por defecto / falla segura | Si cae el enlace, la app no ejecuta comandos pendientes y muestra "Enlace perdido" | |
| Registro de eventos | Log en Monitoreo con hora; contadores de rechazos y caídas | |
| Disponibilidad | Reconexión automática probada (ver `PRUEBAS.md`, escenario 4) | |

## 3. Limitaciones conocidas (honestidad técnica)
- La sal de la clave del enlace es fija por versión de la app; en producción usar un intercambio de claves por sesión (p. ej. ECDH) en lugar de clave compartida manual.
- El prototipo usa Bluetooth clásico; Android no permite verificar desde la app que el emparejamiento sea "autenticado" (Secure Connections) en todos los modelos.
- El almacén de credenciales usa `SharedPreferences` privado (solo hash). Para producción: Android Keystore.
