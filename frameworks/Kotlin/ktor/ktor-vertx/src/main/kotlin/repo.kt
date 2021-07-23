import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.vertx.kotlin.coroutines.await
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.Tuple
import kotlinx.coroutines.*
import java.util.concurrent.ThreadLocalRandom

interface Db {
    suspend fun randomWorld(): World
    suspend fun getWorlds(count: Int): List<World>
    suspend fun getAllWorlds(): Map<Int, World>
    suspend fun updateWorlds(count: Int): List<World>
    suspend fun getFortunes(): MutableList<Fortune>
}

fun randomInt() = 1 + ThreadLocalRandom.current().nextInt(10000)

class VertxRepository(dbName: String?) : Db {

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

    override suspend fun randomWorld(): World {
        val worldId = randomInt()
        val row = selectWorld.execute(Tuple.of(worldId)).await().first()
        return World(row.getInteger(0), row.getInteger(1))
    }

    private val getFortunes = sqlClient.preparedQuery("select id, message from fortune")

    override suspend fun getFortunes(): ArrayList<Fortune> {
        val results = getFortunes.execute().await()
        val fortunes = ArrayList<Fortune>(results.rowCount())
        for (row in results) {
            fortunes.add(Fortune(row.getInteger(0), row.getString(1)))
        }
        return fortunes
    }

    private val updateWorlds = sqlClient.preparedQuery("update world set randomNumber = $1 where id = $2")

    override suspend fun updateWorlds(count: Int): List<World> {
        val worlds = ArrayList<World>(count)
        for (i in 0 until count) {
            worlds.add(World(randomWorld().id, randomInt()))
        }
        worlds.sortBy { it.id } // to prevent postgre deadlocks

        val tuples = worlds.map { Tuple.of(it.randomNumber, it.id) }
        updateWorlds.executeBatch(tuples).await()
        return worlds
    }

    override suspend fun getWorlds(count: Int): List<World> {
        val worlds = ArrayList<World>(count)
        for (i in 0 until count) {
            worlds.add(randomWorld())
        }
        return worlds
    }

    private val selectAllWorlds = sqlClient.preparedQuery("select id, randomNumber from world")

    override suspend fun getAllWorlds(): Map<Int, World> =
        selectAllWorlds.execute().await().let {
            buildMap {
                it.forEach {
                    val world = World(it.getInteger(0), it.getInteger(1))
                    this[world.id] = world
                }
            }
        }
}

class CachedDb(private val delegate: Db) : Db by delegate {
    private val cache: Cache<Int, World> = Caffeine.newBuilder()
        .executor(Dispatchers.Unconfined.asExecutor())
        .build<Int, World>()
        .apply {
            runBlocking {
                val allWorlds = delegate.getAllWorlds()
                putAll(allWorlds)
            }
        }

    override suspend fun getWorlds(count: Int): List<World> =
        (1..count).map { cache.getIfPresent(randomInt())!! }

    override suspend fun updateWorlds(count: Int): List<World> {
        val worlds = delegate.updateWorlds(count)
        for (world in worlds) {
            cache.put(world.id, world)
        }
        return worlds
    }
}
