
# Guardian - Sistema Inteligente de Detección de Caídas y Seguridad Personal

## 📱 ¿Qué es Guardian?

Guardian es una aplicación móvil avanzada para Android que utiliza sensores del smartphone para **detectar automáticamente caídas** y **enviar alertas de emergencia** a contactos familiares. Además, incluye un sistema de **monitoreo de zonas seguras** para notificar cuando la persona sale de áreas designadas.

### 🎯 Problema que Resuelve

**Las caídas son la segunda causa de muerte accidental en el mundo**, especialmente en adultos mayores:
- 37.3 millones de caídas requieren atención médica anualmente
- 646,000 personas mueren por caídas cada año
- El tiempo de respuesta es crítico: cada minuto cuenta en una emergencia
- Muchas personas mayores viven solas y pueden quedar incapacitadas para pedir ayuda

**Guardian salva vidas detectando caídas automáticamente y alertando inmediatamente a familiares.**

---

## 🧠 Tecnología Central

### Detección 
```
🔄 Algoritmo de 3 Fases Inteligentes:

1️⃣ CAÍDA LIBRE (Detección): Aceleración < 0.6G
   ↓ Activa monitoreo intensivo por 1 segundo
   
2️⃣ IMPACTO (Análisis): Detecta tipo de caída y fuerza
   ↓ Caída lateral / hacia atrás / hacia adelante
   ↓ Umbrales adaptativos según tipo detectado
   
3️⃣ POSICIÓN FINAL (Confirmación): Orientación horizontal o cambio drástico
   ↓ Confirma que realmente ocurrió una caída
```

### Características Técnicas Avanzadas
- **Frecuencia de análisis**: 50Hz (20ms entre muestras)
- **Ventana de datos**: 10 segundos (500 muestras)
- **Filtros digitales**: Butterworth 2° orden para eliminar ruido
- **19 buffers** de análisis simultáneo de señales
- **6 tipos de caídas** detectados con precisión específica

### Sistema Anti-Falsos Positivos
Guardian **NO confunde** las siguientes actividades con caídas:
✅ Mirar el teléfono (movimiento controlado)
✅ Guardar en el bolsillo (patrón específico)
✅ Sentarse bruscamente (sin cambio de orientación)
✅ Bajar escaleras rápido (sin caída libre)
✅ Deportes o ejercicio (patrones reconocidos)

---

## 🚨 Sistema de Alertas de Emergencia

### Secuencia Automática de Respuesta
```
🔥 CAÍDA DETECTADA
     ↓
📱 Pantalla de Cancelación (30 segundos configurables)
   │ ┌─ Usuario desliza para cancelar ─→ ❌ Sin alerta
   │ └─ Tiempo agotado ─→ ⬇️
     ↓
📧 1. ENVÍO DE SMS con ubicación GPS
     ↓ (inmediato)
📢 2. SIRENA DE EMERGENCIA (volumen máximo)
     ↓ (3 segundos después)
📞 3. LLAMADA AUTOMÁTICA al contacto
     ↓
🤖 4. RESPUESTA AUTOMÁTICA a llamadas del contacto
```

### Información Enviada en Emergencia
**SMS automático incluye:**
```
🆘 "¡ALERTA! Se ha detectado una posible caída. 
📍 Ubicación: https://maps.google.com/?q=lat,lng
🔋 Batería: 85%
⏰ Hora: 22/05/2024 14:30:15"
```

### Interfaz de Cancelación Inteligente
- **SeekBar**: Requiere deslizar completamente para cancelar (no toque accidental)
- **Vibración progresiva**: Feedback táctil en cada etapa
- **Cuenta regresiva visual**: Usuario sabe exactamente cuánto tiempo tiene
- **Escalada de urgencia**: Mensaje cambia en los últimos 10 segundos

---

## 🌍 Sistema de Zonas Seguras (Geofencing)

### Monitoreo Geográfico Inteligente
Guardian puede alertar si la persona **sale de áreas seguras** configuradas:

**Ejemplo de Configuración:**
- 🏠 **Casa**: Radio 500 metros (paseos por el barrio están bien)
- 🏥 **Centro Médico**: Radio 200 metros (citas médicas)
- 👨‍👩‍👧‍👦 **Casa Familiar**: Radio 300 metros (visitas familiares)

### Horarios de Excepción
**Períodos donde NO alertar aunque esté fuera de zona:**
- 🏋️ "Fisioterapia": Martes y Jueves 14:00-16:00
- 🛒 "Supermercado": Lunes y Miércoles 10:00-12:00
- ⛪ "Iglesia": Domingos 09:00-11:00

### Sistema Anti-Spam
- **Cooldown de 1 hora**: Evita bombardeo de SMS
- **Solo ubicaciones recientes**: Usa GPS de menos de 30 minutos

---

## 💾 Infraestructura Tecnológica

### Almacenamiento y Sincronización
- **Base de datos rotativa**: Un archivo SQLite por día
- **Compresión automática**: Archivos antiguos → ZIP
- **Limpieza automática**: Solo mantiene 7 días localmente

### Robustez y Disponibilidad
- **Servicio 24/7**: Funciona continuamente en segundo plano
- **Auto-inicio**: Se activa automáticamente al reiniciar el dispositivo
- **START_STICKY**: Android lo reinicia si algo lo mata
- **Tolerancia a fallos**: Funciona aunque fallen componentes individuales

### Compatibilidad
- **Android 4.1+**: Funciona en 95%+ de dispositivos
- **Múltiples APIs**: Métodos alternativos para máxima compatibilidad
- **Permisos inteligentes**: Solo solicita lo necesario según versión

---



### Configuración Ultra-Simple
```
📱 INSTALACIÓN (2 minutos):
1. Descargar Guardian
2. Aceptar permisos
3. Configurar contacto de emergencia
4. ✅ ¡LISTO! Ya está protegido
```

### Interfaz Intuitiva
- **Pestañas claras**: Estado, Señales, Configuración, Zonas Seguras
- **Testing integrado**: Botón para probar que todo funciona
- **Feedback visual**: Verde = activo, Rojo = requiere atención
- **Configuración granular**: Control fino sin complejidad

### Visualización en Tiempo Real
- **Gráficas de sensores**: Ver qué detecta el algoritmo
- **Zoom interactivo**: Analizar eventos específicos en detalle
- **Historial navegable**: Revisar eventos pasados

---



## 🛠️ Arquitectura Técnica (Resumen)

### Componentes Principales
```
📱 GUARDIAN APP
├── 🧠 detection/     → Sistema de detección (3 archivos)
├── 🚨 alerts/        → Sistema de emergencias (5 archivos)  
├── 📡 sensors/       → Manejo de sensores (3 archivos)
├── ⚙️ core/          → Infraestructura base (5 archivos)
├── 💾 storage/       → Base de datos y sync (4 archivos)
├── 🌍 safezone/      → Geofencing inteligente (5 archivos)
├── 🎨 ui/            → Interfaz de usuario (3 archivos)
└── 🔧 utils/         → Utilidades comunes (4 archivos)

📊 TOTAL: 43 archivos, ~8,000 líneas de código
```

### Stack Tecnológico
- **Lenguaje**: Kotlin (100% nativo Android)
- **Base de datos**: SQLite con ORM personalizado
- **Sensores**: Acelerómetro + GPS + Network Location
- **Comunicaciones**: SMS + Llamadas + HTTP/JSON
- **UI**: Material Design + Navigation Component
- **Persistencia**: SharedPreferences + JSON (Gson)

### APIs y Bibliotecas
- **Filtros digitales**: Butterworth implementación propia
- **HTTP**: OkHttp para uploads robustos
- **Geolocation**: LocationManager + Geocoder
- **Notifications**: NotificationCompat para compatibilidad
- **Permissions**: Activity Result API moderna

---
