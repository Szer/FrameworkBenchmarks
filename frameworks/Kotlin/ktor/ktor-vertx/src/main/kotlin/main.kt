import com.dslplatform.json.CompiledJson
import com.dslplatform.json.CompiledJson.Behavior
import com.dslplatform.json.CompiledJson.ObjectFormatPolicy
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
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Tuple
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.ArrayList

@CompiledJson(
    formats = [CompiledJson.Format.OBJECT],
    deserializeAs = Message::class,
    discriminator = "",
    minified = false,
    name = "",
    onUnknown = Behavior.DEFAULT,
    typeSignature = CompiledJson.TypeSignature.DEFAULT,
    objectFormatPolicy = ObjectFormatPolicy.DEFAULT
)
data class Message(val message: String)

@CompiledJson(
    formats = [CompiledJson.Format.OBJECT],
    deserializeAs = World::class,
    discriminator = "",
    minified = false,
    name = "",
    onUnknown = Behavior.DEFAULT,
    typeSignature = CompiledJson.TypeSignature.DEFAULT,
    objectFormatPolicy = ObjectFormatPolicy.DEFAULT
)
data class World(val id: Int, val randomNumber: Int)

data class Fortune(val id: Int, val message: String)

const val message = "Hello, World!"
val messageBytes = message.toByteArray(StandardCharsets.US_ASCII)
val messageBuffer = ByteBuffer.allocateDirect(messageBytes.size).put(messageBytes).flip() as ByteBuffer

fun randomWorld() = 1 + ThreadLocalRandom.current().nextInt(10000)

class VertxRepository(dbName: String?) {

    private val pgOptions =
        PgConnectOptions().apply {
            host = dbName ?: "tfb-database"
            database = "hello_world"
            user = "benchmarkdbuser"
            password = "benchmarkdbpass"
            cachePreparedStatements = true
        }

    private val sqlClient = run {
        val poolOptions = PoolOptions().setMaxSize(64)
        PgPool.pool(pgOptions, poolOptions)
    }

    private val selectWorld = sqlClient.preparedQuery("select id, randomNumber from world where id = $1")

    suspend fun getWorld(): World {
        val worldId = randomWorld()
        val row = selectWorld.execute(Tuple.of(worldId)).await().first()
        return World(row.getInteger(0), row.getInteger(1))
    }

    private val getFortunes = sqlClient.preparedQuery("select id, message from fortune")

    suspend fun getFortunes(): List<Fortune> {
        val results = getFortunes.execute().await()
        return results.map { Fortune(it.getInteger(0), it.getString(1)) }
    }

    private val updateWorlds = sqlClient.preparedQuery("update world set randomNumber = $1 where id = $2")

    suspend fun updateWorlds(worlds: List<World>) {
        val batches = worlds.map { Tuple.of(it.randomNumber, it.id) }
        updateWorlds.executeBatch(batches).await()
    }
}

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

fun main(args: Array<String>) {
    val db = VertxRepository(args.firstOrNull())
    val dslJson = DslJson(withRuntime<Any>().includeServiceLoader())
    val jsonWriter = object : ThreadLocal<JsonWriter>() {
        override fun initialValue(): JsonWriter =
            dslJson.newWriter(4096)
    }

    val server = embeddedServer(
        Netty, 8080,
        configure = {
            shareWorkGroup = true
        }
    ) {
        install(Pebble) {
            loader(
                ClasspathLoader().apply {
                    prefix = "templates"
                }
            )
        }
        install(DefaultHeaders)

        routing {
            get("/plaintext") {
                call.respondBytes(messageBytes, contentType = ContentType.Text.Plain)
            }

            get("/json") {
                val writer = jsonWriter.get()
                writer.reset()
                dslJson.serialize(writer, Message::class.java, Message(message))
                call.respondBytes(writer.toByteArray(), ContentType.Application.Json)
            }

            get("/db") {
                val writer = jsonWriter.get()
                writer.reset()
                dslJson.serialize(writer, World::class.java, db.getWorld())
                call.respondBytes(writer.toByteArray(), ContentType.Application.Json)
            }

            get("/query/") {
                val queries = call.parameters["queries"].toBoxedInt()
                val worlds = buildList(queries) {
                    for (i in 0 until queries) {
                        add(db.getWorld())
                    }
                }

                val writer = jsonWriter.get()
                writer.reset()
                dslJson.serialize(writer, worlds)
                call.respondBytes(writer.toByteArray(), ContentType.Application.Json)
            }

            get("/fortunes") {
                val fortunes = db.getFortunes().toMutableList()
                fortunes.add(Fortune(0, "Additional fortune added at request time."))
                fortunes.sortBy { it.message }
                call.respond(PebbleContent("fortunes.html", mapOf("fortunes" to fortunes)))
            }

            get("/updates") {
                val queries = call.parameters["queries"].toBoxedInt()
                val worlds = ArrayList<World>(queries)
                for (i in 0 until queries) {
                    worlds.add(World(db.getWorld().id, randomWorld()))
                }
                worlds.sortBy { it.id } // to prevent postgre deadlocks

                db.updateWorlds(worlds)

                val writer = jsonWriter.get()
                writer.reset()
                dslJson.serialize(writer, worlds)
                call.respondBytes(writer.toByteArray(), ContentType.Application.Json)
            }
        }
    }

    server.start(wait = true)
}
