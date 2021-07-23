import com.dslplatform.json.CompiledJson

@CompiledJson(
    formats = [CompiledJson.Format.OBJECT],
    deserializeAs = Message::class,
    discriminator = "",
    minified = false,
    name = "",
    onUnknown = CompiledJson.Behavior.DEFAULT,
    typeSignature = CompiledJson.TypeSignature.DEFAULT,
    objectFormatPolicy = CompiledJson.ObjectFormatPolicy.DEFAULT
)
data class Message(val message: String)

@CompiledJson(
    formats = [CompiledJson.Format.OBJECT],
    deserializeAs = World::class,
    discriminator = "",
    minified = false,
    name = "",
    onUnknown = CompiledJson.Behavior.DEFAULT,
    typeSignature = CompiledJson.TypeSignature.DEFAULT,
    objectFormatPolicy = CompiledJson.ObjectFormatPolicy.DEFAULT
)
data class World(val id: Int, val randomNumber: Int)

data class Fortune(val id: Int, val message: String)
