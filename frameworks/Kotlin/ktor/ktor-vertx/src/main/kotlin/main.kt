import com.dslplatform.json.DslJson
import com.dslplatform.json.JsonWriter
import com.dslplatform.json.runtime.Settings.withRuntime
import com.mitchellbosecke.pebble.loader.ClasspathLoader
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pebble.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import java.nio.charset.StandardCharsets

const val message = "Hello, World!"
val messageBytes = message.toByteArray(StandardCharsets.US_ASCII)

fun String?.toBoxedInt(): Int = try {
    val i = this?.toInt()
    when {
        i == null -> 1
        i < 1 -> 1
        i > 500 -> 500
        else -> i
    }
} catch (e: NumberFormatException) {
    1
}

val dslJson = DslJson(withRuntime<Any>().includeServiceLoader())
val jsonWriter = object : ThreadLocal<JsonWriter>() {
    override fun initialValue(): JsonWriter =
        dslJson.newWriter(4096)
}

inline fun <reified T> T.toJsonByteArray(): ByteArray {
    val writer = jsonWriter.get()
    writer.reset()
    dslJson.serialize(writer, T::class.java, this)
    return writer.toByteArray()
}

fun main(args: Array<String>) {
    val dbName = args.firstOrNull() ?: "tfb-database"
    val pgOptions =
        PgConnectOptions().apply {
            host = dbName
            database = "hello_world"
            user = "benchmarkdbuser"
            password = "benchmarkdbpass"
            cachePreparedStatements = true
        }

    val sqlClient = run {
        val poolOptions = PoolOptions().setMaxSize(64)
        PgPool.pool(pgOptions, poolOptions)
    }

    val db = VertxRepository(sqlClient)
    val cachedDb = CachedDb(db, sqlClient)

    val server = embeddedServer(
        Netty, 8080,
        configure = {
            shareWorkGroup = true
        }
    ) {
        install(DefaultHeaders)

        install(Pebble) {
            loader(
                ClasspathLoader().apply {
                    prefix = "templates"
                }
            )
        }

        routing {
            get("/plaintext") {
                call.respondBytes(messageBytes, contentType = ContentType.Text.Plain)
            }

            get("/json") {
                val json = Message(message).toJsonByteArray()
                call.respondBytes(json, ContentType.Application.Json)
            }

            get("/db") {
                val json = db.randomWorld().toJsonByteArray()
                call.respondBytes(json, ContentType.Application.Json)
            }

            get("/query/") {
                val queries = call.parameters["queries"].toBoxedInt()
                val json = db.getWorlds(queries).toJsonByteArray()
                call.respondBytes(json, ContentType.Application.Json)
            }

            get("/cached-worlds") {
                val queries = call.parameters["count"].toBoxedInt()
                val json = cachedDb.getWorlds(queries).toJsonByteArray()
                call.respondBytes(json, ContentType.Application.Json)
            }

            get("/fortunes") {
                val fortunes = db.getFortunes()
                fortunes.add(Fortune(0, "Additional fortune added at request time."))
                fortunes.sortBy { it.message }
                call.respond(PebbleContent("fortunes.html", mapOf("fortunes" to fortunes)))
            }

            get("/updates") {
                val queries = call.parameters["queries"].toBoxedInt()
                val json = db.updateWorlds(queries).toJsonByteArray()
                call.respondBytes(json, ContentType.Application.Json)
            }
        }
    }

    server.start(wait = true)
}
