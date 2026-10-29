package ai.devcodex.agent.shell

import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * 应用内 tar.gz 解压（不依赖系统 toybox tar）。
 * 支持普通文件、目录、符号链接、硬链接、GNU 长文件名（L/K）与 pax 扩展头；设备节点等其他类型跳过。
 * 任何 IO 失败都会抛出带条目名的异常，由调用方写入日志并回传界面。
 */
object TarExtractor {
    class Result(val files: Int, val dirs: Int, val links: Int, val skipped: Int)

    private const val BLOCK = 512

    fun extractTarGz(src: InputStream, dest: File, onProgress: (Int) -> Unit = {}): Result {
        dest.mkdirs()
        val root = dest.canonicalFile
        val rootPrefix = root.path + File.separator
        val ins = GZIPInputStream(BufferedInputStream(src, 64 * 1024), 64 * 1024)
        val header = ByteArray(BLOCK)
        var longName: String? = null
        var longLink: String? = null
        var paxPath: String? = null
        var paxLink: String? = null
        var paxSize: Long? = null
        var files = 0; var dirs = 0; var links = 0; var skipped = 0; var entries = 0

        while (true) {
            if (!readFully(ins, header)) break
            if (header.all { it == 0.toByte() }) break

            val type = header[156].toInt().toChar()
            val hdrSize = octal(header, 124, 12)
            val mode = octal(header, 100, 8).toInt() and 0xFFF

            // 扩展头：读取数据体后作用于下一个条目
            if (type == 'L' || type == 'K' || type == 'x') {
                val data = readBytes(ins, hdrSize)
                skipPadding(ins, hdrSize)
                when (type) {
                    'L' -> longName = cstr(data, 0, data.size)
                    'K' -> longLink = cstr(data, 0, data.size)
                    else -> parsePax(data) { k, v ->
                        when (k) {
                            "path" -> paxPath = v
                            "linkpath" -> paxLink = v
                            "size" -> paxSize = v.toLongOrNull()
                        }
                    }
                }
                continue
            }
            if (type == 'g') { skipFully(ins, hdrSize + padding(hdrSize)); continue }

            val size = paxSize ?: hdrSize
            var name = cstr(header, 0, 100)
            if (String(header, 257, 5, Charsets.US_ASCII) == "ustar") {
                val prefix = cstr(header, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
            }
            name = paxPath ?: longName ?: name
            val linkName = paxLink ?: longLink ?: cstr(header, 157, 100)
            longName = null; longLink = null; paxPath = null; paxLink = null; paxSize = null

            val rel = name.trimStart('/').trimEnd('/')
            val unsafe = rel.split('/').any { it == ".." }
            var dataConsumed = false

            try {
                if (rel.isEmpty() || rel == "." || unsafe) {
                    skipped++
                } else {
                    val target = File(root, rel)
                    when (type) {
                        '5' -> {
                            target.mkdirs()
                            runCatching { Os.chmod(target.path, mode or 0x1C0) }
                            dirs++
                        }
                        '0', '\u0000', '7' -> {
                            val parent = target.parentFile!!
                            parent.mkdirs()
                            if (!isInside(parent, root, rootPrefix)) {
                                skipped++
                            } else {
                                target.delete()
                                FileOutputStream(target).use { out -> copyN(ins, out, size) }
                                dataConsumed = true
                                skipPadding(ins, size)
                                runCatching { Os.chmod(target.path, mode or 0x180) }
                                files++
                            }
                        }
                        '2' -> {
                            target.parentFile!!.mkdirs()
                            target.delete()
                            Os.symlink(linkName, target.path)
                            links++
                        }
                        '1' -> {
                            target.parentFile!!.mkdirs()
                            val origin = File(root, linkName.trimStart('/'))
                            if (origin.isFile) {
                                target.delete()
                                origin.copyTo(target, overwrite = true)
                                runCatching { Os.chmod(target.path, mode or 0x180) }
                                links++
                            } else skipped++
                        }
                        else -> skipped++
                    }
                }
            } catch (e: Exception) {
                throw IOException("解压条目失败 [$rel] type=$type: ${e.message}", e)
            }
            if (!dataConsumed && size > 0) skipFully(ins, size + padding(size))

            entries++
            if (entries % 200 == 0) onProgress(entries)
        }
        return Result(files, dirs, links, skipped)
    }

    private fun isInside(f: File, root: File, rootPrefix: String): Boolean {
        val p = f.canonicalFile.path
        return p == root.path || p.startsWith(rootPrefix)
    }

    private fun padding(size: Long): Long = (BLOCK - size % BLOCK) % BLOCK

    private fun skipPadding(ins: InputStream, size: Long) = skipFully(ins, padding(size))

    private fun readFully(ins: InputStream, buf: ByteArray): Boolean {
        var n = 0
        while (n < buf.size) {
            val r = ins.read(buf, n, buf.size - n)
            if (r < 0) { if (n == 0) return false else throw IOException("tar 头被截断") }
            n += r
        }
        return true
    }

    private fun readBytes(ins: InputStream, size: Long): ByteArray {
        if (size < 0 || size > 1_000_000) throw IOException("tar 扩展头异常 size=$size")
        val buf = ByteArray(size.toInt())
        var n = 0
        while (n < buf.size) {
            val r = ins.read(buf, n, buf.size - n)
            if (r < 0) throw IOException("tar 数据被截断")
            n += r
        }
        return buf
    }

    private fun skipFully(ins: InputStream, count: Long) {
        var left = count
        val sink = ByteArray(8192)
        while (left > 0) {
            val r = ins.read(sink, 0, minOf(left, sink.size.toLong()).toInt())
            if (r < 0) throw IOException("tar 数据被截断")
            left -= r
        }
    }

    private fun copyN(ins: InputStream, out: java.io.OutputStream, size: Long) {
        var left = size
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val r = ins.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
            if (r < 0) throw IOException("tar 数据被截断")
            out.write(buf, 0, r)
            left -= r
        }
    }

    private fun cstr(b: ByteArray, off: Int, len: Int): String {
        var n = 0
        while (n < len && off + n < b.size && b[off + n] != 0.toByte()) n++
        return String(b, off, n, Charsets.UTF_8)
    }

    /** 八进制字段；最高位为 1 时按 GNU base-256 解析 */
    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) {
            var v = 0L
            for (i in 1 until len) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
            return v
        }
        var v = 0L
        var started = false
        for (i in off until off + len) {
            val c = b[i].toInt()
            if (c == 32 && !started) continue
            if (c < 48 || c > 55) break
            started = true
            v = v * 8 + (c - 48)
        }
        return v
    }

    /** pax 记录格式："<len> <key>=<value>\n" */
    private fun parsePax(data: ByteArray, onKv: (String, String) -> Unit) {
        var pos = 0
        while (pos < data.size) {
            var sp = pos
            while (sp < data.size && data[sp] != ' '.code.toByte()) sp++
            if (sp >= data.size) break
            val recLen = String(data, pos, sp - pos, Charsets.US_ASCII).toIntOrNull() ?: break
            if (recLen <= 0 || pos + recLen > data.size) break
            val body = String(data, sp + 1, pos + recLen - sp - 2, Charsets.UTF_8)
            val eq = body.indexOf('=')
            if (eq > 0) onKv(body.substring(0, eq), body.substring(eq + 1))
            pos += recLen
        }
    }
}
