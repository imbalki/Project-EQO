package ai.eqo.onboarding

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.util.Locale

/** Only SRV and address records are retained. Invalid packets fail closed, without partial records. */
@Suppress("MagicNumber") // DNS wire masks, field widths and label limits from RFC 1035 / RFC 6762.
internal object MdnsMessage {
    const val MAX_PACKET = 9_000
    const val SRV = 33
    const val A = 1
    const val AAAA = 28
    private const val MAX_RECORDS = 128

    data class Record(
        val name: String,
        val type: Int,
        val ttl: Long,
        val target: String? = null,
        val port: Int? = null,
        val address: InetAddress? = null,
    )

    fun parse(bytes: ByteArray): List<Record> =
        runCatching {
            require(bytes.size in 12..MAX_PACKET)
            val reader = Reader(bytes)
            reader.position = 2
            val flags = reader.word()
            require(flags and 0x8000 != 0 && flags and 0x7a0f == 0)
            val questions = reader.word()
            val records = reader.word() + reader.word() + reader.word()
            require(questions <= 32 && records <= MAX_RECORDS)
            repeat(questions) {
                reader.name()
                reader.skip(4)
            }
            buildList {
                repeat(records) { reader.record()?.let { add(it) } }
            }
        }.getOrDefault(emptyList())

    fun query(questions: List<Pair<String, Int>>): ByteArray {
        require(questions.size in 1..4)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(questions.size)
            repeat(3) { out.writeShort(0) }
            questions.forEach { (name, type) ->
                val labels = name.trimEnd('.').split('.')
                require(name.length <= 253 && labels.all { it.length in 1..63 })
                labels.forEach { label ->
                    val encoded = label.toByteArray(Charsets.UTF_8)
                    require(encoded.size in 1..63)
                    out.writeByte(encoded.size)
                    out.write(encoded)
                }
                out.writeByte(0)
                out.writeShort(type)
                out.writeShort(1) // QM: receive multicast replies on the selected Wi-Fi interface.
            }
        }
        return bytes.toByteArray()
    }

    fun ownEndpoint(
        records: List<Record>,
        instance: String,
        ownAddresses: Set<InetAddress>,
    ): Pair<InetAddress, Int>? {
        val service =
            records
                .filter { it.type == SRV && it.ttl > 0 && it.name == canonical(instance) }
                .distinctBy { it.target to it.port }
        if (service.size != 1) return null
        val endpoints =
            service.mapNotNull { srv ->
                val port = srv.port?.takeIf { it in 1024..65_535 } ?: return@mapNotNull null
                val hosts = records.filter { it.name == srv.target && it.ttl > 0 && it.address != null }
                // A target with any non-local address is not this phone. Never select a LAN peer.
                if (hosts.isEmpty() || hosts.any { it.address !in ownAddresses }) return@mapNotNull null
                ownAddresses.first { it == hosts.first().address } to port
            }
        return endpoints.distinctBy { it.second }.singleOrNull()
    }

    fun canonical(name: String): String = name.trimEnd('.').lowercase(Locale.ROOT) + "."

    private class Reader(
        private val bytes: ByteArray,
    ) {
        var position = 0

        fun word(): Int = (octet() shl 8) or octet()

        private fun octet(): Int {
            require(position < bytes.size)
            return bytes[position++].toInt() and 255
        }

        fun skip(length: Int) {
            require(length >= 0 && position + length <= bytes.size)
            position += length
        }

        fun name(): String {
            val labels = mutableListOf<String>()
            val visited = mutableSetOf<Int>()
            var cursor = position
            var end: Int? = null
            var length = 0
            while (true) {
                require(cursor in bytes.indices && visited.add(cursor) && visited.size <= 128)
                val size = bytes[cursor++].toInt() and 255
                when {
                    size == 0 -> {
                        position = end ?: cursor
                        return canonical(labels.joinToString("."))
                    }
                    size and 0xc0 == 0xc0 -> {
                        require(cursor < bytes.size)
                        val pointer = ((size and 63) shl 8) or (bytes[cursor++].toInt() and 255)
                        if (end == null) end = cursor
                        cursor = pointer
                    }
                    else -> {
                        require(size in 1..63 && cursor + size <= bytes.size)
                        length += size + 1
                        require(length <= 254)
                        labels.add(String(bytes, cursor, size, Charsets.UTF_8))
                        cursor += size
                    }
                }
            }
        }

        fun record(): Record? {
            val owner = name()
            val type = word()
            val clazz = word() and 0x7fff
            val ttl = (word().toLong() shl 16) or word().toLong()
            val size = word()
            val start = position
            skip(size)
            val end = position
            position = start
            val record =
                when {
                    clazz != 1 -> null
                    type == 33 -> {
                        require(size >= 7)
                        skip(4)
                        val port = word()
                        val target = name()
                        require(position == end)
                        Record(owner, type, ttl, target, port)
                    }
                    type == 1 || type == 28 -> {
                        require(size == if (type == 1) 4 else 16)
                        Record(owner, type, ttl, address = InetAddress.getByAddress(bytes.copyOfRange(start, end)))
                    }
                    else -> null
                }
            position = end
            return record
        }
    }
}
