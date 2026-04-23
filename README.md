# FIRMAGOB CLI

FIRMAGOB CLI es una herramienta de línea de comandos diseñada para la firma electrónica de documentos PDF a través de la API de Firma.gob (Secretaría de Gobierno Digital de Chile). Permite realizar firmas en modalidades Desatendida y Atendida (OTP).

# Requisitos previos

    Java 17 o superior.

    Apache Maven.

    Credenciales de acceso a la API (Sandbox o Producción).

# Configuración inicial

Para facilitar el uso diario y evitar ingresar las credenciales en cada ejecución, se recomienda configurar las constantes directamente en el script de Bash.
1. Configurar credenciales en el script

    Abra el archivo /usr/local/bin/firmagob y localice la sección de valores por defecto. Reemplace los valores con sus credenciales permanentes:


Se recomienda dejar configurados estos valores una sola vez
SECRET="su_secret_key_aqui"
TOKEN="su_api_token_key_aqui"
URL="https://api.firma.cert.digital.gob.cl/firma/v2/files/tickets"
ENTIDAD="Nombre de su Institución"

2. Preparar el núcleo Java

    Desde la carpeta del proyecto Java, compile los archivos necesarios:

        mvn clean compile
3. Asignar permisos de ejecución

    Asegúrese de que el sistema pueda ejecutar el comando:


        sudo chmod +x /usr/local/bin/firmagob

# Uso del comando

La sintaxis básica es:

    firmagob <archivo.pdf> [opciones]

# Banderas de comando (Flags)

Si los valores ya están definidos en el script de Bash, no es necesario usar estas banderas a menos que desee sobrescribirlos para una ejecución específica.

    Opción                  Descripción

    -r	--rut	        RUN del firmante (sin puntos ni guion).

    -o	--otp	        Código OTP de 6 dígitos (Activa modo Atendido).

    -s	--secret	Sobrescribe la Secret Key configurada.

    -t	--token	        Sobrescribe el API Token Key configurado.

    -p	--proposito	Propósito de la firma (Desatendido/Propósito General).

    -e	--entidad	Sobrescribe el nombre de la entidad.

    -n	--nombre	Define el nombre interno del campo de firma.

    -h	--help	        Muestra el menú de ayuda.

# Ejemplos de uso
## Firma Desatendida

    Si ya configuró el script con su Secret y Token, solo necesita el nombre del archivo:

firmagob documento.pdf

# Firma Atendida (OTP)

Para firmar usando el código de la aplicación móvil:

    firmagob contrato.pdf --rut 11111111 --otp 123456

    Firma con parámetros personalizados

    firmagob oficio.pdf --rut 12345678 --proposito "Firma de Gabinete" --nombre "Firma_Especial"

Notas adicionales

Seguridad: Al configurar las llaves dentro del script de Bash, evite compartir el archivo o subirlo a repositorios públicos.

Salida: El sistema genera automáticamente una copia del archivo con el sufijo _firmado.pdf en la misma ubicación del archivo original.

Errores: En caso de recibir un Error 400 de la API, verifique que el RUT y el Propósito coincidan exactamente con lo registrado en su cuenta de Firma.gob.