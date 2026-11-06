# Plan de pruebas de estabilidad y eficiencia (criterio 2.1.4.8)

Equipos: Teléfono A (modelo / Android): ________  Teléfono B (modelo / Android): ________
Usa *Monitoreo* para leer los contadores (Enviados / Recibidos / Rechazados / Caídas / RTT) y toma una captura por escenario.

| # | Escenario | Procedimiento | Resultado esperado | Resultado obtenido |
|---|---|---|---|---|
| 1 | Enlace básico | Conectar A (servidor) y B (cliente) con la misma clave | "Conectado con …" en ambos | |
| 2 | Funcionalidad | Switch, slider, modo y mensaje desde B | A refleja los 4 cambios en < 1 s | |
| 3 | Distancia | Alejar los teléfonos 1, 5 y 10 m, enviar ping en cada punto | RTT estable; anotar distancia donde se pierde el enlace | |
| 4 | Pérdida y reconexión | Apagar/encender Bluetooth del cliente, o alejarse hasta perder enlace y volver | "Enlace perdido" → reconecta automáticamente; Caídas = 1 | |
| 5 | Carga | Telemetría activa 2 min + mover slider repetidamente | Sin errores; Rechazados = 0 | |
| 6 | App en segundo plano | Enviar la app a segundo plano 30 s y volver | Enlace sigue o se recupera; estado correcto | |
| 7 | Clave distinta | Usar claves diferentes en A y B | Tramas rechazadas y enlace cortado tras 3 | |
| 8 | Bloqueo de login | 5 contraseñas erróneas | Bloqueo 30 s | |
| 9 | Dos sesiones largas | Mantener 10 min con telemetría | Anotar caídas y RTT promedio | |

**Conclusión:** (resume estabilidad, distancia máxima y latencia promedio)
