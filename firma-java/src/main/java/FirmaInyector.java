import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.itextpdf.signatures.*;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.geom.Rectangle; // NUEVO: Para definir la posición física
import com.itextpdf.io.image.ImageDataFactory; // NUEVO: Para cargar el logo
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.time.Instant;
import java.time.LocalTime; // NUEVO: Para el nombre del archivo
import java.time.format.DateTimeFormatter; // NUEVO: Para el formato de hora
import java.time.temporal.ChronoUnit;

public class FirmaInyector {

    private static String SECRET_KEY;
    private static String API_TOKEN_KEY;
    private static String API_URL;
    private static String RUN;
    private static String ENTIDAD;
    private static String NOMBRE_FIRMA;
    private static String PROPOSITO;
    private static String valorOTP;
    private static String RUTA_LOGO; // NUEVO: Ruta de la imagen
    private static boolean usaOTP = false;
    private static boolean REEMPLAZAR = false; // NUEVO: Flag de reemplazo

    public static void main(String[] args) {
        System.out.println("====== Iniciando proceso de firma ======");
        System.out.println("Cargando configuraciones y validando archivos...");

        SECRET_KEY = System.getProperty("f.secret");
        API_TOKEN_KEY = System.getProperty("f.token");
        API_URL = System.getProperty("f.url");
        RUN = System.getProperty("f.run");
        ENTIDAD = System.getProperty("f.entidad");
        PROPOSITO = System.getProperty("f.proposito");
        valorOTP = System.getProperty("f.otp", "");
        NOMBRE_FIRMA = System.getProperty("f.nombre", "FirmaGobierno_" + System.currentTimeMillis());
        
        RUTA_LOGO = System.getProperty("f.logo", "firma.png"); 
        REEMPLAZAR = Boolean.parseBoolean(System.getProperty("f.reemplazar", "false"));

        imprimirVariablesDeEntrada(args);
        validarArchivos(args, RUTA_LOGO);

        File logoFile = new File(RUTA_LOGO).getAbsoluteFile();
        if (!logoFile.exists()) {
            System.err.println("Error: El logo '" + logoFile.getAbsolutePath() + "' no existe.");
            System.exit(1);
        }

        if (valorOTP != null && !valorOTP.isEmpty()) {
            usaOTP = true;
        }

        if (args.length < 1) {
            System.err.println("Uso: firmagob <archivo.pdf> [otp]");
            System.exit(1);
        }

        File archivoOriginal = new File(args[0]).getAbsoluteFile();
        if (!archivoOriginal.exists()) {
            System.err.println("Error: El archivo " + args[0] + " no existe.");
            System.exit(1);
        }

        String nombreOriginal = archivoOriginal.getName();
        int dotIndex = nombreOriginal.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? nombreOriginal : nombreOriginal.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : nombreOriginal.substring(dotIndex);

        File archivoFirmado;
        if (REEMPLAZAR) {
            archivoFirmado = new File(archivoOriginal.getParent(), baseName + "_temp" + extension);
        } else {
            String timeStamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH_mm_ss"));
            archivoFirmado = new File(archivoOriginal.getParent(), baseName + "_firmado_" + timeStamp + extension);
        }
        
        File tempAgujero = new File(archivoOriginal.getParent(), ".temp_hole_" + nombreOriginal);

        try {
            System.out.println("Analizando PDF y creando espacio visual para la firma...");
            crearAgujeroPdf(archivoOriginal.getAbsolutePath(), tempAgujero.getAbsolutePath(), NOMBRE_FIRMA);

            System.out.println("Calculando la huella digital (Hash) del documento...");
            byte[] hashBytes = obtenerHashDelAgujero(tempAgujero.getAbsolutePath(), NOMBRE_FIRMA);
            String hashHex = bytesToHex(hashBytes);
            String hashBase64 = Base64.getEncoder().encodeToString(hashBytes);
            
            System.out.println("Generando Token y solicitando firma a Firma.gob...");
            String jwtToken = generarToken();
            String p7sBase64 = solicitarFirmaAPI(jwtToken, hashHex, hashBase64);

            if (p7sBase64 != null) {
                System.out.println("Respuesta recibida. Inyectando firma criptográfica en el PDF...");
                inyectarP7S(tempAgujero.getAbsolutePath(), archivoFirmado.getAbsolutePath(), NOMBRE_FIRMA, p7sBase64);
                
                if (REEMPLAZAR) {
                    Files.move(archivoFirmado.toPath(), archivoOriginal.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[ÉXITO] Documento firmado y reemplazado: " + archivoOriginal.getAbsolutePath());
                } else {
                    System.out.println("[ÉXITO] Documento firmado guardado como: " + archivoFirmado.getAbsolutePath());
                }
            } else {
                System.out.println("[ERROR] La firma devuelta por la API es null.");
            }

        } catch (Exception e) {
            System.err.println("[ERROR CRÍTICO] " + e.getMessage());
            e.printStackTrace(); // Opcional: Imprime el error exacto si vuelve a fallar
        } finally {
            System.out.println("Limpiando archivos temporales de trabajo.");
            if (tempAgujero.exists()) tempAgujero.delete();
            System.out.println("====== Proceso terminado ======");
        }
    }

    // --- MÉTODOS DE APOYO ---

    public static void crearAgujeroPdf(String src, String dest, String fieldName) throws Exception {
        PdfReader reader = new PdfReader(src);
        
        PdfDocument docRef = new PdfDocument(new PdfReader(src));
        int ultimaPagina = docRef.getNumberOfPages();
        docRef.close();

        PdfSigner signer = new PdfSigner(reader, new FileOutputStream(dest), new StampingProperties().useAppendMode());

        // Configuramos el nombre del campo interno
        signer.setFieldName(fieldName);

        // Obtenemos el controlador visual de la firma
        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        
        // Definimos la posición y página
        Rectangle rect = new Rectangle(36, 36, 200, 80);
        appearance.setPageRect(rect);
        appearance.setPageNumber(ultimaPagina);
        
        // Establecemos que queremos Logo + Texto
        appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.GRAPHIC_AND_DESCRIPTION);
        
        // Cargamos la imagen de forma segura con la ruta absoluta
        File logoF = new File(RUTA_LOGO).getAbsoluteFile();
        System.out.println("ruta: " + RUTA_LOGO);
        appearance.setSignatureGraphic(ImageDataFactory.create(logoF.getAbsolutePath()));
        

        // Formateamos la fecha para que sea legible y asignamos el texto
        String fechaLegible = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String textoFirma = "Firmado por: " + RUN + "\nEntidad: " + ENTIDAD + "\nFecha: " + fechaLegible;
        appearance.setLayer2Text(textoFirma);

        // Hace el espacio necesario para la firma criptográfica (agujero)
        IExternalSignatureContainer blank = new ExternalBlankSignatureContainer(PdfName.Adobe_PPKLite, PdfName.Adbe_pkcs7_detached);
        signer.signExternalContainer(blank, 8192);
    }

    public static byte[] obtenerHashDelAgujero(String tempPdf, String fieldName) throws Exception {
        // Hacemos un doc nuevo
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(tempPdf));
        // Declaramos un utility a partir del doc
        SignatureUtil signUtil = new SignatureUtil(pdfDoc);
        // Sacamos el diccionario de firmas
        PdfDictionary sigDict = signUtil.getSignatureDictionary(fieldName);
        // Convertirlo a bytes
        PdfArray byteRange = sigDict.getAsArray(PdfName.ByteRange);
        //Los transformamos a números y los metemos en ranges
        long[] ranges = new long[4];
        for (int i = 0; i < 4; i++) { ranges[i] = byteRange.getAsNumber(i).longValue(); }
        pdfDoc.close();

        // Declaramos el hash y leemos en bloques de 8k para eficiencia
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (RandomAccessFile raf = new RandomAccessFile(tempPdf, "r")) {
            byte[] buf = new byte[8192];
            raf.seek(ranges[0]);
            long remaining = ranges[1];
            // Primera pasada hasta la sección de firmas
            while (remaining > 0) {
                int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read == -1) break;
                md.update(buf, 0, read);
                remaining -= read;
            }

            // Segunda pasada desde el final de firmas hasta el final del doc
            raf.seek(ranges[2]);
            remaining = ranges[3];
            while (remaining > 0) {
                int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read == -1) break;
                md.update(buf, 0, read);
                remaining -= read;
            }
        }

        // Alimentamos el hash con ambos y lo digerimos sin la firma
        return md.digest();
    }

    public static String generarToken() {
        // Ajuste de zona horaria para Chile (UTC-4 aprox)
        // La fecha de expiración es la actual + 30 minutos
       Instant expTime = Instant.now().minus(4, ChronoUnit.HOURS).plus(25, ChronoUnit.MINUTES);

        // Creamos el token y lo retornamos con la nueva fecha de expiración
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        return JWT.create()
                .withClaim("entity", ENTIDAD)
                .withClaim("run", RUN)
                .withClaim("purpose", PROPOSITO)
                .withClaim("expiration", expTime.toString().substring(0, 19)) 
                .sign(algorithm);
    }

    public static String solicitarFirmaAPI(String token, String hashHex, String hashBase64) throws Exception {
        // Generamos el JSON con el content, checksum y descripción
        JSONObject fileObj = new JSONObject();
        fileObj.put("content-type", "application/json");
        fileObj.put("content", hashBase64);
        fileObj.put("description", "Firma Automática");
        fileObj.put("checksum", hashHex);

        // Ponemos el token y el api token key 
        JSONObject payload = new JSONObject();
        payload.put("api_token_key", API_TOKEN_KEY);
        payload.put("token", token);
        payload.put("files", new JSONArray().put(fileObj));

        // Declaramos el request con los datos ya formados
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        // Aquí ponemos el OTP si es que lo usa
        if (usaOTP) requestBuilder.header("OTP", valorOTP);

        // Mandamos el request y esperamos la respuesta
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Si está ok lo retornamos, si no, paramos y mostramos el error con su codigo
        if (response.statusCode() == 200) {
            return new JSONObject(response.body()).getJSONArray("files").getJSONObject(0).getString("content");
        } else {
            System.out.println("API Error: " + response.body());
            throw new Exception("API Error: " + response.body());
        }
    }

    public static void inyectarP7S(String tempPdf, String destPdf, String fieldName, String p7sBase64) throws Exception {
        // Decodificamos el string p7s a bytes
        byte[] p7sBytes = Base64.getDecoder().decode(p7sBase64);
        // Copiamos el doc temporal a uno nuevo
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(tempPdf));
        // Inyectamos la firma en el agujero que habíamos hecho antes
        IExternalSignatureContainer container = new ExternalSignatureContainer(p7sBytes);
        PdfSigner.signDeferred(pdfDoc, fieldName, new FileOutputStream(destPdf), container);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) { sb.append(String.format("%02x", b)); }
        return sb.toString();
    }
    
    static class ExternalSignatureContainer implements IExternalSignatureContainer {
        private byte[] sig;
        public ExternalSignatureContainer(byte[] sig) { this.sig = sig; }
        @Override public byte[] sign(InputStream data) { return sig; }
        @Override public void modifySigningDictionary(PdfDictionary signDic) {}
    }


    // --- NUEVAS FUNCIONES DE DEBUG Y VALIDACIÓN ---

    private static void imprimirVariablesDeEntrada(String[] args) {
        System.out.println("\n=== RADIOGRAFÍA DE VARIABLES ===");
        // Esto te dirá exactamente dónde está parado Java
        System.out.println("DIRECTORIO TRABAJO : [" + System.getProperty("user.dir") + "]");
        System.out.println("Archivo PDF (args) : [" + (args.length > 0 ? args[0] : "VACÍO") + "]");
        
        // Calculamos la ruta que Java intentará usar para el logo
        File logoFile = new File(RUTA_LOGO).getAbsoluteFile();
        System.out.println("RUTA_LOGO (Texto)  : [" + RUTA_LOGO + "]");
        System.out.println("RUTA_LOGO (Final)  : [" + logoFile.getAbsolutePath() + "]");
        
        System.out.println("REEMPLAZAR         : [" + REEMPLAZAR + "]");
        System.out.println("RUN                : [" + RUN + "]");
        System.out.println("ENTIDAD            : [" + ENTIDAD + "]");
        System.out.println("PROPOSITO          : [" + PROPOSITO + "]");
        System.out.println("USA OTP            : [" + usaOTP + "] (Valor: [" + valorOTP + "])");
        System.out.println("API_URL            : [" + API_URL + "]");
        System.out.println("API_TOKEN_KEY      : [" + API_TOKEN_KEY + "]");
        
        String secretPrint = (SECRET_KEY != null && SECRET_KEY.length() > 4) 
                ? SECRET_KEY.substring(0, 4) + "..." : "VACÍO O MUY CORTO";
        System.out.println("SECRET_KEY         : [" + secretPrint + "]");
        System.out.println("NOMBRE_FIRMA       : [" + NOMBRE_FIRMA + "]");
        System.out.println("================================\n");
    }

    private static void validarArchivos(String[] args, String rutaLogo) {
        System.out.println("Validando existencia física de archivos...");
        
        // Validación de PDF
        if (args.length < 1 || args[0] == null || args[0].trim().isEmpty()) {
            System.out.println("[ERROR CRÍTICO] No se especificó el archivo PDF.");
            System.exit(1);
        }
        File pdf = new File(args[0]).getAbsoluteFile();
        if (!pdf.exists()) {
            System.out.println("[ERROR CRÍTICO] PDF NO ENCONTRADO en: " + pdf.getAbsolutePath());
            System.exit(1);
        }

        // Validación de Logo
        if (rutaLogo == null || rutaLogo.trim().isEmpty()) {
            System.out.println("[ERROR CRÍTICO] La variable LOGO llegó vacía.");
            System.exit(1);
        }
        File logo = new File(rutaLogo).getAbsoluteFile();
        if (!logo.exists()) {
            System.out.println("[ERROR CRÍTICO] LOGO NO ENCONTRADO.");
            System.out.println("   -> Java lo buscó en: " + logo.getAbsolutePath());
            System.out.println("   -> ¿Está el archivo ahí?");
            System.exit(1);
        }
        
        System.out.println(" -> Archivos verificados con éxito.");
    }
}