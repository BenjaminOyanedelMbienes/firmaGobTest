import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.itextpdf.signatures.*;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.StampingProperties;
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
    private static boolean usaOTP = false;

    public static void main(String[] args) {
        // Cargar configuraciones desde el sistema (vienen del Bash)
        SECRET_KEY = System.getProperty("f.secret");
        API_TOKEN_KEY = System.getProperty("f.token");
        API_URL = System.getProperty("f.url");
        RUN = System.getProperty("f.run");
        ENTIDAD = System.getProperty("f.entidad");
        PROPOSITO = System.getProperty("f.proposito");
        valorOTP = System.getProperty("f.otp", "");
        NOMBRE_FIRMA = System.getProperty("f.nombre", "FirmaGobierno_" + System.currentTimeMillis());
        
        // Si el otp no viene incluído se asume que es firma desatendida
        if (valorOTP != null && !valorOTP.isEmpty()) {
            usaOTP = true;
        }

        // Si no viene un archivo para firmar nos detenemos
        if (args.length < 1) {
            System.err.println("Uso: firmagob <archivo.pdf> [otp]");
            System.exit(1);
        }

        // Convertimos a ruta absoluta para evitar problemas de directorios
        File archivoOriginal = new File(args[0]).getAbsoluteFile();
        if (!archivoOriginal.exists()) {
            System.err.println("Error: El archivo " + args[0] + " no existe.");
            System.exit(1);
        }

        // Lógica para separar el nombre y la extensión original
        String nombreOriginal = archivoOriginal.getName();
        int dotIndex = nombreOriginal.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? nombreOriginal : nombreOriginal.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : nombreOriginal.substring(dotIndex);

        // Archivos resultantes y temporales
        File archivoFirmado = new File(archivoOriginal.getParent(), baseName + "_firmado" + extension);
        File tempAgujero = new File(archivoOriginal.getParent(), ".temp_hole_" + nombreOriginal);

        try {
            System.out.println("-> Firmando: " + archivoOriginal.getName());
            
            // Le hacemos espacio al archivo para poner la firma
            crearAgujeroPdf(archivoOriginal.getAbsolutePath(), tempAgujero.getAbsolutePath(), NOMBRE_FIRMA);

            // Lo hacheamos y lo dejamos en formato base64 y hex
            byte[] hashBytes = obtenerHashDelAgujero(tempAgujero.getAbsolutePath(), NOMBRE_FIRMA);
            String hashHex = bytesToHex(hashBytes);
            String hashBase64 = Base64.getEncoder().encodeToString(hashBytes);
            
            // Generamos el token jwt y solicitamos la firma a firma digital mandando el hash con ambos formatos y el token
            String jwtToken = generarToken();
            String p7sBase64 = solicitarFirmaAPI(jwtToken, hashHex, hashBase64);

            // Si es que llegó una firma la inyectamos en el documento nuevo _firmado
            if (p7sBase64 != null) {
                // Inyectamos la firma directamente en el archivo final "_firmado"
                inyectarP7S(tempAgujero.getAbsolutePath(), archivoFirmado.getAbsolutePath(), NOMBRE_FIRMA, p7sBase64);
                
                System.out.println("Documento firmado con éxito: " + archivoFirmado.getAbsolutePath());
            }
            else 
                System.out.println("La firma devuelta es null.");

        } catch (Exception e) {
            // Bastante autodescriptivo
            System.err.println("Error crítico: " + e.getMessage());
        } finally {
            // Limpiar solo la basura (el archivo con el agujero)
            if (tempAgujero.exists()) tempAgujero.delete();
        }
    }

    // --- MÉTODOS DE APOYO ---

    public static void crearAgujeroPdf(String src, String dest, String fieldName) throws Exception {
        // Declaramos el lector y el firmador
        PdfReader reader = new PdfReader(src);
        PdfSigner signer = new PdfSigner(reader, new FileOutputStream(dest), new StampingProperties().useAppendMode());

        // Pone el nombre de la firma
        signer.setFieldName(fieldName);

        //Hace el espacio necesario para la firma
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
}