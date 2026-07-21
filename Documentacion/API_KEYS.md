# API Keys de Lecturameter

Los valores reales viven en `local.properties` (ignorado por git). Este doc describe qué key es cada una, para qué sirve y cómo conseguirla si se pierde.

---

## Google Books API

| Campo           | Valor                          |
|-----------------|--------------------------------|
| Propiedad       | `GOOGLE_BOOKS_API_KEY`         |
| Usado en        | `SearchRepository` — búsqueda de libros por título/autor/ISBN |
| Consola         | console.cloud.google.com → APIs & Services → Credenciales |
| Proyecto GCP    | Lecturameter                   |
| Restricciones   | Android apps, package `com.lecturameter.public` |
| Cuota gratuita  | 1.000 req/día sin facturación  |

---

## Comic Vine API

| Campo           | Valor                          |
|-----------------|--------------------------------|
| Propiedad       | `COMIC_VINE_API_KEY`           |
| Usado en        | `SearchRepository` — búsqueda de cómics/manga por UPC o título |
| Consola         | comicvine.gamespot.com/api/    |
| Cuenta          | parratronix3.0@gmail.com       |
| Cuota gratuita  | Sin límite documentado para uso personal |
| Endpoint base   | `https://comicvine.gamespot.com/api/` |

---

## Cloudflare Worker — Códigos Pro

| Campo           | Valor                          |
|-----------------|--------------------------------|
| Endpoint        | `https://lm-codes.appaugur.workers.dev/redeem` |
| Auth            | Sin key pública; el secreto vive en el Worker (Cloudflare Dashboard) |
| Docs de uso     | `C:\Refrac\backend\COMO_GENERAR_CODIGOS.md` |

---

## Firma de release

| Campo                  | Valor                                    |
|------------------------|------------------------------------------|
| Keystore               | `C:/Keys/lecturameter.jks`               |
| Propiedad store file   | `RELEASE_STORE_FILE`                     |
| Propiedad store pass   | `RELEASE_STORE_PASSWORD`                 |
| Alias                  | `RELEASE_KEY_ALIAS` = `Lecturameter`     |
| Key pass               | `RELEASE_KEY_PASSWORD`                   |
| Copia de seguridad     | Guardar `lecturameter.jks` fuera del PC  |
