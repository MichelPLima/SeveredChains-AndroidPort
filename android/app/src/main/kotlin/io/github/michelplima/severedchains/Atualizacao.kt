package io.github.michelplima.severedchains

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Atualizações pelo launcher: consulta os Releases do repositório público do port no GitHub, baixa o APK do
 * canal escolhido e entrega ao instalador do Android (PackageInstaller), que pede a confirmação da pessoa.
 *
 * - Canal Estável: só releases normais. Canal Teste: também as marcadas como pré-lançamento no GitHub
 * - A versão vem da tag (ex.: v0.6.0) e é comparada com o versionName do app pelos números
 * - O APK é conferido pelo SHA-256 que o GitHub publica para cada anexo ("digest"); o Android ainda confere
 *   se a assinatura é a mesma do app instalado
 * - A consulta só manda o pedido ao api.github.com com um User-Agent genérico (nada da pessoa)
 */
object Atualizacao {
    enum class Canal(val chave: String) { ESTAVEL("estavel"), TESTE("teste") }

    /** Uma versão publicada que é mais nova que a instalada */
    class Versao(val tag: String, val nome: String, val apk: String, val tamanho: Long, val sha256: String?)

    /** Avisos do download para a tela (chamados na thread do download) */
    interface Andamento {
        fun baixando(copiados: Long, total: Long)
        fun aguardandoConfirmacao()
    }

    const val ACAO_RESULTADO = "io.github.michelplima.severedchains.RESULTADO_ATUALIZACAO"

    private const val PREFS = "preferencias"
    private const val CHAVE_CANAL = "canal_atualizacao"
    private const val CHAVE_ULTIMA = "ultima_verificacao"
    private const val AGENTE = "SeveredChains-Android-updater"
    /** Verificação automática ao abrir o launcher: no máximo a cada 6 horas */
    private const val INTERVALO_MS = 6L * 60 * 60 * 1000

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun canal(ctx: Context): Canal = Canal.entries.firstOrNull { it.chave == prefs(ctx).getString(CHAVE_CANAL, null) } ?: Canal.ESTAVEL

    fun definirCanal(ctx: Context, canal: Canal) {
        prefs(ctx).edit().putString(CHAVE_CANAL, canal.chave).apply()
    }

    fun versaoInstalada(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** Hora de verificar sozinho (ao abrir o launcher)? */
    fun verificacaoVencida(ctx: Context): Boolean = System.currentTimeMillis() - prefs(ctx).getLong(CHAVE_ULTIMA, 0) > INTERVALO_MS

    /** "v0.6.0-teste" ou "0.6.0" -> [0, 6, 0]; só a parte antes do primeiro hífen conta */
    private fun numeros(versao: String): List<Int> =
        Regex("""\d+""").findAll(versao.removePrefix("v").substringBefore('-')).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun maisNova(a: String, b: String): Boolean {
        val x = numeros(a)
        val y = numeros(b)
        for(i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if(d != 0) return d > 0
        }
        return false
    }

    /** A versão mais nova do canal acima da instalada, ou null se está em dia. Rede: rodar fora da thread principal. */
    @Throws(IOException::class)
    fun buscar(ctx: Context, canal: Canal = canal(ctx)): Versao? {
        val conexao = (URL("${Projeto.API_RELEASES}?per_page=30").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", AGENTE)
        }
        val corpo = try {
            if(conexao.responseCode != 200) throw IOException("GitHub respondeu ${conexao.responseCode}")
            conexao.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexao.disconnect()
        }
        prefs(ctx).edit().putLong(CHAVE_ULTIMA, System.currentTimeMillis()).apply()

        val instalada = versaoInstalada(ctx)
        var melhor: Versao? = null
        val releases = JSONArray(corpo)
        for(i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if(r.optBoolean("draft")) continue
            if(r.optBoolean("prerelease") && canal != Canal.TESTE) continue
            val tag = r.optString("tag_name")
            if(!maisNova(tag, instalada) || (melhor != null && !maisNova(tag, melhor.tag))) continue
            val anexos = r.optJSONArray("assets") ?: continue
            for(j in 0 until anexos.length()) {
                val a = anexos.getJSONObject(j)
                if(!a.optString("name").endsWith(".apk")) continue
                val digest = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
                melhor = Versao(tag, r.optString("name").ifBlank { tag }, a.optString("browser_download_url"), a.optLong("size", -1), digest)
                break
            }
        }
        Logs.escrever(ctx, "Atualização (${canal.chave}): instalada $instalada, " + (melhor?.let { "disponível ${it.tag} (${it.tamanho shr 20} MB)" } ?: "em dia"))
        return melhor
    }

    /** O Android deixa este app instalar atualizações? Senão, abrir as configurações para a pessoa permitir. */
    fun podeInstalar(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /**
     * Baixa o APK direto para uma sessão do PackageInstaller (sem arquivo temporário) e pede a instalação.
     * O resultado chega na MainActivity (onNewIntent, ação [ACAO_RESULTADO]). Rodar fora da thread principal.
     */
    @Throws(IOException::class)
    fun baixarEInstalar(activity: Activity, versao: Versao, andamento: Andamento) {
        val instalador = activity.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(activity.packageName)
            if(versao.tamanho > 0) setSize(versao.tamanho)
        }
        val id = instalador.createSession(parametros)
        val sessao = instalador.openSession(id)
        try {
            val conexao = (URL(versao.apk).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", AGENTE)
            }
            val sha = MessageDigest.getInstance("SHA-256")
            try {
                if(conexao.responseCode != 200) throw IOException("download respondeu ${conexao.responseCode}")
                val total = if(versao.tamanho > 0) versao.tamanho else conexao.contentLengthLong
                conexao.inputStream.use { entrada ->
                    sessao.openWrite("severedchains.apk", 0, total).use { saida ->
                        val buf = ByteArray(1 shl 16)
                        var copiados = 0L
                        var ultimoAviso = 0L
                        while(true) {
                            val n = entrada.read(buf)
                            if(n < 0) break
                            saida.write(buf, 0, n)
                            sha.update(buf, 0, n)
                            copiados += n
                            val agora = System.nanoTime()
                            if(agora - ultimoAviso > 250_000_000L) {
                                ultimoAviso = agora
                                andamento.baixando(copiados, total)
                            }
                        }
                        andamento.baixando(copiados, total)
                        sessao.fsync(saida)
                    }
                }
            } finally {
                conexao.disconnect()
            }
            val obtido = sha.digest().joinToString("") { "%02x".format(it) }
            if(versao.sha256 != null && !versao.sha256.equals(obtido, ignoreCase = true)) {
                throw IOException(activity.getString(R.string.atualizacao_hash))
            }
            Logs.escrever(activity, "Atualização ${versao.tag} baixada (sha256 $obtido), pedindo a instalação")
            val resultado = Intent(activity, MainActivity::class.java).setAction(ACAO_RESULTADO).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendente = PendingIntent.getActivity(activity, id, resultado, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            andamento.aguardandoConfirmacao()
            sessao.commit(pendente.intentSender)
            sessao.close()
        } catch(e: Exception) {
            sessao.abandon()
            throw if(e is IOException) e else IOException(e.message ?: e.toString(), e)
        }
    }
}
