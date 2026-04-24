# FIRMAGOB CLI

FIRMAGOB CLI es una herramienta de línea de comandos diseñada para la firma electrónica de documentos PDF a través de la API de Firma.gob (Secretaría de Gobierno Digital de Chile). Permite realizar firmas en modalidades Desatendida y Atendida (OTP), inyectando una representación visual (logo, datos y fecha) al fondo de la última página del documento.

## Requisitos previos

* Java 17 o superior.
* Apache Maven.
* Credenciales de acceso a la API (Sandbox o Producción).

---

## Configuración inicial

Para facilitar el uso diario y evitar ingresar las credenciales en cada ejecución, se recomienda configurar las constantes directamente en el script de Bash.

1. Configurar credenciales en el script
Abra el archivo /usr/local/bin/firmagob y localice la sección de valores por defecto. Reemplace los valores con sus credenciales permanentes:

    SECRET="su_secret_key_aqui"
    TOKEN="su_api_token_key_aqui"
    URL="[https://api.firma.cert.digital.gob.cl/firma/v2/files/tickets](https://api.firma.cert.digital.gob.cl/firma/v2/files/tickets)"
    ENTIDAD="Nombre de su Institución"

2. Preparar el núcleo Java
Desde la carpeta del proyecto Java, compile los archivos necesarios:

    mvn clean compile

3. Asignar permisos de ejecución
Asegúrese de que el sistema pueda ejecutar el comando:

    sudo chmod +x /usr/local/bin/firmagob

---

## Uso del comando

La sintaxis básica es:

    firmagob <archivo.pdf> [opciones]

(Nota: El sistema valida que el archivo de entrada exista físicamente y sea un documento PDF).

### Banderas de comando (Flags)

Si los valores ya están definidos en el script de Bash, no es necesario usar estas banderas a menos que desee sobrescribirlos para una ejecución específica.

-r, --rut         : RUN del firmante (sin puntos ni guion).
-o, --otp         : Código OTP de 6 dígitos (Activa modo Atendido).
-l, --logo        : Ruta absoluta/relativa de la imagen. Defecto: firma.png.
    --reemplazar  : Sobrescribe el archivo PDF original.
-s, --secret      : Sobrescribe la Secret Key configurada.
-t, --token       : Sobrescribe el API Token Key configurado.
-p, --proposito   : Propósito de la firma (Desatendido/Propósito General).
-e, --entidad     : Sobrescribe el nombre de la entidad en el estampado.
-n, --nombre      : Define el nombre interno del campo de firma.
-h, --help        : Muestra el menú de ayuda.

---

## Ejemplos de uso

Firma Desatendida Básica:

    firmagob documento.pdf

Firma Atendida (OTP):

    firmagob contrato.pdf --rut 11111111 --otp 123456

Firma sobrescribiendo el archivo y con logo personalizado:

    firmagob certificado.pdf --reemplazar -l "/ruta/absoluta/mi_logo.png"

Firma con parámetros personalizados completos:

    firmagob oficio.pdf --rut 12345678 --proposito "Firma de Gabinete" --nombre "Firma_Especial"

---

## Notas adicionales

* Estampado Visual: El CLI inyecta automáticamente un bloque visual en la última página.
* Salida por Defecto: Si no se usa --reemplazar, genera un archivo _firmado_HH_mm_ss.pdf.
* Radiografía de Ejecución: El programa imprime un desglose detallado útil para depurar.
* Seguridad: Evite subir el script de Bash con sus llaves a repositorios públicos.
* Errores 400: Verifique que el RUT y Propósito coincidan con los de Firma.gob.
