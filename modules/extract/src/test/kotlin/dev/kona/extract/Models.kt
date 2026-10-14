package dev.kona.extract

class User : Extract<User>() {
    override val fillable = setOf("name", "email", "age", "admin", "settings", "born_at")
    override val casts = mapOf(
        "age" to CastType.INT,
        "admin" to CastType.BOOLEAN,
        "settings" to CastType.JSON,
        "born_at" to CastType.DATETIME,
    )

    companion object : ExtractCompanion<User>(User::class.java)
}

class Post : Extract<Post>(), SoftDeletes {
    override val guarded = emptySet<String>()
    override val perPage = 2

    companion object : ExtractCompanion<Post>(Post::class.java)
}

class Tag : Extract<Tag>() {
    override val table = "tags"
    override val primaryKey = "code"
    override val incrementing = false
    override val timestamps = false
    override val fillable = setOf("code", "label")

    companion object : ExtractCompanion<Tag>(Tag::class.java)
}

class Archive : Extract<Archive>(), SoftDeletes {
    override val timestamps = false
    override val guarded = setOf("id")
    override val deletedAtColumn = "removed_at"

    companion object : ExtractCompanion<Archive>(Archive::class.java)
}

/** Incrementing model on a table with no identity column: the driver returns no generated key. */
class Plain : Extract<Plain>() {
    override val table = "plain"
    override val timestamps = false
    override val guarded = emptySet<String>()
}

/** Default mass-assignment rules: totally guarded. */
class Locked : Extract<Locked>() {
    override val table = "users"
}

class Secondary : Extract<Secondary>() {
    override val table = "tags"
    override val primaryKey = "code"
    override val incrementing = false
    override val timestamps = false
    override val connectionName = "second"
}

class NoDefaultConstructor(@Suppress("unused") val marker: Int) : Extract<NoDefaultConstructor>()
