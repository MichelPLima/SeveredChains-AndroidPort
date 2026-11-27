package io.github.michelplima.severedchains

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream

/**
 * Importação dos 4 discos do jogo pelo seletor do Android (SAF, sem permissões).
 *
 * Os arquivos originais só são lidos. As cópias vão para isos/discoN.bin na pasta do jogo, onde o Unpacker
 * procura os discos a cada abertura (ele exige os 4 sempre, não só na primeira extração).
 *
 * O disco é identificado pelo conteúdo, com a mesma leitura do Unpacker.getIsoReader: .bin cru (2352 bytes
 * por setor), PVD do ISO 9660 no setor 16, sistema "PLAYSTATION" e o ID do volume (SCUS94491...).
 */
object Discos {
    const val PEDIDO = 4202

    /** Unpacker.DISK_IDS: discos 1 a 4 da versão americana (NTSC-U), a única que o jogo aceita */
    val IDS = listOf("SCUS94491", "SCUS94584", "SCUS94585", "SCUS94586")

    /** Avisos da importação para a tela (chamados na thread da importação). */
    interface Andamento {
        fun conferindo(nome: String) {}
        fun copiando(indice: Int, copiados: Long, total: Long) {}
        fun importado(indice: Int, novo: Boolean) {}
        fun recusado(nome: String, motivo: String) {}
    }
    /** IsoReader.SECTOR_SIZE */
    private const val SETOR = 2352
    /** Unpacker.PVD_SECTOR */
    private const val SETOR_PVD = 16
    /** O Unpacker pula 12 bytes de sync e mais 12 (cabeçalho + subcabeçalho do modo 2) antes do PVD */
    private const val INICIO_PVD = SETOR_PVD * SETOR + 24
    /** Lido antes de decidir: até o fim do PVD (o Unpacker exige pelo menos PVD_SECTOR + 1 setores) */
    private const val CABECALHO = (SETOR_PVD + 1) * SETOR
    /** Espaço livre que fica sobrando depois da cópia (o jogo ainda extrai arquivos e grava saves) */
    private const val MARGEM_LIVRE = 512L shl 20

    fun pasta(ctx: Context): File = File(Jvm.pastaDoJogo(ctx), "isos")

    fun arquivo(ctx: Context, indice: Int): File = File(pasta(ctx), "disco${indice + 1}.bin")

    fun todos(ctx: Context): Boolean = IDS.indices.all { arquivo(ctx, it).isFile }

    /** Tamanho da cópia do disco, ou -1 se ele não foi importado. */
    fun tamanho(ctx: Context, indice: Int): Long = arquivo(ctx, indice).let { if(it.isFile) it.length() else -1 }

    /** O jogo já extraiu os arquivos dos discos (Unpacker grava files/version no fim). */
    fun extraidos(ctx: Context): Boolean = File(Jvm.pastaDoJogo(ctx), "files/version").isFile

    /** "SCUS94491" -> "SCUS-94491", como na lombada */
    fun codigo(indice: Int): String = IDS[indice].let { it.substring(0, 4) + "-" + it.substring(4) }

    /** Ex.: "Discos: 1 ✓  2 ✓  3 –  4 –" */
    fun resumo(ctx: Context): String = ctx.getString(R.string.discos_resumo) + ": " + IDS.indices.joinToString("  ") { "${it + 1} " + if(arquivo(ctx, it).isFile) "✓" else "–" }

    fun pedir(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // .bin não tem tipo MIME padrão (cada provedor informa um)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        activity.startActivityForResult(intent, PEDIDO)
    }

    /** Arquivos escolhidos no seletor (um ou vários); vazio se não for o nosso pedido ou se cancelou. */
    fun escolhidos(requestCode: Int, resultCode: Int, data: Intent?): List<Uri> {
        if(requestCode != PEDIDO || resultCode != Activity.RESULT_OK || data == null) return emptyList()
        val clip = data.clipData
        if(clip != null) return (0 until clip.itemCount).map { clip.getItemAt(it).uri }
        return listOfNotNull(data.data)
    }

    /** Confere e copia cada arquivo, avisando [andamento]. Roda fora da thread principal. */
    fun importar(ctx: Context, uris: List<Uri>, andamento: Andamento) {
        pasta(ctx).mkdirs()
        Logs.escrever(ctx, "==== Importar discos: ${uris.size} arquivo(s) ====")
        for(uri in uris) {
            val (nome, tamanho) = info(ctx, uri)
            try {
                ctx.contentResolver.openInputStream(uri)?.use { importarUm(ctx, it, nome, tamanho, andamento) }
                    ?: recusar(ctx, andamento, nome, ctx.getString(R.string.recusa_seletor))
            } catch(e: Exception) {
                recusar(ctx, andamento, nome, ctx.getString(R.string.recusa_leitura, (e.message ?: e).toString()))
            }
        }
        Logs.escrever(ctx, resumo(ctx))
    }

    private fun recusar(ctx: Context, andamento: Andamento, nome: String, motivo: String) {
        Logs.escrever(ctx, "$nome: ignorado, $motivo")
        andamento.recusado(nome, motivo)
    }

    private fun importarUm(ctx: Context, entrada: InputStream, nome: String, tamanho: Long, andamento: Andamento) {
        andamento.conferindo(nome)
        val cabecalho = ByteArray(CABECALHO)
        val lidos = lerAte(entrada, cabecalho)
        val indice = identificar(ctx, cabecalho, lidos)
        if(indice !is Int) {
            recusar(ctx, andamento, nome, indice.toString())
            return
        }
        val destino = arquivo(ctx, indice)
        if(tamanho > 0 && destino.isFile && destino.length() == tamanho) {
            Logs.escrever(ctx, "$nome: disco ${indice + 1} já importado (mesmo tamanho), mantido")
            andamento.importado(indice, novo = false)
            return
        }
        val livre = pasta(ctx).usableSpace + (if(destino.isFile) destino.length() else 0)
        if(tamanho > 0 && livre < tamanho + MARGEM_LIVRE) {
            recusar(ctx, andamento, nome, ctx.getString(R.string.recusa_espaco, indice + 1, mb(tamanho + MARGEM_LIVRE), mb(livre)))
            return
        }

        // Cópia em .parte e troca no fim: o Unpacker nunca vê um disco pela metade
        val parte = File(destino.path + ".parte")
        val inicio = System.nanoTime()
        var copiados = lidos.toLong()
        andamento.copiando(indice, copiados, tamanho)
        try {
            parte.outputStream().use { saida ->
                saida.write(cabecalho, 0, lidos)
                val buf = ByteArray(1 shl 20)
                var ultimoAviso = 0L
                while(true) {
                    val n = entrada.read(buf)
                    if(n < 0) break
                    saida.write(buf, 0, n)
                    copiados += n
                    val agora = System.nanoTime()
                    if(agora - ultimoAviso > 200_000_000L) {
                        ultimoAviso = agora
                        andamento.copiando(indice, copiados, tamanho)
                    }
                }
            }
        } catch(e: Exception) {
            parte.delete()
            throw e
        }
        if(tamanho > 0 && copiados != tamanho) {
            parte.delete()
            recusar(ctx, andamento, nome, ctx.getString(R.string.recusa_incompleta, copiados, tamanho))
            return
        }
        if(destino.exists()) destino.delete()
        if(!parte.renameTo(destino)) {
            parte.delete()
            recusar(ctx, andamento, nome, ctx.getString(R.string.recusa_gravar, destino.name))
            return
        }
        val segundos = (System.nanoTime() - inicio) / 1e9
        Logs.escrever(ctx, "$nome: disco ${indice + 1} (${IDS[indice]}) importado, ${mb(copiados)} MB em ${"%.1f".format(segundos)} s")
        andamento.importado(indice, novo = true)
    }

    /** Índice do disco (0 a 3) ou o motivo da recusa (para a tela e o log). */
    private fun identificar(ctx: Context, c: ByteArray, lidos: Int): Any {
        // trim como o String.trim() do Java (tira tudo <= ' ', inclusive \0); o trim() do Kotlin não tira \0
        fun texto(de: Int, n: Int) = String(c, INICIO_PVD + de, n, Charsets.US_ASCII).trim { it <= ' ' }
        val pvd = lidos >= CABECALHO && c[INICIO_PVD].toInt() == 1 && texto(1, 5) == "CD001" && c[INICIO_PVD + 6].toInt() == 1
        if(!pvd) {
            // Só para explicar a recusa: formatos comuns em coleções de ROMs que o jogo não lê
            if(lidos >= 4 && String(c, 0, 4, Charsets.US_ASCII) == "\u0000PBP") return ctx.getString(R.string.recusa_pbp)
            if(lidos >= 8 && String(c, 0, 8, Charsets.US_ASCII) == "MComprHD") return ctx.getString(R.string.recusa_chd)
            if(lidos >= 16 * 2048 + 6 && c[16 * 2048].toInt() == 1 && String(c, 16 * 2048 + 1, 5, Charsets.US_ASCII) == "CD001") {
                return ctx.getString(R.string.recusa_iso)
            }
            if(lidos < CABECALHO) return ctx.getString(R.string.recusa_pequeno, lidos)
            return ctx.getString(R.string.recusa_nao_bin)
        }
        // Mesmos offsets do Unpacker (7 e 39: ele lê o byte 7, que é zero, junto com o sistema)
        val sistema = texto(7, 32)
        if(sistema != "PLAYSTATION") return ctx.getString(R.string.recusa_nao_playstation, sistema)
        val id = texto(39, 32)
        val i = IDS.indexOf(id)
        if(i < 0) return ctx.getString(R.string.recusa_outro_disco, id)
        return i
    }

    /** Lê até encher [buf] ou acabar o arquivo; devolve quantos bytes leu. */
    private fun lerAte(entrada: InputStream, buf: ByteArray): Int {
        var total = 0
        while(total < buf.size) {
            val n = entrada.read(buf, total, buf.size - total)
            if(n < 0) break
            total += n
        }
        return total
    }

    private fun info(ctx: Context, uri: Uri): Pair<String, Long> {
        var nome = uri.lastPathSegment ?: uri.toString()
        var tamanho = -1L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if(c.moveToFirst()) {
                c.getString(0)?.let { nome = it }
                if(!c.isNull(1)) tamanho = c.getLong(1)
            }
        }
        return nome to tamanho
    }

    private fun mb(bytes: Long) = bytes shr 20
}
