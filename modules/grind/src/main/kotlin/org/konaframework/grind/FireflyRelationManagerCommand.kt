package org.konaframework.grind

/**
 * `grind make:firefly-relation-manager <Resource> <relationship> <titleAttribute>
 * [--attach] [--associate] [--soft-deletes] [--panel=] [--lang=]` writes a class-based relation
 * manager next to its resource:
 *
 * ```
 * app/panels/{panel}/resources/{model}/relations/{Relationship}RelationManager.kt|.java
 * ```
 *
 * `--associate` adds associate / dissociate (has-many, morph-many), `--attach` adds attach /
 * detach (belongs-to-many, morph-to-many), `--soft-deletes` adds the trashed filter plus restore
 * and force delete. Register the class in the resource's `getRelations()`.
 */
class MakeFireflyRelationManagerCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.size != 3) {
            throw GrindException(
                "Usage: grind make:firefly-relation-manager <Resource> <relationship> <titleAttribute> " +
                    "[--attach] [--associate] [--soft-deletes] [--panel=] [--lang=]",
            )
        }
        val (resource, relationship, title) = positional
        Names.requireClassName("resource", resource)
        val model = Names.modelNameFromResourceInput(resource)
        Names.requireClassName("model", model)
        if (!IDENTIFIER.matches(relationship)) throw GrindException("Invalid relationship name [$relationship]: expected a method name like tasks")
        if (!IDENTIFIER.matches(title)) throw GrindException("Invalid title attribute [$title]: expected a column name like name")
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val panel = panelIdFromOptions(options)
        val flags = RelationFlags(
            attach = options["attach"] == "true",
            associate = options["associate"] == "true",
            softDeletes = options["soft-deletes"] == "true",
        )
        val className = relationship.substring(0, 1).uppercase() + relationship.substring(1) + "RelationManager"
        val pkg = "app.panels.$panel.resources.${model.lowercase()}.relations"
        Names.write(
            base,
            Names.sourceFile(lang, "app/panels/$panel/resources/${model.lowercase()}/relations", className),
            "Firefly relation manager",
            relationManagerSource(lang, pkg, className, relationship, title, flags),
        )
        return 0
    }
}

private val IDENTIFIER = Regex("^[a-z_][A-Za-z0-9_]*$")

internal data class RelationFlags(val attach: Boolean, val associate: Boolean, val softDeletes: Boolean)

internal fun relationManagerSource(
    lang: String,
    pkg: String,
    className: String,
    relationship: String,
    title: String,
    flags: RelationFlags,
): String {
    val header = ArrayList<String>()
    header.add("RelationAction.create()")
    if (flags.associate) header.add("RelationAction.associate()")
    if (flags.attach) header.add("RelationAction.attach()")
    val rows = arrayListOf("RelationAction.view()", "RelationAction.edit()")
    if (flags.associate) rows.add("RelationAction.dissociate()")
    if (flags.attach) rows.add("RelationAction.detach()")
    rows.add("RelationAction.delete()")
    if (flags.softDeletes) {
        rows.add("RelationAction.restore()")
        rows.add("RelationAction.forceDelete()")
    }
    val bulk = ArrayList<String>()
    if (flags.associate) bulk.add("DissociateBulkAction.make()")
    if (flags.attach) bulk.add("DetachBulkAction.make()")
    bulk.add("RelationBulkAction.delete()")
    if (flags.softDeletes) {
        bulk.add("RestoreBulkAction.make()")
        bulk.add("ForceDeleteBulkAction.make()")
    }
    val imports = sortedSetOf(
        "org.konaframework.firefly.RelationAction",
        "org.konaframework.firefly.RelationBulkAction",
        "org.konaframework.firefly.RelationManager",
        "org.konaframework.firefly.forms.TextInput",
        "org.konaframework.firefly.tables.BulkActionGroup",
        "org.konaframework.firefly.tables.TextColumn",
    )
    if (flags.associate) imports.add("org.konaframework.firefly.DissociateBulkAction")
    if (flags.attach) imports.add("org.konaframework.firefly.DetachBulkAction")
    if (flags.softDeletes) {
        imports.add("org.konaframework.firefly.RestoreBulkAction")
        imports.add("org.konaframework.firefly.ForceDeleteBulkAction")
        imports.add("org.konaframework.firefly.tables.TrashedFilter")
    }
    val filters = if (flags.softDeletes) ".filters(TrashedFilter.make())" else ""
    if (lang == "java") {
        return lines(
            "package $pkg;",
            "",
            *imports.map { "import $it;" }.toTypedArray(),
            "",
            "public class $className extends RelationManager {",
            "    public $className() {",
            "        super(\"$relationship\");",
            "        recordTitleAttribute(\"$title\");",
            "        form(schema -> schema.components(TextInput.make(\"$title\").required()));",
            "        table(table -> table.columns(TextColumn.make(\"$title\").searchable().sortable())$filters);",
            "        headerActions(${header.joinToString(", ")});",
            "        actions(${rows.joinToString(", ")});",
            "        bulkActions(BulkActionGroup.make(${bulk.joinToString(", ")}));",
            "    }",
            "}",
        )
    }
    return lines(
        "package $pkg",
        "",
        *imports.map { "import $it" }.toTypedArray(),
        "",
        "class $className : RelationManager(\"$relationship\") {",
        "    init {",
        "        recordTitleAttribute(\"$title\")",
        "        form { schema -> schema.components(TextInput.make(\"$title\").required()) }",
        "        table { table -> table.columns(TextColumn.make(\"$title\").searchable().sortable())$filters }",
        "        headerActions(${header.joinToString(", ")})",
        "        actions(${rows.joinToString(", ")})",
        "        bulkActions(BulkActionGroup.make(${bulk.joinToString(", ")}))",
        "    }",
        "}",
    )
}
