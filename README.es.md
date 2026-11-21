# Photos Unlimited

[English](README.md) · [Português (Brasil)](README.pt-BR.md) · **Español** ·
[简体中文](README.zh-Hans.md) · [日本語](README.ja.md)

*Esta traducción fue hecha con un modelo de lenguaje y puede ir por detrás del README en inglés, que es la referencia.*

Un pequeño cargador independiente para Android que sube archivos a una biblioteca de
Google Fotos. Elige carpetas, haz copias de seguridad de las fotos y los videos originales
y sigue usando la app oficial de Google Fotos para explorar y editar tu biblioteca.

**Android 13+ · Licencia MIT**

[Sitio web](https://venzellucas.github.io/photos-unlimited/) ·
[Descargar APK](https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk) ·
[Notas de la versión](https://github.com/venzellucas/photos-unlimited/releases/latest)

**Verifica el APK.** Cada versión está firmada con el certificado cuya huella SHA-256
es
`cbd14272c6aaa0568df8cc8be4ec10be9c6a2ebc0354eb202b5a05d8cda74bce`. Android rechaza
cualquier actualización que no esté firmada con esta clave. Además, cada versión incluye
la suma de verificación del APK en `SHA256SUMS.txt`. Para informar de un problema de
seguridad, consulta [SECURITY.md](SECURITY.md).

## Qué hace

- Seleccionar carpetas de fotos y videos para la copia de seguridad de los archivos
  originales.
- Ejecutar copias de seguridad manuales o dejar que Android programe el trabajo
  automático, con preferencias de red sin límite de datos y de carga de la batería.
- Ver el progreso del archivo actual, el historial de subidas, las coincidencias ya
  existentes y los archivos que necesitan un reintento.
- Usar pantallas nativas de Material, temas claro y oscuro, colores del fondo de pantalla,
  un ícono adaptable y la foto de perfil de Google en caché cuando esté disponible.
- Analizar los metadatos de Google Fotos para inspeccionar el uso de almacenamiento
  informado, con filtros por tipo de contenido y por almacenamiento cobrado o desconocido.
  Esta función es una vista previa experimental.
- Usar la app en English, Português (Brasil), Español, 简体中文 o 日本語. Sigue el idioma
  del teléfono; elige otro en Ajustes → **Idioma**.

No se necesita root, ROM personalizada, microG, clave de API ni servidor de Photos
Unlimited.

## Instalar y conectar

1. Descarga el APK firmado de la versión. Es posible que Android te pida permitir la
   instalación desde tu navegador. Instálalo sobre una versión existente para conservar
   tu configuración.
2. Conecta tu cuenta mediante la página de configuración de Google en la app. Si Google
   rechaza el inicio de sesión integrado, usa la alternativa del token de configuración
   en el navegador que se describe en la pantalla de conexión. Introduce ese token solo
   en la app; nunca lo publiques en una incidencia ni en un mensaje.
3. Permite el acceso a todas las fotos y videos y a la ubicación del contenido
   multimedia. Esta última es necesaria para leer los bytes completos del archivo
   original, incluida la ubicación incrustada.
4. Elige las carpetas. Los archivos que ya estén en ellas se incluyen en la primera
   copia de seguridad.
5. Desactiva la copia de seguridad automática en la app oficial de Google Fotos y luego
   toca **Hacer copia ahora**. Esto también activa la copia de seguridad automática en
   Photos Unlimited.

La app consulta GitHub una vez al día mientras está abierta y muestra una tarjeta cuando
hay una versión nueva. Desactiva esto en Ajustes → **Buscar actualizaciones**. Si el
botón de la cuenta muestra una inicial, ábrelo y elige **Actualizar foto de perfil**. La
consulta del perfil usa un token independiente de solo lectura, como máximo una vez al
día; si falla, la credencial de subida no se invalida. El selector avanzado de cuentas del
teléfono es una herramienta de diagnóstico, no una alternativa de inicio de sesión para la
copia de seguridad.

## Límites del MVP

La app usa el protocolo móvil no documentado de Google, adaptado de GPMC y gotohp de
xob0t. Solicita el tratamiento de calidad original con un perfil de subida de Pixel.
**Una subida aceptada no es una prueba de exención de cuota.** Google controla la
contabilidad del almacenamiento y puede cambiar o rechazar los métodos. Verifica subidas
de muestra nuevas, los originales descargables y el uso real de la cuota antes de confiar
en la app. Los elementos que ya existen en la biblioteca y coinciden se omiten y
conservan su contabilidad.

El análisis de almacenamiento abarca Google Fotos, no Drive ni Gmail. Un valor
desconocido no es cero. El total suma los bytes cobrados informados de los registros
analizados y puede diferir del uso de toda la cuenta. Los elementos compartidos, las
versiones alternativas, las omisiones y los cambios simultáneos pueden afectar la
cobertura. La contabilidad en vivo no está totalmente validada.

## Liberar espacio

Los elementos que ya usan almacenamiento de Google pueden reemplazarse por una copia
subida con esta app: toca un elemento en **Almacenamiento** o selecciona varios
(**Seleccionar todos los aptos** funciona en todas las páginas). Google identifica el
contenido por su hash y lo elimina con la misma clave, así que el original debe haber
desaparecido antes de que pueda existir un reemplazo idéntico. Por eso cada elemento:

1. Se comprueba con el registro de Google (ningún otro elemento puede compartir sus
   bytes).
2. Obtiene una copia del teléfono si está presente; si no, se descarga de Google, y solo
   se acepta si su SHA-1 es igual al hash que Google registró para la subida original.
   Esto demuestra que el archivo es idéntico byte a byte, de modo que los metadatos
   incrustados (fecha EXIF, GPS, cámara, ubicación del video) se conservan exactamente.
3. Se mueve a la Papelera y se elimina de forma permanente; después, la copia se sube de
   nuevo.
4. Se confirma solo cuando Google informa de 0 bytes cobrados para el reemplazo; se
   restauran el pie de foto, los favoritos y el archivado.

Propiedades de seguridad:

- Cada paso se registra en un diario antes de ejecutarse; si hay una caída, se reanuda de
  forma segura. Si la copia cambia antes de la eliminación, el original se restaura desde
  la Papelera. Un reemplazo cobrado o cualquier elemento que requiera atención detiene
  toda la ejecución.
- La copia se guarda en `Pictures/Photos Unlimited/Pending upload/`, no en el
  almacenamiento privado de la app, de modo que desinstalar o borrar los datos de la app
  no puede hacerla perder. Los nombres de archivo incluyen el SHA-1 y un hash corto de la
  cuenta; una instalación nueva comprueba cada archivo sobrante por su hash y lo termina
  (nunca en una cuenta distinta). Solo los archivos que escribió esta app se terminan
  automáticamente; los archivos dejados por una instalación anterior se muestran primero
  para que los apruebes, y los archivos escritos por otras apps se ignoran. Una copia
  preparada se elimina únicamente cuando Google muestra un elemento activo, fuera de la
  Papelera, con la misma suma de verificación. Una notificación advierte que no se debe
  desinstalar mientras algo esté solo en el teléfono, los cambios de cuenta esperan hasta
  que termine y el cuadro de desinstalación de Android ofrece conservar los datos.
- Justo antes de eliminar, el elemento se consulta de nuevo y debe seguir siendo elegible,
  el traslado a la Papelera debe verse en el registro de Google y solo un elemento a la
  vez está entre la eliminación y un reemplazo confirmado.
- **Desactiva primero la copia de seguridad de Google Fotos.** Mientras esté activada,
  Google Fotos elimina la copia en el teléfono de todo lo que se quite de la nube, en
  todos los dispositivos de la cuenta
  ([Ayuda de Google](https://support.google.com/photos/answer/6128858)). Liberar espacio
  lo pide y, como red de seguridad, devuelve al teléfono una copia eliminada, idéntica
  byte a byte.
- Los elementos de **Ahorro de espacio** se reemplazan por el original solo cuando el
  archivo original sigue en el teléfono (demostrado por el hash registrado por Google); la
  copia reducida de Google nunca se usa en su lugar. Esto también mejora las subidas
  reducidas gratuitas anteriores a 2021.
- Las **fotos con movimiento** (Pixel, Samsung) son archivos únicos y conservan su
  movimiento; Google vuelve a extraer el mismo video incrustado. Las **Live Photos de
  iPhone** guardan su video en un archivo aparte, por lo que se rechazan en lugar de
  perder el movimiento.

Se pierde con el reemplazo: álbumes, copias en álbumes compartidos, comentarios, grupos de
rostros y las ediciones de fecha, ubicación u otras hechas dentro de Google Fotos. Otros
dispositivos con la copia de seguridad de Google Fotos activada aún pueden eliminar sus
copias. Probado en vivo con una cuenta de prueba (fotos, videos, elementos de Ahorro de
espacio, una foto con movimiento y un video detenido a mitad de la ejecución que se
recuperó tras borrar los datos de la app); aún no se ha probado con una biblioteca real
grande.

### Recordatorio sobre la copia de seguridad de Google Fotos

Dos apps de copia de seguridad en un mismo teléfono compiten entre sí, y las copias de
Google Fotos usan almacenamiento. Android no permite que las apps lean los ajustes de
Google Fotos, así que mientras la copia automática está activada, la app pregunta cada vez
que se abre hasta que confirmes que la copia de seguridad de Google Fotos está
desactivada, con un enlace directo a su página de Copia de seguridad, y vuelve a
preguntar solo si más adelante ve que otra app subió antes una foto nueva.

Android controla la programación en segundo plano. Las reglas de red, batería, carga e
inactividad pueden retrasar el trabajo. Forzar la detención de la app detiene el trabajo
automático hasta que se vuelva a abrir. Las transferencias de archivos interrumpidas
reinician el archivo. Los videos muy grandes pueden necesitar una ejecución manual con una
conexión estable. RAW, HDR y las ediciones requieren una validación específica de cada
formato; los archivos separados de foto y video (Live Photos de iPhone) no se ensamblan
en pares.

## Privacidad

Consulta [PRIVACY.md](PRIVACY.md). Las credenciales se cifran en el almacenamiento
privado de la app; las fotos se envían directamente a Google. No existe ningún servidor de
cuentas propiedad del proyecto. Nunca incluyas credenciales, cookies, tokens de
configuración ni fotos privadas en los informes de errores.

## Compilación

Usa JDK 21 y la plataforma y herramientas de compilación 37 del SDK de Android. Configura
`ANDROID_HOME` o un archivo `local.properties` sin versionar. El repositorio incluye un
wrapper de Gradle.

```sh
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El APK de depuración está en `app/build/outputs/apk/debug/`. Las compilaciones de
depuración no pueden actualizar una versión firmada instalada. Ejecuta las pruebas de
instrumentación solo en un emulador de prueba desechable:

```sh
ANDROID_SERIAL=YOUR_TEST_EMULATOR ./gradlew :app:connectedDebugAndroidTest
```

Para las compilaciones de versión, proporciona un almacén de claves externo mediante
`PHOTOS_KEYSTORE` y `PHOTOS_SIGN_PASSWORD`, define `ANDROID_HOME` y ejecuta
`RELEASE_TAG=vX.Y.Z tools/build-release.sh`, donde `X.Y.Z` es el `versionName` de
`app/build.gradle`. El APK y su `SHA256SUMS.txt` se escriben en `dist/X.Y.Z/`. Nunca
subas estos valores ni el almacén de claves al repositorio. Conserva la clave de firma de
la versión entre actualizaciones.

Las máscaras de la biblioteca pueden regenerarse con `tools/generate-library-templates.py`
usando `blackboxprotobuf` y la copia fijada de GPMC indicada más abajo.

## Validación

Cada versión supera las suites de JVM y de instrumentación de Android, lint sin errores y
una actualización firmada in situ desde la versión anterior. La versión 1.0.0 también tuvo
una revisión de seguridad independiente (con los hallazgos corregidos antes de publicarla)
y ejecuciones reales de Liberar espacio como se describe arriba. Las pruebas cubren el
manejo del protocolo, las subidas confirmadas, la interrupción, las colas persistentes, la
separación de cuentas, la paginación de metadatos, la apariencia y el aislamiento de la
caché del perfil. Las credenciales y las respuestas de red de las pruebas son sintéticas.
Se inspeccionaron las pantallas en modo claro y oscuro y con texto grande. Estas
comprobaciones no demuestran la exención real de cuota ni una contabilidad completa de la
biblioteca.

## Traducciones

La app, este README y el sitio web están disponibles en inglés, portugués de Brasil,
español, chino simplificado y japonés. Las traducciones se hicieron con un modelo de
lenguaje y pueden ir por detrás del texto en inglés, que es la referencia. Las
correcciones son bienvenidas como pull requests: el texto de la app está en
`app/src/main/res/values-*/strings*.xml`, y los README y las páginas del sitio traducidos
están junto a los ingleses.

## Créditos y licencia

Adaptaciones del protocolo con licencia MIT:

- [xob0t/gpmc](https://github.com/xob0t/gpmc), revisión
  `94b1b267ca6819da542d4330a7dd216747eb0437`.
- [xob0t/gotohp](https://github.com/xob0t/gotohp), revisión
  `2a5c1b9678281b6a2ff08661a53ea1dc6b1e2edc`.

El aviso original se conserva en
[app/src/main/assets/XOB0T-LICENSE.txt](app/src/main/assets/XOB0T-LICENSE.txt) y en la
pantalla Acerca de de la app. Android y las bibliotecas incluidas conservan sus propias
licencias. Consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Google, Google Fotos, Android y Pixel son marcas comerciales de sus respectivos
propietarios. Photos Unlimited es independiente y no está afiliado a Google.
