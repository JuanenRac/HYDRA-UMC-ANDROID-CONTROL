# Plan de Mejora de Sincronización HYDRA-UMC

Este plan aborda los fallos de sincronización entre la aplicación Android y el servidor HYDRA-UMC STUDIO, optimizando el tráfico de red, mejorando la resiliencia en redes lentas y evitando la pérdida de datos en actualizaciones parciales.

## Cambios Propuestos

### [Componente] Red y Conectividad

#### [MODIFY] [Discovery.kt](file:///C:/Users/juane/Documents/GitHub/HYDRA-UMC-ANDROID-CONTROL/app/src/main/java/com/hydraumc/control/network/Discovery.kt)
- **Incrementar el timeout de escaneo**: Cambiar `SCAN_TIMEOUT_MS` de 600ms a 1500ms.
- **Razón**: 600ms es insuficiente para muchas redes Wi-Fi industriales o domésticas congestionadas, causando que la app no detecte servidores válidos.

#### [MODIFY] [HydraWebSocket.kt](file:///C:/Users/juane/Documents/GitHub/HYDRA-UMC-ANDROID-CONTROL/app/src/main/java/com/hydraumc/control/network/HydraWebSocket.kt)
- **Optimizar el Echo Guard**: Implementar una comparación más robusta que simplemente `toString()`. Aunque `org.json` no garantiza orden, intentaremos minimizar actualizaciones falsas causadas por cambios menores en el formato del servidor.

### [Componente] Modelo de Datos

#### [MODIFY] [HydraState.kt](file:///C:/Users/juane/Documents/GitHub/HYDRA-UMC-ANDROID-CONTROL/app/src/main/java/com/hydraumc/control/model/HydraState.kt)
- **Fusión inteligente de Arrays (Deep Merge)**: Modificar `deepMerge` para que, al encontrar un `JSONArray`, intente fusionar elementos por su campo `id` en lugar de sobrescribir el array completo.
- **Razón**: Evita que los robots o controladores desaparezcan de la UI cuando el servidor envía un mensaje de "delta" que solo contiene una parte de la lista.

### [Componente] Lógica de Negocio (ViewModel)

#### [MODIFY] [RobotViewModel.kt](file:///C:/Users/juane/Documents/GitHub/HYDRA-UMC-ANDROID-CONTROL/app/src/main/java/com/hydraumc/control/viewmodel/RobotViewModel.kt)
- **Debounce de Sync REST**:
    - Introducir un retardo (aprox. 500ms) para la sincronización completa vía REST (`postSettings`) durante operaciones de alta frecuencia como el *jogging*.
    - Las operaciones de WebSocket seguirán siendo instantáneas.
- **Sincronización Inmediata para Críticos**: Garantizar que los comandos `play`, `stop`, `pause`, `enable` y `disable` sigan disparando un sync REST inmediato sin debounce.
- **Razón**: Evita saturar el servidor con peticiones pesadas de estado completo durante movimientos manuales, reduciendo el lag y el riesgo de colisiones de datos.

## Plan de Verificación

### Pruebas Automatizadas
- No se dispone de un entorno de pruebas unitarias completo para red, pero se verificará la lógica de `deepMerge` mediante una prueba rápida de escritorio si es necesario.

### Verificación Manual
1. **Detección de Servidor**: Abrir la app y verificar si el escaneo de red es más estable.
2. **Jogging fluido**: Mover el robot con el joystick y observar en los logs de telemetría que las peticiones REST no se acumulan.
3. **Persistencia de Robots**: Simular una actualización parcial (o esperar a una) y verificar que los robots no "parpadean" o desaparecen de la lista.
