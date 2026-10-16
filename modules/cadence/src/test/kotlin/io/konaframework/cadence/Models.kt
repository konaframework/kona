package io.konaframework.cadence

class User : Cadence<User>() {
    override val fillable = setOf("name", "email", "age", "admin", "settings", "born_at")
    override val casts = mapOf(
        "age" to CastType.INT,
        "admin" to CastType.BOOLEAN,
        "settings" to CastType.JSON,
        "born_at" to CastType.DATETIME,
    )

    companion object : CadenceCompanion<User>(User::class.java)
}

class Post : Cadence<Post>(), SoftDeletes {
    override val guarded = emptySet<String>()
    override val perPage = 2

    companion object : CadenceCompanion<Post>(Post::class.java)
}

class Tag : Cadence<Tag>() {
    override val table = "tags"
    override val primaryKey = "code"
    override val incrementing = false
    override val timestamps = false
    override val fillable = setOf("code", "label")

    companion object : CadenceCompanion<Tag>(Tag::class.java)
}

class Archive : Cadence<Archive>(), SoftDeletes {
    override val timestamps = false
    override val guarded = setOf("id")
    override val deletedAtColumn = "removed_at"

    companion object : CadenceCompanion<Archive>(Archive::class.java)
}

/** Incrementing model on a table with no identity column: the driver returns no generated key. */
class Plain : Cadence<Plain>() {
    override val table = "plain"
    override val timestamps = false
    override val guarded = emptySet<String>()
}

/** Default mass-assignment rules: totally guarded. */
class Locked : Cadence<Locked>() {
    override val table = "users"
}

class Secondary : Cadence<Secondary>() {
    override val table = "tags"
    override val primaryKey = "code"
    override val incrementing = false
    override val timestamps = false
    override val connectionName = "second"
}

class NoDefaultConstructor(@Suppress("unused") val marker: Int) : Cadence<NoDefaultConstructor>()
