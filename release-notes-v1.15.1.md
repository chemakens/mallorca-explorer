# Mallorca Explorer v1.15.1 (Build 46)

## 🐛 Correcciones de errores

### Google Sign-In Fix
- **Problema resuelto**: El botón "Iniciar sesión con Google" no respondía al pulsarlo
- **Causa**: El Context pasado al Credential Manager API era ApplicationContext en lugar de Activity Context
- **Solución**: Implementada función `findActivity()` para extraer el Activity correcto desde cualquier Context
- **Impacto**: Google Sign-In ahora funciona correctamente en todos los dispositivos

## 🔧 Cambios técnicos

### AuthRepositoryImpl.kt
- Agregada función de extensión `Context.findActivity()` para navegar la cadena de ContextWrapper
- Mejorados logs de debugging con Timber para facilitar troubleshooting
- Validación mejorada del Activity Context antes de invocar CredentialManager API

### Verificado
- ✅ Login con Google funciona correctamente
- ✅ Sincronización de datos desde Firestore tras login
- ✅ Manejo correcto de errores y cancelaciones
- ✅ Logs informativos para debugging

## 📦 Detalles del build

- **Version**: 1.15.1
- **Version Code**: 46
- **Build anterior**: 1.15.0 (45)
- **Tamaño AAB**: 182 MB
- **SHA-256**: `00b90445a4cb88be6a67ca378217aa4e2c019ebb716d70207b501697962c0b13`
- **Firmado**: ✅ Con keystore de release
- **Fecha**: 2026-09-12

## 📝 Notas para Google Play Console

**ES:**
```
Corrección de error crítico: El botón "Iniciar sesión con Google" ahora funciona correctamente. Se ha solucionado un problema técnico que impedía el inicio de sesión con cuentas de Google.
```

**EN:**
```
Critical bug fix: The "Sign in with Google" button now works correctly. A technical issue preventing Google account sign-in has been resolved.
```

**DE:**
```
Kritischer Bugfix: Die Schaltfläche "Mit Google anmelden" funktioniert jetzt korrekt. Ein technisches Problem, das die Anmeldung mit Google-Konten verhinderte, wurde behoben.
```

## 🚀 Próximos pasos

1. Subir `app-release.aab` a Google Play Console
2. Crear una versión interna/beta para testing
3. Verificar que el login funciona en producción
4. Promover a producción cuando esté validado

---

**Archivo generado**: `2026-09-12 16:50`
