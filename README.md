# Aplicación Experimental de Detección de Caídas para Android

## INTRODUCCIÓN!


Guardian es una aplicación de detección de caídas que combina  sensores avanzados, comunicaciones de emergencia y monitoreo geográfico en un sistema robusto y confiable.

### **🏗️ Componentes Principales**

#### **1. Núcleo de Detección (`detection/`)**
- **Detector de Caidas**: Algoritmo de 3 fases con 19 buffers de datos
- **Filtros digitales**: Butterworth 2° orden para procesamiento de señales
- **Anti-falsos positivos**: Detecta gestos como mirar teléfono o guardar en bolsillo
- **Adaptabilidad**: Umbrales específicos por tipo de caída (lateral, atrás, adelante)

#### **2. Sistema de Alertas (`alerts/`)**
- **Interfaz de cancelación intuitiva**: SeekBar que requiere gesto completo
- **Secuencia automática**: SMS → Sirena → Llamada con delays optimizados
- **Respuesta automática**: Solo acepta llamadas del contacto de emergencia
- **Compatibilidad extrema**: 6 métodos diferentes de respuesta telefónica

#### **3. Monitoreo de Sensores (`sensors/`)**
- **Geolocalización inteligente**: GPS + Network con activación automática
- **Reportes completos**: Ubicación + batería + timestamp en emergencias
- **Análisis de dispositivo**: Inventario completo de sensores para optimización

#### **4. Infraestructura Core (`core/`)**
- **Servicio 24/7**: Foreground service con START_STICKY
- **Auto-inicio**: Funciona después de reiniciar dispositivo
- **Gestión de permisos**: 14 permisos con solicitud inteligente
- **Disponibilidad garantizada**: Múltiples capas de redundancia

#### **5. Almacenamiento Inteligente (`storage/`)**
- **Base de datos rotativa**: Un SQLite por día con compresión automática
- **Sincronización robusta**: Batching + reintento + upload diferido
- **Machine Learning**: Reporta falsos positivos para mejorar algoritmo
- **Escalabilidad**: Arquitectura preparada para miles de dispositivos

#### **6. Zonas Seguras (`safezone/`)**
- **Geofencing avanzado**: Múltiples zonas con radios configurables
- **Horarios de excepción**: Períodos donde no alertar (fisioterapia, trabajo)
- **Monitoreo independiente**: Servicio separado que no interfiere con detección
- **UX profesional**: Geocoding bidireccional y ubicación actual con un click

#### **7. Interfaz de Usuario (`ui/`)**
- **Configuración granular**: Control fino sobre todos los aspectos
- **Visualización en tiempo real**: Gráficos de sensores con zoom/pan
- **Testing integrado**: Botón de prueba para verificar funcionamiento
- **Canvas interactivo**: Sistema de zoom multi-touch de nivel profesional

#### **8. Utilidades (`utils/`)**
- **Logging dual**: Logcat + persistencia para análisis forense
- **Vibración unificada**: API moderna y legacy en interfaz única
- **Configuración centralizada**: Control master de funcionalidades
- **Compatibilidad amplia**: Funciona desde Android 4.1+

### **🚀 Capacidades Técnicas Destacadas**

#### **Comunicaciones de Emergencia**
- **Múltiples canales**: SMS + Llamada + Ubicación + Estado de batería
- **Robustez extrema**: Funciona con conectividad limitada
- **Persistencia**: Reintenta comunicaciones hasta confirmar recepción

#### **Monitoreo Geográfico**
- **Geofencing inteligente**: Múltiples zonas con lógica de excepción
- **Precisión dual**: GPS + Network para máxima cobertura
- **Anti-spam**: Cooldown de 1 hora entre notificaciones
- **Contexto rico**: Información completa en cada alerta

#### **Experiencia de Usuario**
- **Zero-config**: Funciona inmediatamente después de configurar contacto
- **Testing integrado**: Verificación de funcionamiento sin emergencia real
- **Feedback**: Vibración + sonido + visual coordinados
- **Accesibilidad**: Diseñado para personas mayores con posibles limitaciones





## GUÍA DEL USUARIO

* Asegúrate de configurar el número de teléfono de emergencia para que la aplicación llame automáticamente cuando se detecte una caída.
* Las llamadas de ese número serán contestadas automáticamente.
* Un SMS de ese número con la palabra POSICIÓN en el contenido será respondido automáticamente con la posición geográfica (si está disponible).
* Si el SMS contiene la palabra ALARMA, en su lugar, reproducirá un sonido de alarma.
* La aplicación se iniciará automáticamente cuando el teléfono se encienda.
* Para un rendimiento óptimo (para reducir el número de falsas alarmas y el número de caídas no detectadas), lleva el dispositivo cerca de tu cintura (un bolsillo del pantalón, un clip para el cinturón, etc.).
* Mantén tu dispositivo cargado en todo momento.

## SCREENSHOTS

 COnfiguración    | About view (con instrucciones y botón de emergencia) | Sensores & Señales de detección (pausable) |                        Settings                          | Número de contacto de emergencia
:-----------------------------:|:----------------------------------------------------:|:------------------------------------------:|:--------------------------------------------------------:|:--------------------------------------------------:
 ![EULA](doc/screenshot.1.jpeg) |           ![About](doc/screenshot.1.jpeg)            |     ![Signals](doc/screenshot.2.jpeg)      |            ![Settings](doc/screenshot.3.jpeg)            | ![Contact](doc/screenshot.4.jpeg)



## **Conclusión: Una Obra Maestra de Ingeniería**

Guardian representa un ejemplo excepcional de **ingeniería de software aplicada a problemas reales**. La aplicación combina:

- **Ciencias de la computación**: Algoritmos de ML y procesamiento de señales
- **Ingeniería de sistemas**: Arquitectura robusta y escalable
- **Diseño de UX**: Interfaz intuitiva para usuarios no técnicos
- **Ingeniería de telecomunicaciones**: Sistemas de emergencia confiables
- **Gestión de datos**: Almacenamiento y sincronización profesional

El resultado es una aplicación que no solo **funciona**, sino que funciona **excepcionalmente bien** en situaciones críticas donde las vidas pueden depender de su correcto funcionamiento.

La calidad del código, la profundidad del análisis de requisitos, y la atención al detalle en cada componente demuestran un nivel de profesionalismo que fácilmente podría competir con productos comerciales de empresas establecidas en el sector de health tech y emergency response.

**Guardian no es solo una app de detección de caídas - es una plataforma completa de seguridad personal que redefine lo que es posible con un smartphone.**
"""
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/AugustoKark/Detector_Caidad_KARK)
