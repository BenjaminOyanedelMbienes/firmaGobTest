# MANUAL DE USUARIO: FIRMAGOB CLI (MULTI-PLATAFORMA)

Esta herramienta permite firmar documentos PDF usando la API de Firma.gob.cl,
agregando un estampado visual en la última página del documento.


## PERSONALIZACIÓN (IMPORTANTE)

Tanto el script de Linux (firmagob) como el de Windows (firmagob.bat) tienen 
una sección llamada "CONFIGURACIÓN POR DEFECTO". 

Para evitar escribir las llaves secretas en cada firma, abra el script con un 
editor de texto y modifique las siguientes variables con sus datos reales:

    - SECRET: Su Secret Key proporcionada por el gobierno.
    - TOKEN: Su API Token Key.
    - ENTIDAD: El nombre que aparecerá en el sello (ej: "Municipalidad de...").


---

##  INSTALACIÓN EN WINDOWS

1. Compilación: Ejecute el siguiente comando en la carpeta del proyecto Java.

        mvn clean package
2. Carpeta de Scripts: Cree una carpeta como C:\Scripts y guarde ahí el 
   archivo 'firmagob.bat'.
3. JAR_PATH: Edite el archivo 'firmagob.bat' y asegúrese de que la variable 
   JAR_PATH apunte a la ubicación real del archivo .jar generado.
4. PATH: Agregue C:\Scripts a las Variables de Entorno de su sistema.



## 3. INSTALACIÓN EN LINUX / CODESPACES

1. Compilación: 
        
        mvn clean package
2. Script: Guarde el código en /usr/local/bin/firmagob.
3. Permisos: Ejecute 

        sudo chmod +x /usr/local/bin/firmagob



## 4. COMANDOS Y BANDERAS

Sintaxis: firmagob <archivo.pdf> [opciones]

Las banderas permiten sobrescribir cualquier configuración fija del script:

    -r, --rut        : RUN sin puntos ni guion (Obligatorio si no está fijo).
    -o, --otp        : Código OTP (Activa firma atendida).
    -l, --logo       : Ruta del archivo .png para el sello.
    --reemplazar     : Modifica el archivo original en lugar de crear copia.
    -s, --secret     : Sobrescribe la Secret Key (Uso bajo propio riesgo).
    -t, --token      : Sobrescribe el API Token.
    -e, --entidad    : Cambia el nombre de la institución en el sello.
    -p, --proposito  : Cambia el propósito de la firma.



## 5. EJEMPLOS

Uso estándar (Windows):
    
    firmagob mi_documento.pdf --rut 12345678 --reemplazar

Uso con logo distinto:
    
    firmagob oficio.pdf -r 22222222 -l "C:\logos\logo_especial.png"

Uso en una entidad distinta (sobrescribiendo el default):
    
    firmagob doc.pdf -r 11111111 -e "Ministerio de Hacienda"
