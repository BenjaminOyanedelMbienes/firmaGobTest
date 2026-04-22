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
import java.security.MessageDigest;
import java.util.Base64;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class FirmaInyector {

    // Credenciales de TEST (Firma Desatendida) del manual
    private static final String SECRET_KEY = "27a216342c744f89b7b82fa290519ba0";
    private static final String API_TOKEN_KEY = "sandbox";
    private static final String RUN = "22222222";
    private static final String ENTIDAD = "Subsecretaría General de la Presidencia";
    private static final String PROPOSITO = "Desatendido";
    private static final String API_URL = "https://api.firma.cert.digital.gob.cl/firma/v2/files/tickets";

    public static void main(String[] args) {
        try {
            // Mantengo la ruta que adaptaste en tu Codespace
            String srcPdf = "../prueba.pdf"; 
            String tempPdf = "temp_agujero.pdf"; 
            String destPdf = "documento_final_firmado.pdf"; 

            System.out.println("1. Creando agujero (Blank Signature) en el PDF...");
            crearAgujeroPdf(srcPdf, tempPdf, "FirmaGobierno");

            System.out.println("2. Extrayendo Hash SHA-256 (ByteRange)...");
            byte[] hashBytes = obtenerHashDelAgujero(tempPdf, "FirmaGobierno");
            String hashHex = bytesToHex(hashBytes);
            String hashBase64 = Base64.getEncoder().encodeToString(hashBytes);

            System.out.println("3. Generando Token JWT...");
            String jwtToken = generarToken();

            System.out.println("4. Solicitando firma (P7S) a Firma.gob...");
            String p7sBase64 = solicitarFirmaAPI(jwtToken, hashHex, hashBase64);

            if (p7sBase64 != null) {
                System.out.println("5. Inyectando P7S en el PDF...");
                inyectarP7S(tempPdf, destPdf, "FirmaGobierno", p7sBase64);
                System.out.println("\n=== ÉXITO: PDF FIRMADO CORRECTAMENTE ===");
                System.out.println("Revisa el archivo: " + destPdf);
            } else {
                System.out.println("Error: No se recibió el P7S de la API.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- PASO 1: CREAR EL AGUJERO ---
    public static void crearAgujeroPdf(String src, String dest, String fieldName) throws Exception {
        PdfReader reader = new PdfReader(src);
        PdfSigner signer = new PdfSigner(reader, new FileOutputStream(dest), new StampingProperties());
        signer.setFieldName(fieldName);

        IExternalSignatureContainer blank = new ExternalBlankSignatureContainer(PdfName.Adobe_PPKLite, PdfName.Adbe_pkcs7_detached);
        signer.signExternalContainer(blank, 8192);
    }

    // --- PASO 2: OBTENER EL HASH (Corregido para iText 7 y ByteRange puro) ---
    public static byte[] obtenerHashDelAgujero(String tempPdf, String fieldName) throws Exception {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(tempPdf));
        SignatureUtil signUtil = new SignatureUtil(pdfDoc);
        PdfDictionary sigDict = signUtil.getSignatureDictionary(fieldName);
        PdfArray byteRange = sigDict.getAsArray(PdfName.ByteRange);
        
        long[] ranges = new long[4];
        for (int i = 0; i < 4; i++) {
            ranges[i] = byteRange.getAsNumber(i).longValue();
        }
        pdfDoc.close();

        // Leemos físicamente el archivo saltándonos el agujero según las coordenadas del ByteRange
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (RandomAccessFile raf = new RandomAccessFile(tempPdf, "r")) {
            byte[] buf = new byte[8192];
            
            // 1. Leer antes del agujero
            raf.seek(ranges[0]);
            long remaining = ranges[1];
            while (remaining > 0) {
                int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read == -1) break;
                md.update(buf, 0, read);
                remaining -= read;
            }
            
            // 2. Leer después del agujero
            raf.seek(ranges[2]);
            remaining = ranges[3];
            while (remaining > 0) {
                int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read == -1) break;
                md.update(buf, 0, read);
                remaining -= read;
            }
        }
        return md.digest();
    }

    // --- PASO 3: GENERAR JWT ---
    public static String generarToken() {
        Instant expTime = Instant.now().minus(4, ChronoUnit.HOURS).plus(25, ChronoUnit.MINUTES);
        
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        return JWT.create()
                .withClaim("entity", ENTIDAD)
                .withClaim("run", RUN)
                .withClaim("purpose", PROPOSITO)
                .withClaim("expiration", expTime.toString().substring(0, 19)) 
                .sign(algorithm);
    }

    // --- PASO 4: LLAMADA A LA API ---
    public static String solicitarFirmaAPI(String token, String hashHex, String hashBase64) throws Exception {
        JSONObject fileObj = new JSONObject();
        fileObj.put("content-type", "application/json");
        fileObj.put("content", hashBase64);
        fileObj.put("description", "Prueba Java iText");
        fileObj.put("checksum", hashHex);

        JSONObject payload = new JSONObject();
        payload.put("api_token_key", API_TOKEN_KEY);
        payload.put("token", token);
        payload.put("files", new JSONArray().put(fileObj));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JSONObject resJson = new JSONObject(response.body());
            return resJson.getJSONArray("files").getJSONObject(0).getString("content");
        } else {
            System.out.println("Error API: " + response.body());
            return null;
        }
    }

    // --- PASO 5: INYECTAR EL P7S (Corregido para iText 7) ---
    public static void inyectarP7S(String tempPdf, String destPdf, String fieldName, String p7sBase64) throws Exception {
        byte[] p7sBytes = Base64.getDecoder().decode(p7sBase64);
        
        // iText 7 requiere instanciar el PdfDocument antes de diferir la firma
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(tempPdf));
        
        IExternalSignatureContainer container = new ExternalSignatureContainer(p7sBytes);
        PdfSigner.signDeferred(pdfDoc, fieldName, new FileOutputStream(destPdf), container);
    }

    // Utilidad: Convertir bytes a Hexadecimal para el checksum
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    // Contenedor que "envuelve" la firma P7S que nos da el Gobierno
    static class ExternalSignatureContainer implements IExternalSignatureContainer {
        private byte[] sig;
        public ExternalSignatureContainer(byte[] sig) { this.sig = sig; }
        @Override
        public byte[] sign(InputStream data) { return sig; }
        @Override
        public void modifySigningDictionary(PdfDictionary signDic) {}
    }
}