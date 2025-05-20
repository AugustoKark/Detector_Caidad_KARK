# Secuencia de Detección de Caídas

## 1. Entrada y Remuestreo

* **Entrada:** Datos del acelerómetro (`x`, `y`, `z`) en unidades de **G**
* **Frecuencia:** Remuestreo a **50Hz** (cada 20ms)
* **Fórmula de interpolación lineal:**

```python
valor(t) = ante + (post - ante) * (t - t_ante) / (t_post - t_ante)
```

---

## 2. Aplicación de Filtros

### Filtro Pasa-Bajos (LPF) – Butterworth 2° orden

* **Frecuencia de corte:** 0.25 Hz
* **Ganancia:** 4143.204922
* **Fórmula:**

```python
xv[2] = valor / FILTER_LPF_GAIN
yv[2] = (xv[0] + xv[2]) + 2*xv[1] + (-0.9565436765)*yv[0] + (1.9555782403)*yv[1]
```

* **Aplicado a:** `x`, `y`, `z` → `xLPF`, `yLPF`, `zLPF`

### Filtro Pasa-Altos (HPF) – Butterworth 2° orden

* **Frecuencia de corte:** 0.25 Hz
* **Ganancia:** 1.022463023
* **Fórmula:**

```python
xv[2] = valor / FILTER_HPF_GAIN
yv[2] = (xv[0] + xv[2]) - 2*xv[1] + (-0.9565436765)*yv[0] + (1.9555782403)*yv[1]
```

* **Aplicado a:** `x`, `y`, `z` → `xHPF`, `yHPF`, `zHPF`

---

## 3. Cálculo de Métricas

### Máximo-Mínimo (ventana de 100ms)

```python
xMaxMin = max(x) - min(x)
yMaxMin = max(y) - min(y)
zMaxMin = max(z) - min(z)
```

### Magnitud del Vector (SV)

```python
sv(x, y, z) = √(x² + y² + z²)
```

### Métricas principales:

```python
svTOT = sv(x, y, z)                        # Magnitud total
svD = sv(xHPF, yHPF, zHPF)                 # Componente dinámica
svMaxMin = sv(xMaxMin, yMaxMin, zMaxMin)  # Diferencia max-mín
z2 = (svTOT² - svD² - G²) / (2*G)         # Componente vertical
```

---

## 4. Detección de Caída Libre

### Condiciones:

```python
svTOT_anterior ≥ 0.6 AND svTOT_actual < 0.6
```

### Acciones:

* `timeoutFalling = 50` (1 segundo)
* `falling[t] = 1.0`
* Captura de orientación previa: promedio de `xLPF`, `yLPF`, `zLPF` de los últimos 400ms

---

## 5. Detección de Impacto

### Pre-condición:

* `timeoutFalling > -1` (dentro del 1s posterior a la caída libre)

### Condiciones (cualquiera de estas):

```python
svTOT ≥ 2.0  OR
svD ≥ 1.7    OR
svMaxMin ≥ 2.0  OR
z2 ≥ 1.5
```

### Acciones:

* `timeoutImpact = 100` (2 segundos)
* `impact[t] = 1.0`

---

## 6. Confirmación de Caída (Lying)

### Pre-condición:

* `timeoutImpact == 0` (exactamente 2s después del impacto)

### Acciones:

* Captura de orientación posterior: promedio de `xLPF`, `yLPF`, `zLPF` de los últimos 400ms
* Cálculo de cambio de orientación:

```python
dx = |orientacion_antes.x - orientacion_despues.x|
dy = |orientacion_antes.y - orientacion_despues.y|
dz = |orientacion_antes.z - orientacion_despues.z|
```

### Condición final:

```python
dx > 0.7  OR  dy > 0.7  OR  dz > 0.7
```

Si se cumple:

* `lying[t] = 1.0`
* Se activa `FallAlertActivity`

---

## Resumen de la Secuencia Temporal

```python
t = 0     # Inicio del monitoreo
t = X     # svTOT cae por debajo de 0.6 → Inicio de detección de caída
t = X+1s  # Ventana para detectar impacto
t = Y     # Se detecta impacto (algún umbral superado)
t = Y+2s  # Evaluación de cambio de orientación
          # Si cambio > 0.7 en algún eje → CAÍDA CONFIRMADA
```

---

## Valores de Umbral Utilizados

| Parámetro                 | Valor | Descripción                          |
| ------------------------- | ----- | ------------------------------------ |
| `FALLING_WAIST_SV_TOT`    | 0.6   | Umbral caída libre                   |
| `IMPACT_WAIST_SV_TOT`     | 2.0   | Umbral impacto (magnitud total)      |
| `IMPACT_WAIST_SV_D`       | 1.7   | Umbral impacto (componente dinámica) |
| `IMPACT_WAIST_SV_MAX_MIN` | 2.0   | Umbral impacto (diferencia max-mín)  |
| `IMPACT_WAIST_Z_2`        | 1.5   | Umbral impacto (componente Z)        |
| `ORIENTATION_THRESHOLD`   | 0.7   | Umbral de cambio de orientación      |


## Notas
