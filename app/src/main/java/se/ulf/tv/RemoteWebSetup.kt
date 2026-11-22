package se.ulf.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

class RemoteWebSetup(
    private val port: Int = 8088,
    private val onM3uReceived: (String) -> Unit,
    private val onXtreamReceived: (String, String, String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            try {
                val socket = ServerSocket(port)
                serverSocket = socket
                while (isActive && !socket.isClosed) {
                    val client = try { socket.accept() } catch (_: Exception) { break }
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: java.net.Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            var message = ""
            if (requestLine.startsWith("POST") && contentLength > 0 && contentLength < 16384) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n == -1) break
                    read += n
                }
                val body = String(buf, 0, read)
                val params = body.split("&").mapNotNull { part ->
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) {
                        URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8")
                    } else null
                }.toMap()

                val m3u = params["m3u"]?.trim().orEmpty()
                val server = params["server"]?.trim().orEmpty()
                val user = params["user"]?.trim().orEmpty()
                val pass = params["pass"]?.trim().orEmpty()

                if (m3u.startsWith("http://", true) || m3u.startsWith("https://", true)) {
                    onM3uReceived(m3u)
                    message = "<div class='ok'>M3U-länk skickad till TV:n!</div>"
                } else if (server.startsWith("http", true) && user.isNotBlank() && pass.isNotBlank()) {
                    onXtreamReceived(server, user, pass)
                    message = "<div class='ok'>Xtream-inloggning skickad till TV:n!</div>"
                } else {
                    message = "<div class='err'>Kontrollera att adressen börjar med http:// eller https://</div>"
                }
            }

            val html = """
                <!DOCTYPE html>
                <html lang="sv"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Ulf TV • Fjärrinmatning</title>
                <style>
                  body{font-family:-apple-system,sans-serif;background:#0a0a0f;color:#fff;max-width:480px;margin:32px auto;padding:20px}
                  .card{background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.15);border-radius:18px;padding:20px;margin-bottom:20px}
                  h1{font-size:24px;margin:0 0 18px} h2{font-size:16px;margin:0 0 12px;color:#cbd5e1}
                  input{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid rgba(255,255,255,.2);background:#141724;color:#fff;margin-bottom:10px;font-size:15px}
                  button{width:100%;padding:12px;border-radius:10px;border:0;background:#fff;color:#000;font-weight:700;font-size:15px;cursor:pointer}
                  .ok{background:#14532d;padding:12px;border-radius:10px;margin-bottom:16px}
                  .err{background:#7f1d1d;padding:12px;border-radius:10px;margin-bottom:16px}
                </style></head><body>
                <h1>Ulf TV • Lägg till källa</h1>
                $message
                <div class="card"><h2>M3U-spellista</h2>
                <form method="POST"><input name="m3u" placeholder="https://exempel.se/lista.m3u" required>
                <button type="submit">Skicka M3U till TV:n</button></form></div>
                <div class="card"><h2>Xtream Codes</h2>
                <form method="POST">
                <input name="server" placeholder="http://server.com:8080" required>
                <input name="user" placeholder="Användarnamn" required>
                <input name="pass" placeholder="Lösenord" type="password" required>
                <button type="submit">Skicka Xtream till TV:n</button></form></div>
                </body></html>
            """.trimIndent()

            val bytes = html.toByteArray(Charsets.UTF_8)
           val out = client.getOutputStream()
            val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            out.write(header.toByteArray())
            out.write(bytes)
            out.flush()
