@echo off
setlocal enabledelayedexpansion

:: ======================================================================
:: CONFIGURACIÓN POR DEFECTO (Edita estos valores para tu uso diario)
:: ======================================================================
set SECRET="tu_secret_key_aqui"
set TOKEN="tu_api_token_key_aqui"
set URL="https://api.firma.cert.digital.gob.cl/firma/v2/files/tickets"
set ENTIDAD="Subsecretaría General de la Presidencia"
set PROPOSITO="Desatendido"
set NOMBRE_FIRMA="FirmaGobierno"
:: ======================================================================

set RUN=""
set OTP=""
set LOGO="firma.png"
set REEMPLAZAR="false"
set PDF=""

:parse
if "%~1"=="" goto end_parse
if "%~1"=="-r" set RUN=%~2& shift & shift & goto parse
if "%~1"=="--rut" set RUN=%~2& shift & shift & goto parse
if "%~1"=="-o" set OTP=%~2& shift & shift & goto parse
if "%~1"=="--otp" set OTP=%~2& shift & shift & goto parse
if "%~1"=="-l" set LOGO=%~2& shift & shift & goto parse
if "%~1"=="--logo" set LOGO=%~2& shift & shift & goto parse
if "%~1"=="-s" set SECRET=%~2& shift & shift & goto parse
if "%~1"=="--secret" set SECRET=%~2& shift & shift & goto parse
if "%~1"=="-t" set TOKEN=%~2& shift & shift & goto parse
if "%~1"=="--token" set TOKEN=%~2& shift & shift & goto parse
if "%~1"=="-e" set ENTIDAD=%~2& shift & shift & goto parse
if "%~1"=="--entidad" set ENTIDAD=%~2& shift & shift & goto parse
if "%~1"=="-n" set NOMBRE_FIRMA=%~2& shift & shift & goto parse
if "%~1"=="--nombre" set NOMBRE_FIRMA=%~2& shift & shift & goto parse
if "%~1"=="-p" set PROPOSITO=%~2& shift & shift & goto parse
if "%~1"=="--proposito" set PROPOSITO=%~2& shift & shift & goto parse
if "%~1"=="--reemplazar" set REEMPLAZAR="true"& shift & goto parse
if "%~1"=="-h" goto help
if "%~1"=="--help" goto help

set PDF=%~1
shift
goto parse

:help
echo Uso: firmagob ^<archivo.pdf^> [opciones]
echo Descripción:
echo   Firma electrónicamente un documento PDF usando la API de FirmaGob.
echo.
echo Opciones:
echo   -r, --rut        RUN del firmante
echo   -o, --otp        Codigo OTP
echo   -l, --logo       Ruta al logo (.png)
echo   -s, --secret     Sobrescribir Secret Key
echo   -t, --token      Sobrescribir API Token
echo   -e, --entidad    Nombre de la entidad en el sello
echo   -p, --proposito  Proposito de la firma
echo   --reemplazar     No crear copia, modificar original
goto :eof

:end_parse
:: Ajusta esta ruta a la ubicacion real de tu JAR
set JAR_PATH="C:\workspaces\firmaGobTest\firma-java\target\FirmaInyector-1.0-SNAPSHOT.jar"

java -Df.secret=%SECRET% -Df.token=%TOKEN% -Df.url=%URL% -Df.run=%RUN% -Df.entidad=%ENTIDAD% -Df.proposito=%PROPOSITO% -Df.otp=%OTP% -Df.logo=%LOGO% -Df.reemplazar=%REEMPLAZAR% -Df.nombre=%NOMBRE_FIRMA% -jar %JAR_PATH% %PDF%

endlocal