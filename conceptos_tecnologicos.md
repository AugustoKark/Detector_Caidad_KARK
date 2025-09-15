# Lista de Conceptos Tecnológicos Identificados

## Lenguaje de Programación y Plataforma
- **Kotlin**: Lenguaje de programación principal
- **Android SDK**: Plataforma de desarrollo móvil
- **Android API Level 30**: Versión objetivo del SDK
- **Compatibilidad mínima**: Android API Level 16

## Arquitectura y Patrones de Diseño
- **MVVM (Model-View-ViewModel)**: Patrón arquitectónico principal
- **Foreground Services**: Servicios de primer plano para funcionamiento continuo
- **Singleton Pattern**: Implementado en clases como Guardian
- **Observer Pattern**: Para manejo de eventos de sensores
- **Repository Pattern**: Para gestión de datos y persistencia

## Sensores y Hardware
- **Acelerómetro (TYPE_ACCELEROMETER)**: Sensor principal para detección de caídas
- **Giroscopio**: Sensor secundario para análisis de movimiento
- **Sensor de Proximidad**: Para activación condicional del sistema
- **GPS/LocationManager**: Para geolocalización y zonas seguras
- **SensorManager**: Gestión de sensores del dispositivo

## Procesamiento de Señales
- **Filtro Pasa-Bajos**: Eliminación de ruido en señales
- **Filtro Pasa-Altos**: Resalte de cambios bruscos
- **Promedio Móvil**: Estabilización de señales
- **Frecuencia de Muestreo**: 50Hz (intervalos de 20ms)
- **Análisis de MaxJerk**: Máxima variación de aceleración

## Algoritmos de Detección
- **Detección Multi-fase**: Sistema de 3 fases secuenciales
- **Umbral de Caída Libre**: 0.6G como tolerancia mínima
- **Detección de Impacto**: Análisis de transición caída-impacto
- **Análisis de Inmovilidad**: Verificación post-impacto
- **Algoritmos Determinísticos**: Sin uso de Machine Learning

## Geolocalización y Mapas
- **OpenStreetMap**: Plataforma de mapas de código abierto
- **Geocodificación**: Conversión de coordenadas a direcciones
- **Geofencing**: Delimitación de zonas geográficas seguras
- **LocationListener**: Interfaz para actualizaciones de ubicación
- **Google Maps Integration**: Para enlaces en notificaciones

## Comunicación y Notificaciones
- **SMS**: Sistema principal de alertas de emergencia
- **Android Notification System**: Notificaciones del sistema
- **Notification Channels**: Canales de notificación categorizados
- **PendingIntent**: Intenciones pendientes para acciones
- **Contact Provider**: Acceso a contactos del dispositivo

## Persistencia de Datos
- **SharedPreferences**: Almacenamiento de configuraciones
- **JSON Serialization**: Serialización de datos complejos
- **CSV Logging**: Exportación de datos para análisis
- **Gson Library**: Procesamiento de JSON

## Librerías y Dependencias
- **AndroidX Libraries**: Librerías de soporte modernas
- **Material Design Components**: Interfaz de usuario moderna
- **Kotlin Coroutines**: Programación asíncrona
- **ViewBinding**: Enlace de vistas tipo-seguro
- **Navigation Component**: Navegación entre fragmentos
- **Preference Library**: Pantallas de configuración

## Servicios del Sistema
- **PowerManager**: Gestión de energía y wakelocks
- **WifiManager**: Información de conectividad
- **NotificationManager**: Gestión de notificaciones
- **LocationManager**: Servicios de ubicación
- **SensorManager**: Gestión de sensores

## Optimización y Rendimiento
- **WakeLock**: Prevención de suspensión del dispositivo
- **Battery Optimization**: Optimización de consumo energético
- **Background Processing**: Procesamiento en segundo plano
- **Interval Optimization**: Optimización de intervalos de muestreo
- **Memory Management**: Gestión eficiente de memoria

## Interfaz de Usuario
- **Fragment Architecture**: Arquitectura basada en fragmentos
- **RecyclerView**: Listas eficientes de elementos
- **ViewPager2**: Navegación por pestañas
- **Material Design**: Diseño moderno y accesible
- **Custom Views**: Vistas personalizadas (Zoomable)

## Seguridad y Permisos
- **Runtime Permissions**: Permisos en tiempo de ejecución
- **Location Permissions**: Permisos de ubicación
- **SMS Permissions**: Permisos para envío de SMS
- **Contact Permissions**: Acceso a contactos
- **Manifest Declarations**: Declaraciones de permisos

## Testing y Validación
- **Device Testing**: Pruebas en dispositivos físicos
- **Android Debug Bridge (ADB)**: Herramientas de debugging
- **Log System**: Sistema de logging personalizado
- **Data Analysis**: Análisis estadístico de datos de sensores

## Metodología de Desarrollo
- **Scrum**: Metodología ágil de gestión
- **Sprint Planning**: Planificación de iteraciones
- **User Stories**: Historias de usuario
- **Kanban**: Tracking de tareas
- **MVP (Minimum Viable Product)**: Producto mínimo viable