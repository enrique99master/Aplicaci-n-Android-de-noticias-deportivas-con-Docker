package com.example.appdenoticias;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class noticiero extends AppCompatActivity {

    private LinearLayout contenedorNoticias;
    private ProgressBar progreso;
    private Button btnActualizar;
    private ScrollView scrollNoticias;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    // =====================================================
    // CONFIGURACIÓN
    // =====================================================

    private static final int MAX_DIAS = 5;
    private static final int MAX_NOTICIAS = 30;

    // =====================================================
    // LAS 3 FUENTES
    // =====================================================

    private static final String AZTECA =
            "https://www.tvazteca.com/aztecadeportes/";

    private static final String RECORD =
            "https://www.record.com.mx/ultimas-noticias";

    private static final String CLARO =
            "https://www.clarosports.com/";

    // =====================================================
    // ON CREATE
    // =====================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_noticiero);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );

        contenedorNoticias =
                findViewById(R.id.contenedorNoticias);

        progreso =
                findViewById(R.id.progreso);

        btnActualizar =
                findViewById(R.id.btnActualizar);

        scrollNoticias =
                findViewById(R.id.scrollNoticias);

        btnActualizar.setOnClickListener(v -> {

            scrollNoticias.scrollTo(0, 0);

            cargarNoticias();
        });

        cargarNoticias();
    }

    // =====================================================
    // CARGAR TODAS LAS NOTICIAS
    // =====================================================

    private void cargarNoticias() {

        progreso.setVisibility(android.view.View.VISIBLE);

        btnActualizar.setEnabled(false);
        btnActualizar.setText("CARGANDO...");

        contenedorNoticias.removeAllViews();

        executor.execute(() -> {

            List<Noticia> noticias =
                    new ArrayList<>();

            Set<String> urlsProcesadas =
                    new HashSet<>();

            // =============================================
            // 1. AZTECA
            // =============================================

            try {

                buscarEnFuente(
                        AZTECA,
                        "Azteca Deportes",
                        "tvazteca.com/aztecadeportes/",
                        noticias,
                        urlsProcesadas
                );

            } catch (Exception e) {

                e.printStackTrace();
            }

            // =============================================
            // 2. RÉCORD
            // =============================================

            try {

                buscarEnFuente(
                        RECORD,
                        "RÉCORD",
                        "record.com.mx/",
                        noticias,
                        urlsProcesadas
                );

            } catch (Exception e) {

                e.printStackTrace();
            }

            // =============================================
            // 3. CLARO SPORTS
            // =============================================

            try {

                buscarEnFuente(
                        CLARO,
                        "Claro Sports",
                        "clarosports.com/",
                        noticias,
                        urlsProcesadas
                );

            } catch (Exception e) {

                e.printStackTrace();
            }

            // =============================================
            // ORDENAR TODAS JUNTAS
            // MÁS RECIENTE PRIMERO
            // =============================================

            Collections.sort(
                    noticias,
                    (a, b) ->
                            Long.compare(
                                    b.fecha,
                                    a.fecha
                            )
            );

            runOnUiThread(() -> {

                progreso.setVisibility(
                        android.view.View.GONE
                );

                btnActualizar.setEnabled(true);

                btnActualizar.setText(
                        "↻ ACTUALIZAR"
                );

                contenedorNoticias.removeAllViews();

                if (noticias.isEmpty()) {

                    mostrarMensaje(
                            "No se encontraron noticias deportivas " +
                                    "de los últimos 5 días."
                    );

                    return;
                }

                int cantidad =
                        Math.min(
                                noticias.size(),
                                MAX_NOTICIAS
                        );

                for (int i = 0; i < cantidad; i++) {

                    crearTarjeta(
                            noticias.get(i)
                    );
                }
            });
        });
    }

    // =====================================================
    // BUSCAR EN UNA FUENTE
    // =====================================================

    private void buscarEnFuente(
            String paginaPrincipal,
            String nombreFuente,
            String dominioPermitido,
            List<Noticia> noticias,
            Set<String> urlsProcesadas
    ) throws Exception {

        Document pagina =
                conectar(paginaPrincipal);

        Elements enlaces =
                pagina.select("a[href]");

        int revisados = 0;

        for (Element enlace : enlaces) {

            if (revisados >= 60) {

                break;
            }

            String url =
                    enlace.absUrl("href");

            url = limpiarUrl(url);

            if (url.isEmpty()) {

                continue;
            }

            // =============================================
            // SOLO EL DOMINIO DE ESA FUENTE
            // =============================================

            if (!url.contains(dominioPermitido)) {

                continue;
            }

            // =============================================
            // EVITAR LINKS QUE NO SON NOTICIAS
            // =============================================

            if (!pareceArticulo(
                    url,
                    nombreFuente
            )) {

                continue;
            }

            if (urlsProcesadas.contains(url)) {

                continue;
            }

            urlsProcesadas.add(url);

            revisados++;

            try {

                Noticia noticia =
                        obtenerNoticia(
                                url,
                                nombreFuente
                        );

                if (noticia == null) {

                    continue;
                }

                // =========================================
                // SOLO ÚLTIMOS 5 DÍAS
                // =========================================

                if (!estaDentroDelRango(
                        noticia.fecha
                )) {

                    continue;
                }

                noticias.add(noticia);

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    // =====================================================
    // CONEXIÓN
    // =====================================================

    private Document conectar(
            String url
    ) throws Exception {

        return Jsoup.connect(url)

                .userAgent(
                        "Mozilla/5.0 (Linux; Android 13) " +
                                "AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) " +
                                "Chrome/120.0 Mobile Safari/537.36"
                )

                .header(
                        "Accept-Language",
                        "es-MX,es;q=0.9"
                )

                .timeout(20000)

                .followRedirects(true)

                .get();
    }

    // =====================================================
    // LIMPIAR URL
    // =====================================================

    private String limpiarUrl(
            String url
    ) {

        if (url == null) {

            return "";
        }

        url = url.trim();

        int numeral =
                url.indexOf("#");

        if (numeral > 0) {

            url =
                    url.substring(
                            0,
                            numeral
                    );
        }

        // NO quitamos ? completamente porque algunos sitios
        // podrían utilizarlo en sus artículos.

        return url;
    }

    // =====================================================
    // SABER SI PARECE ARTÍCULO
    // =====================================================

    private boolean pareceArticulo(
            String url,
            String fuente
    ) {

        String u =
                url.toLowerCase(
                        Locale.ROOT
                );

        // =============================================
        // DESCARTAR ELEMENTOS COMUNES
        // =============================================

        if (
                u.contains("/tag/")
                        ||
                        u.contains("/tags/")
                        ||
                        u.contains("/autor/")
                        ||
                        u.contains("/author/")
                        ||
                        u.contains("/login")
                        ||
                        u.contains("/contacto")
                        ||
                        u.contains("/privacy")
                        ||
                        u.contains("/privacidad")
                        ||
                        u.contains("/terminos")
                        ||
                        u.contains("/suscripcion")
                        ||
                        u.contains("/newsletter")
                        ||
                        u.contains("/podcast")
                        ||
                        u.contains("/videos/")
                        ||
                        u.contains("/video/")
        ) {

            return false;
        }

        // =============================================
        // AZTECA
        // =============================================

        if (fuente.equals("Azteca Deportes")) {

            if (!u.contains(
                    "tvazteca.com/aztecadeportes/"
            )) {

                return false;
            }

            if (
                    u.equals(
                            "https://www.tvazteca.com/aztecadeportes/"
                    )
                            ||
                            u.endsWith(
                                    "/aztecadeportes/fut-azteca"
                            )
                            ||
                            u.contains("/envivo")
                            ||
                            u.contains("/en-vivo")
                            ||
                            u.contains("/resultados")
                            ||
                            u.contains("/calendario")
            ) {

                return false;
            }

            return u.length() > 60;
        }

        // =============================================
        // RÉCORD
        // =============================================

        if (fuente.equals("RÉCORD")) {

            if (!u.contains(
                    "record.com.mx/"
            )) {

                return false;
            }

            if (
                    u.endsWith(
                            "/ultimas-noticias"
                    )
                            ||
                            u.endsWith(
                                    "/futbol"
                            )
                            ||
                            u.endsWith(
                                    "/box"
                            )
                            ||
                            u.endsWith(
                                    "/otros-deportes"
                            )
            ) {

                return false;
            }

            return u.length() > 45;
        }

        // =============================================
        // CLARO SPORTS
        // =============================================

        if (fuente.equals("Claro Sports")) {

            if (!u.contains(
                    "clarosports.com/"
            )) {

                return false;
            }

            if (
                    u.equals(
                            "https://www.clarosports.com/"
                    )
                            ||
                            u.endsWith("/futbol/")
                            ||
                            u.endsWith("/liga-mx/")
            ) {

                return false;
            }

            return u.length() > 50;
        }

        return false;
    }

    // =====================================================
    // OBTENER INFORMACIÓN DE UNA NOTICIA
    // =====================================================

    private Noticia obtenerNoticia(
            String url,
            String fuente
    ) throws Exception {

        Document doc =
                conectar(url);

        Noticia noticia =
                new Noticia();

        noticia.url = url;

        noticia.fuente = fuente;

        // =============================================
        // TÍTULO
        // =============================================

        noticia.titulo =
                meta(
                        doc,
                        "meta[property=og:title]"
                );

        if (noticia.titulo.isEmpty()) {

            noticia.titulo =
                    meta(
                            doc,
                            "meta[name=twitter:title]"
                    );
        }

        if (noticia.titulo.isEmpty()) {

            Element h1 =
                    doc.selectFirst("h1");

            if (h1 != null) {

                noticia.titulo =
                        h1.text().trim();
            }
        }

        if (
                noticia.titulo == null
                        ||
                        noticia.titulo.length() < 8
        ) {

            return null;
        }

        // =============================================
        // DESCRIPCIÓN
        // =============================================

        noticia.descripcion =
                meta(
                        doc,
                        "meta[property=og:description]"
                );

        if (noticia.descripcion.isEmpty()) {

            noticia.descripcion =
                    meta(
                            doc,
                            "meta[name=description]"
                    );
        }

        if (noticia.descripcion.isEmpty()) {

            noticia.descripcion =
                    meta(
                            doc,
                            "meta[name=twitter:description]"
                    );
        }

        // =============================================
        // IMAGEN
        // =============================================

        noticia.imagen =
                obtenerImagen(doc);

        // =============================================
        // FECHA
        // =============================================

        noticia.fecha =
                obtenerFecha(doc);

        // Sin fecha comprobable no la presentamos
        // como una noticia reciente.

        if (noticia.fecha <= 0) {

            return null;
        }

        return noticia;
    }

    // =====================================================
    // OBTENER FECHA
    // =====================================================

    private long obtenerFecha(
            Document doc
    ) {

        // =============================================
        // 1. ARTICLE:PUBLISHED_TIME
        // =============================================

        String fecha =
                meta(
                        doc,
                        "meta[property=article:published_time]"
                );

        long resultado =
                convertirFecha(fecha);

        if (resultado > 0) {

            return resultado;
        }

        // =============================================
        // 2. DATEPUBLISHED
        // =============================================

        Element date =
                doc.selectFirst(
                        "meta[itemprop=datePublished]"
                );

        if (date != null) {

            resultado =
                    convertirFecha(
                            date.attr("content")
                    );

            if (resultado > 0) {

                return resultado;
            }
        }

        // =============================================
        // 3. TIME DATETIME
        // =============================================

        Elements tiempos =
                doc.select(
                        "time[datetime]"
                );

        for (Element tiempo : tiempos) {

            resultado =
                    convertirFecha(
                            tiempo.attr(
                                    "datetime"
                            )
                    );

            if (resultado > 0) {

                return resultado;
            }
        }

        // =============================================
        // 4. JSON-LD
        // =============================================

        Elements scripts =
                doc.select(
                        "script[type=application/ld+json]"
                );

        Pattern patron =
                Pattern.compile(
                        "\"datePublished\"\\s*:\\s*\"([^\"]+)\"",
                        Pattern.CASE_INSENSITIVE
                );

        for (Element script : scripts) {

            String json =
                    script.data();

            if (json == null ||
                    json.isEmpty()) {

                json =
                        script.html();
            }

            Matcher matcher =
                    patron.matcher(json);

            if (matcher.find()) {

                resultado =
                        convertirFecha(
                                matcher.group(1)
                        );

                if (resultado > 0) {

                    return resultado;
                }
            }
        }

        // =============================================
        // 5. FECHA ESCRITA EN EL TEXTO
        // =============================================

        return buscarFechaTexto(
                doc.text()
        );
    }

    // =====================================================
    // BUSCAR FECHA EN TEXTO
    // =====================================================

    private long buscarFechaTexto(
            String texto
    ) {

        if (texto == null) {

            return 0;
        }

        // =============================================
        // 17/09/2026
        // 17/09/2026 | 07:30
        // =============================================

        Pattern patron =
                Pattern.compile(
                        "(\\d{1,2}/\\d{1,2}/20\\d{2})" +
                                "(?:\\s*[|\\-]?\\s*" +
                                "(\\d{1,2}:\\d{2}))?"
                );

        Matcher matcher =
                patron.matcher(texto);

        if (matcher.find()) {

            String fecha =
                    matcher.group(1);

            if (matcher.group(2) != null) {

                fecha +=
                        " "
                                +
                                matcher.group(2);
            }

            long resultado =
                    convertirFecha(fecha);

            if (resultado > 0) {

                return resultado;
            }
        }

        // =============================================
        // 17 septiembre, 2026
        // =============================================

        String limpio =
                quitarAcentos(
                        texto.toLowerCase(
                                new Locale(
                                        "es",
                                        "MX"
                                )
                        )
                );

        Pattern espanol =
                Pattern.compile(
                        "(\\d{1,2})\\s+" +
                                "(?:de\\s+)?" +
                                "(enero|febrero|marzo|abril|mayo|junio|" +
                                "julio|agosto|septiembre|octubre|noviembre|diciembre)" +
                                "\\s*(?:,|de)?\\s*" +
                                "(20\\d{2})"
                );

        matcher =
                espanol.matcher(limpio);

        if (matcher.find()) {

            int dia =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            int mes =
                    numeroMes(
                            matcher.group(2)
                    );

            int anio =
                    Integer.parseInt(
                            matcher.group(3)
                    );

            String fecha =
                    String.format(
                            Locale.US,
                            "%02d/%02d/%04d",
                            dia,
                            mes,
                            anio
                    );

            return convertirFecha(fecha);
        }

        return 0;
    }

    // =====================================================
    // CONVERTIR FECHA
    // =====================================================

    private long convertirFecha(
            String fecha
    ) {

        if (
                fecha == null
                        ||
                        fecha.trim().isEmpty()
        ) {

            return 0;
        }

        fecha = fecha.trim();

        String[] formatos = {

                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssZ",

                "dd/MM/yyyy HH:mm",
                "d/M/yyyy HH:mm",

                "dd/MM/yyyy",
                "d/M/yyyy",

                "yyyy-MM-dd",

                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z"
        };

        for (String patron : formatos) {

            try {

                SimpleDateFormat formato =
                        new SimpleDateFormat(
                                patron,
                                Locale.US
                        );

                formato.setLenient(false);

                Date date =
                        formato.parse(fecha);

                if (date != null) {

                    return date.getTime();
                }

            } catch (Exception ignored) {

            }
        }

        return 0;
    }

    // =====================================================
    // VENTANA MÓVIL DE CINCO DÍAS
    // =====================================================

    private boolean estaDentroDelRango(
            long fecha
    ) {

        if (fecha <= 0) {

            return false;
        }

        long ahora =
                System.currentTimeMillis();

        long cincoDias =
                MAX_DIAS
                        *
                        24L
                        *
                        60L
                        *
                        60L
                        *
                        1000L;

        long limite =
                ahora - cincoDias;

        // Una hora de tolerancia por zona horaria.
        long futuroPermitido =
                ahora
                        +
                        60L
                                *
                                60L
                                *
                                1000L;

        return fecha >= limite
                &&
                fecha <= futuroPermitido;
    }

    // =====================================================
    // IMAGEN
    // =====================================================

    private String obtenerImagen(
            Document doc
    ) {

        String[] selectores = {

                "meta[property=og:image]",
                "meta[property=og:image:secure_url]",
                "meta[name=twitter:image]",
                "meta[name=twitter:image:src]"
        };

        for (String selector : selectores) {

            String imagen =
                    meta(
                            doc,
                            selector
                    );

            imagen =
                    normalizarImagen(
                            imagen
                    );

            if (!imagen.isEmpty()) {

                return imagen;
            }
        }

        Element imagen =
                doc.selectFirst(
                        "article img[src]"
                );

        if (imagen != null) {

            String url =
                    imagen.absUrl("src");

            if (!url.isEmpty()) {

                return url;
            }
        }

        imagen =
                doc.selectFirst(
                        "article img[data-src]"
                );

        if (imagen != null) {

            String url =
                    imagen.absUrl(
                            "data-src"
                    );

            if (!url.isEmpty()) {

                return url;
            }
        }

        return "";
    }

    // =====================================================
    // NORMALIZAR IMAGEN
    // =====================================================

    private String normalizarImagen(
            String imagen
    ) {

        if (imagen == null) {

            return "";
        }

        imagen =
                imagen.trim()
                        .replace(
                                "&amp;",
                                "&"
                        );

        if (imagen.startsWith("//")) {

            return "https:" + imagen;
        }

        return imagen;
    }

    // =====================================================
    // META
    // =====================================================

    private String meta(
            Document doc,
            String selector
    ) {

        try {

            Element elemento =
                    doc.selectFirst(
                            selector
                    );

            if (elemento == null) {

                return "";
            }

            return elemento
                    .attr("content")
                    .trim();

        } catch (Exception e) {

            return "";
        }
    }

    // =====================================================
    // MESES
    // =====================================================

    private int numeroMes(
            String mes
    ) {

        switch (mes) {

            case "enero": return 1;
            case "febrero": return 2;
            case "marzo": return 3;
            case "abril": return 4;
            case "mayo": return 5;
            case "junio": return 6;
            case "julio": return 7;
            case "agosto": return 8;
            case "septiembre": return 9;
            case "octubre": return 10;
            case "noviembre": return 11;
            case "diciembre": return 12;

            default:
                return 0;
        }
    }

    // =====================================================
    // QUITAR ACENTOS
    // =====================================================

    private String quitarAcentos(
            String texto
    ) {

        return Normalizer.normalize(
                texto,
                Normalizer.Form.NFD
        ).replaceAll(
                "\\p{M}",
                ""
        );
    }

    // =====================================================
    // CREAR TARJETA
    // =====================================================

    private void crearTarjeta(
            Noticia noticia
    ) {

        LinearLayout tarjeta =
                new LinearLayout(this);

        tarjeta.setOrientation(
                LinearLayout.VERTICAL
        );

        tarjeta.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(14)
        );

        GradientDrawable fondo =
                new GradientDrawable();

        fondo.setColor(
                Color.WHITE
        );

        fondo.setCornerRadius(
                dp(16)
        );

        fondo.setStroke(
                dp(1),
                Color.rgb(
                        225,
                        225,
                        225
                )
        );

        tarjeta.setBackground(fondo);

        tarjeta.setElevation(
                dp(5)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                dp(5),
                dp(5),
                dp(5),
                dp(16)
        );

        tarjeta.setLayoutParams(params);

        // =============================================
        // FUENTE
        // =============================================

        TextView fuente =
                new TextView(this);

        fuente.setText(
                noticia.fuente
        );

        fuente.setTextSize(13);

        fuente.setTypeface(
                null,
                Typeface.BOLD
        );

        // Distinguimos ligeramente las fuentes.
        if (noticia.fuente.equals("RÉCORD")) {

            fuente.setTextColor(
                    Color.rgb(
                            190,
                            0,
                            0
                    )
            );

        } else if (
                noticia.fuente.equals(
                        "Claro Sports"
                )
        ) {

            fuente.setTextColor(
                    Color.rgb(
                            30,
                            90,
                            50
                    )
            );

        } else {

            fuente.setTextColor(
                    Color.rgb(
                            13,
                            71,
                            161
                    )
            );
        }

        fuente.setPadding(
                dp(2),
                0,
                dp(2),
                dp(8)
        );

        tarjeta.addView(fuente);

        // =============================================
        // IMAGEN
        // =============================================

        if (
                noticia.imagen != null
                        &&
                        !noticia.imagen.isEmpty()
        ) {

            ImageView imagen =
                    new ImageView(this);

            LinearLayout.LayoutParams pImagen =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(205)
                    );

            pImagen.setMargins(
                    0,
                    0,
                    0,
                    dp(12)
            );

            imagen.setLayoutParams(
                    pImagen
            );

            imagen.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            tarjeta.addView(imagen);

            Glide.with(this)

                    .load(
                            noticia.imagen
                    )

                    .diskCacheStrategy(
                            DiskCacheStrategy.ALL
                    )

                    .placeholder(
                            new ColorDrawable(
                                    Color.LTGRAY
                            )
                    )

                    .error(
                            new ColorDrawable(
                                    Color.LTGRAY
                            )
                    )

                    .centerCrop()

                    .into(imagen);
        }

        // =============================================
        // TÍTULO
        // =============================================

        TextView titulo =
                new TextView(this);

        titulo.setText(
                noticia.titulo
        );

        titulo.setTextSize(20);

        titulo.setTypeface(
                null,
                Typeface.BOLD
        );

        titulo.setTextColor(
                Color.rgb(
                        30,
                        30,
                        30
                )
        );

        titulo.setPadding(
                dp(2),
                0,
                dp(2),
                dp(8)
        );

        tarjeta.addView(titulo);

        // =============================================
        // DESCRIPCIÓN
        // =============================================

        if (
                noticia.descripcion != null
                        &&
                        !noticia.descripcion.isEmpty()
        ) {

            TextView descripcion =
                    new TextView(this);

            String texto =
                    noticia.descripcion;

            if (texto.length() > 200) {

                texto =
                        texto.substring(
                                0,
                                200
                        )
                                +
                                "...";
            }

            descripcion.setText(texto);

            descripcion.setTextSize(15);

            descripcion.setTextColor(
                    Color.DKGRAY
            );

            descripcion.setPadding(
                    dp(2),
                    0,
                    dp(2),
                    dp(10)
            );

            tarjeta.addView(
                    descripcion
            );
        }

        // =============================================
        // TIEMPO
        // =============================================

        TextView tiempo =
                new TextView(this);

        tiempo.setText(
                tiempoTranscurrido(
                        noticia.fecha
                )
        );

        tiempo.setTextSize(13);

        tiempo.setTextColor(
                Color.GRAY
        );

        tiempo.setPadding(
                dp(2),
                0,
                dp(2),
                dp(10)
        );

        tarjeta.addView(tiempo);

        // =============================================
        // VER NOTICIA
        // =============================================

        TextView ver =
                new TextView(this);

        ver.setText(
                "Ver noticia   →"
        );

        ver.setTextSize(14);

        ver.setTypeface(
                null,
                Typeface.BOLD
        );

        ver.setTextColor(
                Color.rgb(
                        13,
                        71,
                        161
                )
        );

        tarjeta.addView(ver);

        tarjeta.setOnClickListener(
                v ->
                        abrirNoticia(
                                noticia.url
                        )
        );

        contenedorNoticias.addView(
                tarjeta
        );
    }

    // =====================================================
    // HACE X MINUTOS/HORAS/DÍAS
    // =====================================================

    private String tiempoTranscurrido(
            long fecha
    ) {

        long diferencia =
                System.currentTimeMillis()
                        -
                        fecha;

        if (diferencia < 0) {

            diferencia = 0;
        }

        long minutos =
                diferencia / 60000L;

        if (minutos < 1) {

            return "Publicado hace unos segundos";
        }

        if (minutos < 60) {

            return "Hace "
                    +
                    minutos
                    +
                    (
                            minutos == 1
                                    ?
                                    " minuto"
                                    :
                                    " minutos"
                    );
        }

        long horas =
                minutos / 60L;

        if (horas < 24) {

            return "Hace "
                    +
                    horas
                    +
                    (
                            horas == 1
                                    ?
                                    " hora"
                                    :
                                    " horas"
                    );
        }

        long dias =
                horas / 24L;

        return "Hace "
                +
                dias
                +
                (
                        dias == 1
                                ?
                                " día"
                                :
                                " días"
                );
    }

    // =====================================================
    // ABRIR NOTICIA
    // =====================================================

    @SuppressLint("SetJavaScriptEnabled")
    private void abrirNoticia(
            String url
    ) {

        WebView webView =
                new WebView(this);

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);

        settings.setDomStorageEnabled(true);

        settings.setLoadWithOverviewMode(true);

        settings.setUseWideViewPort(true);

        webView.setWebViewClient(
                new WebViewClient()
        );

        setContentView(webView);

        webView.loadUrl(url);
    }

    // =====================================================
    // MENSAJE
    // =====================================================

    private void mostrarMensaje(
            String texto
    ) {

        TextView mensaje =
                new TextView(this);

        mensaje.setText(texto);

        mensaje.setTextSize(18);

        mensaje.setGravity(
                Gravity.CENTER
        );

        mensaje.setTextColor(
                Color.DKGRAY
        );

        mensaje.setPadding(
                dp(30),
                dp(50),
                dp(30),
                dp(50)
        );

        contenedorNoticias.addView(
                mensaje
        );
    }

    // =====================================================
    // DP
    // =====================================================

    private int dp(
            int valor
    ) {

        return (int) (
                valor
                        *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    // =====================================================
    // DESTROY
    // =====================================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        executor.shutdown();
    }

    // =====================================================
    // CLASE NOTICIA
    // =====================================================

    private static class Noticia {

        String titulo = "";
        String descripcion = "";
        String imagen = "";
        String url = "";
        String fuente = "";

        long fecha = 0;
    }
}