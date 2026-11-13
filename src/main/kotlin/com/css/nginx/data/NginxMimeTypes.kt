package com.css.nginx.data

/** Common MIME types for `types { }` and default_type, as in nginx's own conf/mime.types. */
object NginxMimeTypes {
    val all = listOf(
        "text/html", "text/css", "text/xml", "text/plain", "text/javascript", "text/csv", "text/markdown", "text/mathml",
        "text/vnd.wap.wml", "text/x-component", "text/vnd.sun.j2me.app-descriptor", "image/gif", "image/jpeg", "image/png",
        "image/svg+xml", "image/webp", "image/avif", "image/x-icon", "image/vnd.microsoft.icon", "image/tiff", "image/bmp",
        "application/javascript", "application/json", "application/ld+json", "application/manifest+json", "application/xml",
        "application/atom+xml", "application/rss+xml", "application/pdf", "application/zip", "application/gzip", "application/wasm",
        "application/octet-stream", "application/x-www-form-urlencoded", "application/xhtml+xml", "application/java-archive",
        "application/msword", "application/vnd.ms-excel", "application/vnd.ms-fontobject", "application/rtf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/x-7z-compressed",
        "application/x-shockwave-flash", "application/x-x509-ca-cert", "application/grpc", "font/woff", "font/woff2", "font/ttf",
        "font/otf", "audio/mpeg", "audio/ogg", "audio/midi", "audio/x-m4a", "audio/aac", "audio/wav", "video/mp4", "video/mpeg",
        "video/webm", "video/ogg", "video/quicktime", "video/x-flv", "video/x-msvideo", "video/mp2t", "video/3gpp",
        "multipart/form-data",
    )
}
