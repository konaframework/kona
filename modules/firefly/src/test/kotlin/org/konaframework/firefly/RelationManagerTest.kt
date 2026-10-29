package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.SoftDeletes
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.notifications.FireflyNotification
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.BulkAction
import org.konaframework.firefly.tables.BulkActionGroup
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.tables.TrashedFilter
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.net.URLEncoder
import java.util.function.Function

class RelationManagerTest {
    private lateinit var p1: RmProject
    private lateinit var p2: RmProject

    @BeforeEach
    fun seed() {
        val url = "jdbc:h2:mem:ffrel${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
        val connection = database.connection()
        connection.statement("create table rm_projects (id bigint auto_increment primary key, name varchar(100), team varchar(20))")
        connection.statement(
            "create table rm_tasks (id bigint auto_increment primary key, project_id bigint, name varchar(100), deleted_at timestamp)",
        )
        connection.statement("create table rm_users (id bigint auto_increment primary key, name varchar(100))")
        connection.statement("create table rm_project_user (project_id bigint, user_id bigint, role varchar(20))")
        connection.statement(
            "create table rm_comments (id bigint auto_increment primary key, commentable_type varchar(50), commentable_id bigint, body varchar(100))",
        )
        connection.statement("create table rm_tags (id bigint auto_increment primary key, name varchar(100), deleted_at timestamp)")
        connection.statement("create table rm_taggables (tag_id bigint, taggable_id bigint, taggable_type varchar(50))")
        connection.statement("create table rm_milestones (id bigint auto_increment primary key, project_id bigint, name varchar(100))")
        connection.statement("create table rm_checks (id bigint auto_increment primary key, milestone_id bigint, name varchar(100))")
        database.setAsGlobal()
        p1 = RmProject().fill(mapOf("name" to "Kona", "team" to "a")).also { it.save() }
        p2 = RmProject().fill(mapOf("name" to "Other", "team" to "b")).also { it.save() }
    }

    // ——— Has many (with soft deletes) ———

    @Test
    fun hasManyListsOnlyOwnerRowsWithFilamentDefaults() {
        val alpha = task(p1, "Alpha")
        task(p1, "Beta")
        task(p2, "Gamma")
        task(null, "Loose")
        val router = mount(projects(tasks()))
        val edit = page(router, "edit", p1)
        val section = edit.substringAfter("""data-ff-relation="tasks"""")
        assertTrue(section.contains("Alpha"), section)
        assertTrue(section.contains("Beta"))
        assertFalse(section.substringBefore("data-ff-combobox").contains(">Gamma<"))
        assertTrue(edit.contains("""data-ff-kind="has_many""""))
        assertTrue(edit.contains("""<h2 class="ff-relation-heading">Tasks"""))
        assertTrue(edit.contains(">New task<"), edit)
        assertTrue(edit.contains(">Associate<"))
        assertTrue(edit.contains(">Dissociate<"))
        assertTrue(edit.contains(">Edit<"))
        assertTrue(edit.contains(">View<"))
        assertTrue(edit.contains(">Delete<"))
        assertFalse(edit.contains("""data-ff-action="restore""""), "restore only shows on trashed rows")
        assertTrue(edit.contains("Dissociate selected"))
        assertTrue(edit.contains("Restore selected"))
        assertTrue(edit.contains("Deleted records"), "soft-deleting related model gets the trashed filter")
        assertTrue(edit.contains("/admin/projects/relations/create?relation=tasks&amp;owner=${p1.getKey()}&amp;page=edit"), edit)
        assertTrue(edit.contains("/admin/projects/relations/bulk?relation=tasks"))
        assertTrue(edit.contains("""data-ff-search-url="/admin/projects/relations/options?relation=tasks&amp;owner=${p1.getKey()}&amp;page=edit&amp;op=associate""""))
        // Associate picker preloads rows that are not already the owner's.
        val picker = edit.substringAfter("""name="recordId"""", "").ifEmpty { edit }
        assertTrue(edit.contains("Gamma"))
        assertTrue(edit.contains("Loose"))
        assertNotNull(picker)
        // Table links keep the owner id and relation (soft navigation).
        assertTrue(edit.contains("id=${p1.getKey()}"))
        assertTrue(edit.contains("relation=tasks"))
        // Row view / edit dialogs are filled from the record.
        assertTrue(edit.contains("""value="Alpha""""), edit)
        assertTrue(edit.contains("""name="id" value="${alpha.getKey()}""""))
        assertTrue(edit.contains("Edit task"))

        val view = page(router, "view", p1)
        assertTrue(view.contains("""data-ff-read-only="true""""))
        assertTrue(view.contains("Alpha"))
        assertFalse(view.contains(">New task<"))
        assertFalse(view.contains(">Dissociate<"))
        assertFalse(view.contains("Dissociate selected"))
        assertTrue(view.contains(">View<"))
        assertFalse(view.contains("data-ff-dialog-switch=\"edit\""), "read-only view dialog has no edit switch")
    }

    @Test
    fun hasManyCreateEditAssociateDissociateDeleteRestoreForceDelete() {
        val alpha = task(p1, "Alpha")
        val gamma = task(p2, "Gamma")
        val loose = task(null, "Loose")
        val router = mount(projects(tasks()))
        val session = RmSession()

        val created = relationPost(router, "create", p1, "name=Fresh", session = session)
        assertEquals(302, created.status)
        assertEquals("/admin/projects/edit?id=${p1.getKey()}&relation=tasks", created.headers["Location"])
        assertEquals(p1.getKey().toString(), RmTask().newQuery().where("name", "Fresh").first()!!.getAttribute("project_id").toString())
        assertToast(session, "Created", "success")

        val invalid = relationPost(router, "create", p1, "name=", session = session)
        assertEquals(302, invalid.status)
        assertToast(session, "Please fix the form", "danger")
        assertEquals(0L, RmTask().newQuery().where("name", "").count())

        relationPost(router, "edit", p1, "id=${alpha.getKey()}&name=Alpha2", session = session)
        assertEquals("Alpha2", RmTask().newQuery().withTrashed().where("id", alpha.getKey()).first()!!.getAttribute("name"))
        assertToast(session, "Saved", "success")
        assertEquals(302, relationPost(router, "edit", p1, "id=${alpha.getKey()}&name=").status)
        assertEquals(404, relationPost(router, "edit", p1, "id=${gamma.getKey()}&name=Stolen").status, "other owner's row")
        assertEquals(404, relationPost(router, "edit", p1, "name=NoId").status)

        relationPost(router, "associate", p1, "recordId=${gamma.getKey()},${loose.getKey()}", session = session)
        assertToast(session, "Associated", "success")
        assertEquals(p1.getKey().toString(), reload(gamma).getAttribute("project_id").toString())
        assertEquals(p1.getKey().toString(), reload(loose).getAttribute("project_id").toString())
        relationPost(router, "associate", p1, "recordId=", session = session)
        assertToast(session, "Select at least one task", "danger")
        relationPost(router, "associate", p1, "recordId=99999", session = session)
        assertToast(session, "task [99999] cannot be associated", "danger")

        relationPost(router, "dissociate", p1, "id=${gamma.getKey()}", session = session)
        assertNull(reload(gamma).getAttribute("project_id"))
        assertToast(session, "Dissociated", "success")

        relationPost(router, "delete", p1, "id=${loose.getKey()}", session = session)
        assertTrue((reload(loose) as SoftDeletes).trashed())
        val trashedView = get(router, "/admin/projects/edit", mapOf("id" to p1.getKey().toString(), "relation" to "tasks", "filters" to "1", "filters.trashed" to "only"))
        assertTrue(trashedView.contains("""data-ff-action="restore""""))
        assertTrue(trashedView.contains("""data-ff-action="force-delete""""))
        relationPost(router, "restore", p1, "id=${loose.getKey()}", session = session)
        assertFalse((reload(loose) as SoftDeletes).trashed())
        assertToast(session, "Restored", "success")
        relationPost(router, "restore", p1, "id=${loose.getKey()}", session = session)
        assertToast(session, "The record is not deleted", "danger")
        assertEquals(404, relationPost(router, "restore", p1, "id=99999").status)
        relationPost(router, "delete", p1, "id=${loose.getKey()}")
        relationPost(router, "force-delete", p1, "id=${loose.getKey()}", session = session)
        assertNull(RmTask().newQuery().withTrashed().where("id", loose.getKey()).first())

        assertEquals(403, relationPost(router, "attach", p1, "recordId=1").status, "has-many does not attach")
        assertEquals(403, relationPost(router, "detach", p1, "id=1").status)
    }

    @Test
    fun hasManyBulkActionsOptionsAndRouteGuards() {
        val alpha = task(p1, "Alpha")
        val beta = task(p1, "Beta")
        val gamma = task(p2, "Gamma")
        val router = mount(projects(tasks()))
        val session = RmSession()

        val bulk = relationPost(router, "bulk", p1, "_bulk_action=dissociate&_bulk_keys=${alpha.getKey()},${gamma.getKey()}", session = session)
        assertEquals(302, bulk.status)
        assertNull(reload(alpha).getAttribute("project_id"))
        assertEquals(p2.getKey().toString(), reload(gamma).getAttribute("project_id").toString(), "out of scope key untouched")
        assertToast(session, "1 of 2 dissociated", "warning")
        relationPost(router, "bulk", p1, "_bulk_action=delete&_bulk_keys=${beta.getKey()}", session = session)
        assertTrue((reload(beta) as SoftDeletes).trashed())
        relationPost(router, "bulk", p1, "_bulk_action=restore&_bulk_keys=${beta.getKey()}", session = session)
        assertFalse((reload(beta) as SoftDeletes).trashed())
        relationPost(router, "bulk", p1, "_bulk_action=restore&_bulk_keys=${beta.getKey()}", session = session)
        assertToast(session, "Nothing was restored", "danger")
        relationPost(router, "bulk", p1, "_bulk_action=delete&_bulk_keys=${beta.getKey()}")
        relationPost(router, "bulk", p1, "_bulk_action=forceDelete&_bulk_keys=${beta.getKey()}")
        assertNull(RmTask().newQuery().withTrashed().where("id", beta.getKey()).first())
        assertEquals(422, relationPost(router, "bulk", p1, "_bulk_keys=1").status)
        assertEquals(422, relationPost(router, "bulk", p1, "_bulk_action=nope&_bulk_keys=1").status)
        assertEquals(403, relationPost(router, "bulk", p1, "_bulk_action=dissociate&_bulk_keys=1", page = "view").status)

        val options = options(router, p1, "associate", "Gam")
        assertTrue(options.contains("\"label\":\"Gamma\""), options)
        assertTrue(options.contains("\"enabled\":true"))
        assertFalse(options.contains("Beta"))
        assertEquals("[]", options(router, p1, "associate", "", page = "view"))
        assertEquals("[]", options(router, p1, "attach", ""), "has-many offers no attach picker")
        assertEquals(422, optionsResponse(router, p1, "nope", "").status)

        assertEquals(403, relationPost(router, "create", p1, "name=X", page = "view").status)
        assertEquals(422, relationPost(router, "create", p1, "name=X", page = "list").status)
        assertEquals(404, post(router, "create", mapOf("relation" to "nope", "owner" to p1.getKey().toString(), "page" to "edit"), "").status)
        assertEquals(404, post(router, "create", mapOf("relation" to "tasks", "owner" to "99999", "page" to "edit"), "").status)
        assertEquals(404, post(router, "create", mapOf("relation" to "tasks", "owner" to "", "page" to "edit"), "").status)
        assertNull(router.find("POST", "/admin/projects/relations/view"), "view is not a POST operation")

        // CSRF: a session token must match.
        val guarded = RmSession().also { it.put("_token", "secret") }
        assertEquals(419, relationPost(router, "create", p1, "name=X", session = guarded).status)
        assertEquals(302, relationPost(router, "create", p1, "name=X&_token=secret", session = guarded).status)
    }

    // ——— Belongs to many (pivot) ———

    @Test
    fun belongsToManyAttachDetachCreateEditWithPivotRole() {
        val ada = user("Ada")
        val bob = user("Bob")
        val cy = user("Cy")
        p1.members().attach(listOf(ada.getKey()), mapOf("role" to "lead"))
        p2.members().attach(listOf(bob.getKey()), mapOf("role" to "dev"))
        lastRelation = "members"
        val router = mount(projects(members()))
        val session = RmSession()

        val edit = page(router, "edit", p1)
        val table = edit.substringAfter("""data-ff-relation="members"""")
        assertTrue(edit.contains("""data-ff-kind="belongs_to_many""""))
        assertTrue(table.contains("Ada"))
        assertTrue(table.contains("lead"), "pivot column reads into the row")
        assertTrue(edit.contains(">Attach<"))
        assertTrue(edit.contains(">Detach<"))
        assertTrue(edit.contains("Detach selected"))
        assertTrue(edit.contains(">New member<"))
        assertTrue(edit.contains("""name="role""""), "attach form carries pivot fields")
        assertFalse(edit.contains("Deleted records"), "no soft deletes, no trashed filter")

        val options = options(router, p1, "attach", "")
        assertTrue(options.contains("Bob"))
        assertTrue(options.contains("Cy"))
        assertFalse(options.contains("Ada"), "already attached")

        relationPost(router, "attach", p1, "recordId=${bob.getKey()},${cy.getKey()}&role=dev", session = session)
        assertToast(session, "Attached", "success")
        assertEquals(setOf("Ada", "Bob", "Cy"), p1.members().get().map { it.getAttribute("name") }.toSet())
        assertEquals("dev", pivotRole(p1, bob))
        relationPost(router, "attach", p1, "recordId=${bob.getKey()}", session = session)
        assertToast(session, "member [${bob.getKey()}] cannot be attached", "danger")

        relationPost(router, "create", p1, "name=Dee&role=qa", session = session)
        val dee = RmUser().newQuery().where("name", "Dee").first()!!
        assertEquals("qa", pivotRole(p1, dee))

        relationPost(router, "edit", p1, "id=${ada.getKey()}&name=Ada2&role=owner", session = session)
        assertEquals("Ada2", reloadUser(ada).getAttribute("name"))
        assertEquals("owner", pivotRole(p1, ada))
        assertEquals("dev", pivotRole(p2, bob), "other owner's pivot untouched")

        relationPost(router, "detach", p1, "id=${bob.getKey()}", session = session)
        assertToast(session, "Detached", "success")
        assertFalse(p1.members().get().any { it.getKey() == bob.getKey() })
        assertNotNull(RmUser().newQuery().where("id", bob.getKey()).first(), "detach keeps the user")
        assertEquals(404, relationPost(router, "detach", p1, "id=${bob.getKey()}").status)

        relationPost(router, "delete", p1, "id=${cy.getKey()}", session = session)
        assertNull(RmUser().newQuery().where("id", cy.getKey()).first())
        assertEquals(0L, org.konaframework.cadence.DB.table("rm_project_user").where("user_id", cy.getKey()).count())

        relationPost(router, "bulk", p1, "_bulk_action=detach&_bulk_keys=${dee.getKey()}")
        assertFalse(p1.members().get().any { it.getKey() == dee.getKey() })
        assertEquals(403, relationPost(router, "associate", p1, "recordId=1").status)
        assertEquals(403, relationPost(router, "restore", p1, "id=1").status, "no soft deletes, no restore")
    }

    @Test
    fun attachAllowsDuplicatesSearchColumnsOptionsQueryAndLimit() {
        val ada = user("Ada")
        user("Adam")
        user("Bob")
        p1.members().attach(listOf(ada.getKey()), mapOf("role" to "lead"))
        lastRelation = "members"
        val seen = ArrayList<String>()
        val manager = RelationManager("members").recordTitleAttribute("name").pivotColumns("role")
            .headerActions(
                RelationAction.attach().allowDuplicates().multiple().preloadRecordSelect(false)
                    .recordSelectSearchColumns("id")
                    .recordSelectOptionsLimit(5)
                    .recordSelectOptionsQuery { query, context ->
                        seen.add(context.operation.token)
                        (query as ModelBuilder<*>).where("name", "<>", "Bob")
                    },
            )
        val router = mount(projects(manager))
        val edit = page(router, "edit", p1)
        assertTrue(edit.contains("""data-ff-multiple="true""""), edit)
        assertFalse(edit.contains(">New member<"), "custom header actions replace defaults")
        val options = options(router, p1, "attach", "Ad")
        assertTrue(options.contains("Ada"), "duplicates allowed")
        assertTrue(options.contains("Adam"))
        assertFalse(options.contains("Bob"))
        assertTrue(seen.contains("attach"))
        relationPost(router, "attach", p1, "recordId=${ada.getKey()}")
        assertEquals(2L, org.konaframework.cadence.DB.table("rm_project_user").where("user_id", ada.getKey()).count())
    }

    // ——— Morph many / morph to many / has many through ———

    @Test
    fun morphManyMorphToManyAndHasManyThroughScopeToOwner() {
        val mine = RmComment().fill(mapOf("commentable_type" to p1.getMorphClass(), "commentable_id" to p1.getKey(), "body" to "Mine")).also { it.save() }
        RmComment().fill(mapOf("commentable_type" to p2.getMorphClass(), "commentable_id" to p2.getKey(), "body" to "Theirs")).save()
        val stray = RmComment().fill(mapOf("commentable_type" to "Elsewhere", "commentable_id" to p1.getKey(), "body" to "Stray")).also { it.save() }
        val red = RmTag().fill(mapOf("name" to "red")).also { it.save() }
        val blue = RmTag().fill(mapOf("name" to "blue")).also { it.save() }
        p1.tags().attach(listOf(red.getKey()))
        val m1 = RmMilestone().fill(mapOf("project_id" to p1.getKey(), "name" to "M1")).also { it.save() }
        val m2 = RmMilestone().fill(mapOf("project_id" to p2.getKey(), "name" to "M2")).also { it.save() }
        val check = RmCheck().fill(mapOf("milestone_id" to m1.getKey(), "name" to "CheckOne")).also { it.save() }
        RmCheck().fill(mapOf("milestone_id" to m2.getKey(), "name" to "CheckTwo")).save()
        val router = mount(
            projects(
                RelationManager("comments").recordTitleAttribute("body").form(Schema.make().components(TextInput.make("body"))),
                RelationManager("tags").recordTitleAttribute("name").form(Schema.make().components(TextInput.make("name"))),
                RelationManager("checks").recordTitleAttribute("name").form(Schema.make().components(TextInput.make("name"))),
            ),
        )
        val comments = get(router, "/admin/projects/edit", mapOf("id" to p1.getKey().toString(), "relation" to "comments"))
        val commentsPanel = comments.substringAfter("""data-ff-relation-panel="comments"""").substringBefore("data-ff-relation-panel=")
        assertTrue(commentsPanel.contains("Mine"))
        assertFalse(commentsPanel.substringAfter("<tbody").contains(">Theirs<"))
        assertTrue(comments.contains("""data-ff-kind="morph_many""""))
        assertTrue(comments.contains("""data-ff-kind="morph_to_many""""))
        assertTrue(comments.contains("""data-ff-kind="has_many_through""""))
        assertTrue(comments.contains("""data-ff-relation-tab="comments" aria-selected="true""""))
        assertTrue(comments.contains("""data-ff-relation-tab="tags" aria-selected="false""""))
        assertTrue(comments.contains("""data-ff-relation-panel="tags" hidden"""))

        relationPost(router, "associate", p1, "recordId=${stray.getKey()}", relation = "comments")
        assertEquals(p1.getMorphClass(), RmComment().newQuery().where("id", stray.getKey()).first()!!.getAttribute("commentable_type"))
        relationPost(router, "dissociate", p1, "id=${mine.getKey()}", relation = "comments")
        val dissociated = RmComment().newQuery().where("id", mine.getKey()).first()!!
        assertNull(dissociated.getAttribute("commentable_id"))
        assertNull(dissociated.getAttribute("commentable_type"))
        assertTrue(options(router, p1, "associate", "", relation = "comments").contains("Mine"))
        relationPost(router, "create", p1, "body=Born", relation = "comments")
        assertEquals(p1.getMorphClass(), RmComment().newQuery().where("body", "Born").first()!!.getAttribute("commentable_type"))

        val tags = options(router, p1, "attach", "", relation = "tags")
        assertTrue(tags.contains("blue"))
        assertFalse(tags.contains("red"))
        relationPost(router, "attach", p1, "recordId=${blue.getKey()}", relation = "tags")
        assertEquals(setOf("red", "blue"), p1.tags().get().map { it.getAttribute("name") }.toSet())
        relationPost(router, "detach", p1, "id=${red.getKey()}", relation = "tags")
        assertEquals(setOf("blue"), p1.tags().get().map { it.getAttribute("name") }.toSet())

        val checks = get(router, "/admin/projects/edit", mapOf("id" to p1.getKey().toString(), "relation" to "checks"))
        val checksPanel = checks.substringAfter("""data-ff-relation-panel="checks"""")
        assertTrue(checksPanel.contains("CheckOne"))
        assertFalse(checksPanel.contains("CheckTwo"))
        assertFalse(checksPanel.contains(">New check<"), "through relations do not create")
        assertEquals(403, relationPost(router, "create", p1, "name=X", relation = "checks").status)
        assertEquals(403, relationPost(router, "associate", p1, "recordId=1", relation = "checks").status)
        relationPost(router, "edit", p1, "id=${check.getKey()}&name=CheckOne2", relation = "checks")
        assertEquals("CheckOne2", RmCheck().newQuery().where("id", check.getKey()).first()!!.getAttribute("name"))
        relationPost(router, "delete", p1, "id=${check.getKey()}", relation = "checks")
        assertNull(RmCheck().newQuery().where("id", check.getKey()).first())
    }

    // ——— Authorization, read-only, customization ———

    @Test
    fun authorizationPredicatesHideActionsAndRejectPosts() {
        val alpha = task(p1, "Alpha")
        val locked = task(p1, "Locked")
        val manager = tasks()
            .canCreate { false }
            .canDelete { context -> context.get("name") != "Locked" }
            .canEdit { context -> context.record != null }
            .canAssociate { true }
            .canDissociate { true }
            .canView { true }
            .canRestore { true }
            .canForceDelete { true }
            .canAttach { true }
            .canDetach { true }
        val router = mount(projects(manager))
        val edit = page(router, "edit", p1)
        assertFalse(edit.contains(">New task<"))
        assertEquals(403, relationPost(router, "create", p1, "name=X").status)
        assertEquals(403, relationPost(router, "delete", p1, "id=${locked.getKey()}").status)
        assertEquals(302, relationPost(router, "delete", p1, "id=${alpha.getKey()}").status)
        val session = RmSession()
        relationPost(router, "bulk", p1, "_bulk_action=delete&_bulk_keys=${locked.getKey()},99999", session = session)
        assertToast(session, "Nothing was deleted", "danger")
        assertFalse((reload(locked) as SoftDeletes).trashed())

        val gated = tasks().canViewForRecord { context -> context.owner.let { (it as RmProject).getAttribute("team") == "b" } }
        val gatedRouter = mount(projects(gated))
        assertFalse(page(gatedRouter, "edit", p1).contains("data-ff-relation="))
        assertTrue(page(gatedRouter, "edit", p2).contains("""data-ff-relation="tasks""""))
        assertEquals(403, relationPost(gatedRouter, "create", p1, "name=X").status)

        val actionGate = tasks().headerActions(RelationAction.create().authorize { false }, RelationAction.associate().hidden { true })
            .actions(RelationAction.edit().visible { context -> context.get("name") == "Locked" }, RelationAction.delete())
        val actionRouter = mount(projects(actionGate))
        val gatedEdit = page(actionRouter, "edit", p1)
        assertFalse(gatedEdit.contains(">New task<"))
        assertFalse(gatedEdit.contains(">Associate<"))
        assertEquals(403, relationPost(actionRouter, "create", p1, "name=X").status)
        assertEquals(403, relationPost(actionRouter, "dissociate", p1, "id=${alpha.getKey()}").status, "not in the action list")
        assertEquals(403, relationPost(actionRouter, "restore", p1, "id=${alpha.getKey()}").status)
    }

    @Test
    fun readOnlyOverridesTitlesBadgesHooksAndModifyQuery() {
        task(p1, "Alpha")
        task(p1, "Hidden")
        val loose = task(null, "Loose")
        val events = ArrayList<String>()
        val manager = RelationManager("tasks")
            .title("Work items")
            .modelLabel("work item")
            .pluralModelLabel("work items")
            .recordTitleAttribute("name")
            .recordTitle { record -> "#" + (record as Cadence<*>).getAttribute("name") }
            .inverseRelationship("project")
            .badge { context -> (context.owner as RmProject).tasks().get().size.toString() }
            .readOnly(false)
            .modifyQueryUsing { query, _ -> (query as ModelBuilder<*>).where("name", "<>", "Hidden") }
            .form { schema -> schema.components(TextInput.make("name")) }
            .infolist { schema -> schema.components(TextEntry.make("name").label("Task name")) }
            .headerActions(
                RelationAction.create().label("Add").modalHeading("Add work").modalSubmitActionLabel("Add it")
                    .successNotificationTitle("Added").slideOver()
                    .mutateFormDataUsing { data -> data + ("name" to (data["name"].toString() + "!")) }
                    .before { events.add("before:" + it.operation.token) }
                    .after { events.add("after:" + (it.record as Cadence<*>).getAttribute("name")) },
                RelationAction.associate().modalDescription("Pick one").form { schema -> schema.components(TextInput.make("name")) },
            )
            .actions(RelationAction.view().slideOver(), RelationAction.edit(), RelationAction.delete().modalDescription("Gone?"))
            .bulkActions(BulkActionGroup.make(RelationBulkAction.delete(), BulkAction.make("noop").action { }))
        assertEquals("Work items", manager.title())
        assertEquals("work item", manager.modelLabel())
        assertEquals("work items", manager.pluralModelLabel())
        assertEquals("project", manager.inverseRelationship())
        assertEquals("tasks", manager.relationship())
        val router = mount(projects(manager))
        val view = page(router, "view", p1)
        assertFalse(view.contains("data-ff-read-only"), "readOnly(false) keeps the view page editable")
        assertTrue(view.contains("Work items<span class=\"ff-relation-badge\">2</span>"), view)
        assertTrue(view.contains(">Add<"))
        assertTrue(view.contains("Add work"))
        assertTrue(view.contains("Add it"))
        assertTrue(view.contains("Pick one"))
        assertTrue(view.contains("Gone?"))
        assertTrue(view.contains("Task name"))
        assertTrue(view.contains("#Loose"), "record title closure labels picker options")
        assertFalse(view.substringAfter("<tbody").contains(">Hidden<"), "modifyQueryUsing narrows the table")
        assertTrue(view.contains("ff-dialog-sidebar"), "slide-over dialogs")
        val session = RmSession()
        relationPost(router, "create", p1, "name=New", page = "view", session = session)
        assertToast(session, "Added", "success")
        assertEquals(listOf("before:create", "after:New!"), events)
        val hidden = RmTask().newQuery().where("name", "Hidden").first()!!
        assertEquals(404, relationPost(router, "edit", p1, "id=${hidden.getKey()}&name=x", page = "view").status)
        relationPost(router, "associate", p1, "recordId=${loose.getKey()}&name=Renamed", page = "view")
        assertEquals("Renamed", reload(loose).getAttribute("name"))
        relationPost(router, "bulk", p1, "_bulk_action=noop&_bulk_keys=${loose.getKey()}", page = "view")

        val forced = mount(projects(tasks().readOnly(true)))
        assertTrue(page(forced, "edit", p1).contains("""data-ff-read-only="true""""))
        assertEquals(403, relationPost(forced, "create", p1, "name=X").status)
    }

    @Test
    fun legacyApisDefaultsAndMisconfiguration() {
        val manager = RelationManager("teamMembers")
        assertEquals("Team members", manager.title())
        assertEquals("team member", manager.modelLabel())
        assertEquals("team members", manager.pluralModelLabel())
        assertNull(manager.inverseRelationship())
        assertTrue(manager.isReadOnly(ViewPage("view")))
        assertFalse(manager.isReadOnly(EditPage("edit")))
        assertFalse(manager.isReadOnly(null))
        assertEquals("category", RelationManager("categories").modelLabel())
        assertEquals("box", RelationManager("boxes").modelLabel())
        assertEquals("glass", RelationManager("glass").modelLabel())
        assertEquals("y", RelationManager("ies").modelLabel())
        assertEquals("sheep", RelationManager("sheep").modelLabel())
        assertThrows<FireflyException> { manager.title(" ") }
        assertThrows<FireflyException> { manager.modelLabel(" ") }
        assertThrows<FireflyException> { manager.pluralModelLabel(" ") }
        assertThrows<FireflyException> { manager.inverseRelationship(" ") }
        assertThrows<FireflyException> { manager.pivotColumns("bad column") }
        val badged = mount(projects(tasks().badge("7")))
        assertTrue(page(badged, "edit", p1).contains("""<span class="ff-relation-badge">7</span>"""))

        // Standalone render without an owner still works (legacy API).
        assertTrue(RelationManager("tasks").form(Schema.make().components(TextInput.make("name"))).render().contains("ff-input"))
        assertThrows<FireflyException> { RelationManager("tasks").bind("not a model", null, "relations", "") }
        assertThrows<FireflyException> { RelationManager("tasks").bind(RmProject(), null, "relations", "") }
        val runtime = RelationManager("tasks").bind(p1, null, "relations", "")
        assertEquals("edit", runtime.pageSlug)

        // Undefined or unsupported relationships fail loudly instead of rendering fake tables.
        val undefined = mount(projects(RelationManager("nothing")))
        assertThrows<FireflyException> { page(undefined, "edit", p1) }
        val unsupported = mount(projects(RelationManager("lead")))
        assertThrows<FireflyException> { page(unsupported, "edit", p1) }
        assertEquals(422, relationPost(unsupported, "create", p1, "name=x", relation = "lead").status)
        val memory = mount(projects(RelationManager("tasks").table(Table.memory(mapOf("id" to "1", "name" to "Fake")).columns(TextColumn.make("name")))))
        assertThrows<FireflyException> { page(memory, "edit", p1) }

        // Shared table instance: scoped once, filter not duplicated, user bulk actions adopted.
        task(p1, "Alpha")
        val shared = Table.forModel(RmTask::class.java).columns(TextColumn.make("name")).filters(TrashedFilter.make())
            .bulkActions(DissociateBulkAction.make())
        val sharedRouter = mount(projects(RelationManager("tasks").recordTitleAttribute("name").table(shared)))
        page(sharedRouter, "view", p1)
        val again = page(sharedRouter, "edit", p1)
        assertEquals(1, shared.filterSet().filters().count { it is TrashedFilter })
        assertTrue(again.contains("Dissociate selected"))
        assertFalse(again.contains("Restore selected"))
        assertTrue(again.contains("Alpha"))
        // Default table: the title attribute column.
        val plain = mount(projects(RelationManager("tasks").recordTitleAttribute("name")))
        assertTrue(page(plain, "edit", p1).contains("Alpha"))
        // Missing owner: nothing renders.
        assertFalse(get(plain, "/admin/projects/edit", mapOf("id" to "99999")).contains("data-ff-relation="))
        // Relation bulk actions outside a relation table refuse to run.
        val loneTable = Table.memory(mapOf("id" to "1")).bulkActions(DetachBulkAction.make())
        assertThrows<FireflyException> { loneTable.runBulkAction("detach", listOf("1"), emptyMap(), false) }
        assertEquals("forceDelete", ForceDeleteBulkAction.make().name)
        assertEquals("restore", RestoreBulkAction.make().name)
        assertEquals(RelationOperation.DELETE, RelationBulkAction.delete().operation)
    }

    @Test
    fun tenantScopedOwnersAndValidationOnPivotAndAssociateForms() {
        val tenantRouter = Router()
        val resource = projects(tasks()).tenant("team")
        Panel().path("admin").authenticate(false).tenant(Tenant("a")).tenant(Tenant("b")).resource(resource).mount(tenantRouter)
        assertEquals(404, post(tenantRouter, "create", mapOf("relation" to "tasks", "owner" to p1.getKey().toString(), "page" to "edit", "tenant" to "b"), "name=X").status)
        assertEquals(302, post(tenantRouter, "create", mapOf("relation" to "tasks", "owner" to p1.getKey().toString(), "page" to "edit", "tenant" to "a"), "name=X").status)
        assertEquals(302, post(tenantRouter, "create", mapOf("relation" to "tasks", "owner" to p1.getKey().toString(), "page" to "edit"), "name=Y").status)

        val bob = user("Bob")
        val strict = RelationManager("members").recordTitleAttribute("name").pivotColumns("role")
            .headerActions(RelationAction.attach().form { schema -> schema.components(TextInput.make("role").required()) })
        val router = mount(projects(strict))
        val session = RmSession()
        relationPost(router, "attach", p1, "recordId=${bob.getKey()}&role=", relation = "members", session = session)
        assertToast(session, "Please fix the form", "danger")
        assertTrue(p1.members().get().isEmpty())
        val denied = RelationManager("members").recordTitleAttribute("name").canAttach { context -> context.record == null }
        assertEquals(403, relationPost(mount(projects(denied)), "attach", p1, "recordId=${bob.getKey()}", relation = "members").status)
    }

    @Test
    fun relationActionApi() {
        assertEquals("New task", RelationAction.create().getLabel("task"))
        assertEquals("Attach", RelationAction.attach().getLabel("user"))
        assertEquals("Force delete", RelationAction.forceDelete().getLabel("task"))
        assertEquals("Custom", RelationAction.detach().label("Custom").getLabel("x"))
        assertEquals(RelationOperation.FORCE_DELETE, RelationOperation.fromToken("force-delete"))
        assertNull(RelationOperation.fromToken("nope"))
        assertTrue(RelationKind.MORPH_MANY.isOneToMany())
        assertFalse(RelationKind.HAS_MANY_THROUGH.isOneToMany())
        assertTrue(RelationKind.MORPH_TO_MANY.isManyToMany())
        assertFalse(RelationKind.HAS_MANY.isManyToMany())
        assertThrows<FireflyException> { RelationAction.create().label(" ") }
        assertThrows<FireflyException> { RelationAction.create().modalHeading(" ") }
        assertThrows<FireflyException> { RelationAction.create().modalSubmitActionLabel(" ") }
        assertThrows<FireflyException> { RelationAction.create().successNotificationTitle(" ") }
        assertThrows<FireflyException> { RelationAction.attach().recordSelectOptionsLimit(0) }
        val context = RelationContext(RelationManager("tasks"), p1, "plain", RelationOperation.VIEW, "edit", false)
        assertNull(context.get("name"))
        assertEquals("Kona", RelationContext(RelationManager("tasks"), p2, p1, RelationOperation.VIEW, "edit", true).get("name"))
        assertEquals("x", RelationContext(RelationManager("tasks"), p1, mapOf("name" to "x"), RelationOperation.VIEW, "edit", true).get("name"))
        assertThrows<FireflyException> {
            RelationManager("tasks").perform(RelationManager("tasks").bind(p1, null, "relations", ""), RelationOperation.VIEW, emptyMap())
        }
    }

    @Test
    fun routeGuardsMissingParametersAndTenantEdges() {
        lastRelation = "tasks"
        val guarded = Router()
        Panel().path("admin").resource(projects(tasks())).mount(guarded)
        assertEquals(302, relationPost(guarded, "create", p1, "name=X").status, "unauthenticated → login")
        assertEquals(302, optionsResponse(guarded, p1, "associate", "").status)
        val hosted = Router()
        Panel().path("admin").domain("ops.example").authenticate(false).resource(projects(tasks())).mount(hosted)
        assertEquals(404, relationPost(hosted, "create", p1, "name=X").status, "wrong host")
        assertEquals(404, optionsResponse(hosted, p1, "associate", "").status)

        val router = mount(projects(tasks()))
        assertEquals(422, post(router, "create", emptyMap(), "").status, "no page / relation / owner")
        assertEquals(404, optionsResponse(router, p1, "associate", "", relation = "nope").status)
        assertEquals(404, post(router, "create", mapOf("page" to "edit"), "").status, "no relation")
        assertEquals(404, post(router, "create", mapOf("page" to "edit", "relation" to "tasks"), "").status, "no owner")

        // Signed-in panel users reach the relation routes.
        val signed = Router()
        Panel().id("relsigned${System.nanoTime()}").path("admin").resource(projects(tasks())).mount(signed)
        val register = signed.find("POST", "/admin/register")!!.route.action
        register.handle(Request("POST", "/admin/register", emptyMap(), emptyMap(), "name=Ada&email=ada@example.com&password=secret"))
        signed.find("POST", "/admin/login")!!.route.action
            .handle(Request("POST", "/admin/login", emptyMap(), emptyMap(), "email=ada@example.com&password=secret"))
        assertEquals(302, relationPost(signed, "create", p1, "name=Signed").status)
        assertNotNull(RmTask().newQuery().where("name", "Signed").first())
        assertEquals(200, optionsResponse(signed, p1, "associate", "").status)
        val bare = router.find("GET", "/admin/projects/relations/options")!!.route.action.handle(
            Request("GET", "/admin/projects/relations/options", emptyMap(), mapOf("relation" to "tasks", "owner" to p1.getKey().toString(), "page" to "edit"), ""),
        )
        assertEquals(422, bare.status, "no op")
        val noQuery = router.find("GET", "/admin/projects/relations/options")!!.route.action.handle(
            Request("GET", "/admin/projects/relations/options", emptyMap(), mapOf("relation" to "tasks", "owner" to p1.getKey().toString(), "page" to "edit", "op" to "associate"), ""),
        )
        assertEquals(200, noQuery.status)
        val alpha = task(p1, "Alpha")
        assertEquals(422, relationPost(router, "bulk", p1, "_bulk_action=dissociate").status, "no keys selected")
        assertEquals(302, relationPost(router, "associate", p1, "").status, "no recordId")
        assertNotNull(reload(alpha).getAttribute("project_id"))

        val p3 = RmProject().fill(mapOf("name" to "Teamless")).also { it.save() }
        val tenantRouter = Router()
        Panel().path("admin").authenticate(false).tenant(Tenant("a")).resource(projects(tasks()).tenant("team")).mount(tenantRouter)
        assertEquals(404, post(tenantRouter, "create", mapOf("relation" to "tasks", "owner" to p3.getKey().toString(), "page" to "edit", "tenant" to "a"), "name=X").status)

        // Owner whose local key is null has no related rows.
        task(p3, "Orphan")
        val teamRouter = mount(projects(RelationManager("teamTasks").recordTitleAttribute("name")))
        val teamless = page(teamRouter, "edit", p3)
        assertFalse(teamless.substringAfter("<tbody", "").contains(">Orphan<"))
        assertEquals("abc", RelationBinding.coerce("abc"))
        assertEquals(7L, RelationBinding.coerce("7"))
    }

    @Test
    fun directPageRenderNoModelOwnerAndTabBadges() {
        task(p1, "Alpha")
        val resource = projects(tasks().badge("3"), members())
        val edit = resource.pages().first { it is EditPage }.render(mapOf("record" to p1.getKey().toString()))
        assertTrue(edit.contains("""action="relations/create?relation=tasks"""), "relative base without the panel")
        assertTrue(edit.contains("""Tasks<span class="ff-relation-badge">3</span></a>"""), edit)
        assertTrue(edit.contains("""data-ff-relation-tab="members" aria-selected="false""""))
        val noModel = object : Resource("loose") {
            init {
                relation(RelationManager("tasks"))
            }
        }
        assertFalse(noModel.pages().first { it is EditPage }.render(mapOf("record" to "1")).contains("data-ff-relation="))
    }

    @Test
    fun softDeletingManyToManyKeepsLinksUntilForceDelete() {
        val red = RmTag().fill(mapOf("name" to "red")).also { it.save() }
        p1.tags().attach(listOf(red.getKey()))
        lastRelation = "tags"
        val router = mount(projects(RelationManager("tags").recordTitleAttribute("name").form(Schema.make().components(TextInput.make("name")))))
        relationPost(router, "delete", p1, "id=${red.getKey()}")
        assertEquals(1L, org.konaframework.cadence.DB.table("rm_taggables").where("tag_id", red.getKey()).count(), "soft delete keeps the link")
        relationPost(router, "restore", p1, "id=${red.getKey()}")
        assertEquals(setOf("red"), p1.tags().get().map { it.getAttribute("name") }.toSet())
        relationPost(router, "delete", p1, "id=${red.getKey()}")
        relationPost(router, "force-delete", p1, "id=${red.getKey()}")
        assertEquals(0L, org.konaframework.cadence.DB.table("rm_taggables").where("tag_id", red.getKey()).count())
        assertNull(RmTag().newQuery().withTrashed().where("id", red.getKey()).first())
    }

    @Test
    fun bulkFailureMessagesCustomFormsAndPreloadLimit() {
        val a = task(p1, "A")
        val b = task(p1, "B")
        val router = mount(projects(tasks().canDelete { context -> context.get("name") != "B" && context.get("name") != "A" }))
        val session = RmSession()
        relationPost(router, "bulk", p1, "_bulk_action=restore&_bulk_keys=${a.getKey()}", session = session)
        assertToast(session, "Nothing was restored", "danger", "One selected record is not deleted.")
        relationPost(router, "bulk", p1, "_bulk_action=restore&_bulk_keys=${a.getKey()},${b.getKey()}", session = session)
        assertToast(session, "Nothing was restored", "danger", "2 selected records are not deleted.")
        relationPost(router, "bulk", p1, "_bulk_action=delete&_bulk_keys=${a.getKey()}", session = session)
        assertToast(session, "Nothing was deleted", "danger", "One selected record was not authorized.")
        relationPost(router, "bulk", p1, "_bulk_action=delete&_bulk_keys=${a.getKey()},${b.getKey()}", session = session)
        assertToast(session, "Nothing was deleted", "danger", "2 selected records were not authorized.")
        relationPost(router, "bulk", p1, "_bulk_action=dissociate&_bulk_keys=99998", session = session)
        assertToast(session, "Nothing was dissociated", "danger", "One selected record is no longer related.")
        relationPost(router, "bulk", p1, "_bulk_action=dissociate&_bulk_keys=99998,99999", session = session)
        assertToast(session, "Nothing was dissociated", "danger", "2 selected records are no longer related.")

        // Action-level forms, an unnamed field, a laid-out manager form, row-only View.
        val loose1 = task(null, "L1")
        val loose2 = task(null, "L2")
        val nameless = task(null, null)
        val custom = RelationManager("tasks").recordTitleAttribute("name")
            .form(Schema.make().columns(2).components(TextInput.make("name"), TextInput.make("")))
            .headerActions(
                RelationAction.create().form { schema -> schema.components(TextInput.make("name").required()) },
                RelationAction.associate().recordSelectOptionsLimit(1).recordSelectOptionsQuery { _, _ -> },
            )
            .actions(RelationAction.view(), RelationAction.edit().form { schema -> schema.components(TextInput.make("name")) })
            .canEdit { context -> context.record !is Cadence<*> || context.get("name") != "Frozen" }
            .canAttach { false }
        val customRouter = mount(projects(custom))
        val edit = page(customRouter, "edit", p1)
        assertTrue(edit.contains("ff-grid") || edit.contains("data-ff-columns") || edit.contains("columns"), "manager form layout survives templating")
        val readOnly = page(customRouter, "view", p1)
        assertFalse(readOnly.contains("""data-ff-dialog-switch="edit""""))
        assertTrue(options(customRouter, p1, "associate", "").contains("\"label\":\"${nameless.getKey()}\""), "untitled records fall back to the key")
        relationPost(customRouter, "associate", p1, "recordId=${loose1.getKey()},${loose2.getKey()}")
        assertEquals(p1.getKey().toString(), reload(loose2).getAttribute("project_id").toString(), "past the preload limit still associates")
        assertEquals("[]", options(customRouter, p1, "attach", ""))
        relationPost(customRouter, "edit", p1, "id=${a.getKey()}&name=Frozen")
        assertEquals(403, relationPost(customRouter, "edit", p1, "id=${a.getKey()}&name=Thawed").status)
        val noEdit = mount(projects(tasks().actions(RelationAction.view())))
        assertFalse(page(noEdit, "edit", p1).contains("""data-ff-dialog-switch="edit""""))

        // Header actions that are not header operations or not supported here are skipped.
        val odd = mount(
            projects(
                tasks().headerActions(
                    RelationAction.view(),
                    RelationAction.attach(),
                    RelationAction.associate().hidden { false }.visible { true },
                    RelationAction.create().visible { false },
                ),
            ),
        )
        val oddEdit = page(odd, "edit", p1)
        assertFalse(oddEdit.contains(">Attach<"))
        assertTrue(oddEdit.contains(">Associate<"))
        assertFalse(oddEdit.contains(">New task<"))
        val noAssociate = mount(projects(tasks().canAssociate { false }))
        assertEquals("[]", options(noAssociate, p1, "associate", ""))
        // Pivot columns on a has-many manager stay model attributes.
        val pivoted = mount(projects(tasks().pivotColumns("name").form(Schema.make().components(TextInput.make("name"), TextInput.make("")))))
        relationPost(pivoted, "create", p1, "name=Kept")
        assertNotNull(RmTask().newQuery().where("name", "Kept").first())

        // Shared table rendered outside a relation request is not scoped.
        val shared = Table.forModel(RmTask::class.java).columns(TextColumn.make("name"))
        page(mount(projects(RelationManager("tasks").recordTitleAttribute("name").table(shared))), "edit", p1)
        assertTrue(shared.render().contains(">L1<") || shared.render().contains("Gamma") || shared.render().contains("ff-table"))
        assertTrue(RelationManager("tasks").infolist(Schema.make().components(TextEntry.make("name"))).name() == "tasks")
        assertThrows<FireflyException> {
            RelationManager("tasks").perform(RelationManager("tasks").bind(p1, null, "relations", ""), RelationOperation.BULK, emptyMap())
        }
        val row = RowRecord(org.konaframework.firefly.tables.Cell { path -> if (path == "key") "5" else "v" })
        assertEquals("5", row.key())
        assertEquals("v", RelationContext(RelationManager("tasks"), p1, row, RelationOperation.VIEW, "edit", false).get("name"))
    }

    // ——— Fixtures ———

    private fun tasks(): RelationManager = RelationManager("tasks")
        .recordTitleAttribute("name")
        .form(Schema.make().components(TextInput.make("name").required()))
        .table { table -> table.columns(TextColumn.make("name")) }

    private fun members(): RelationManager = RelationManager("members")
        .recordTitleAttribute("name")
        .pivotColumns("role")
        .form(Schema.make().components(TextInput.make("name").required(), TextInput.make("role")))
        .table { table -> table.columns(TextColumn.make("name"), TextColumn.make("role")) }
        .headerActions(RelationAction.create(), RelationAction.attach().form { schema -> schema.components(TextInput.make("role")) })

    private fun projects(vararg managers: RelationManager): Resource = object : Resource("projects") {
        init {
            label("Project")
            model(RmProject::class.java)
            for (manager in managers) relation(manager)
        }

        override fun form(schema: Schema): Schema = schema.components(TextInput.make("name"))

        override fun infolist(schema: Schema): Schema = schema.components(TextEntry.make("name"))
    }

    private fun mount(resource: Resource): Router {
        val router = Router()
        Panel().path("admin").authenticate(false).resource(resource).mount(router)
        return router
    }

    private fun task(project: RmProject?, name: String?): RmTask =
        RmTask().fill(mapOf("project_id" to project?.getKey(), "name" to name)).also { it.save() }

    private fun user(name: String): RmUser = RmUser().fill(mapOf("name" to name)).also { it.save() }

    private fun reload(task: RmTask): RmTask = RmTask().newQuery().withTrashed().where("id", task.getKey()).first()!!

    private fun reloadUser(user: RmUser): RmUser = RmUser().newQuery().where("id", user.getKey()).first()!!

    private fun pivotRole(project: RmProject, user: RmUser): Any? =
        org.konaframework.cadence.DB.table("rm_project_user")
            .where("project_id", project.getKey()).where("user_id", user.getKey()).first()?.get("role")

    private fun page(router: Router, slug: String, owner: RmProject): String =
        get(router, "/admin/projects/$slug", mapOf("id" to owner.getKey().toString()))

    private fun get(router: Router, path: String, query: Map<String, String>): String {
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        val response = match.route.action.handle(Request("GET", path, emptyMap(), query, ""))
        assertEquals(200, response.status, response.body)
        return response.body
    }

    private fun relationPost(
        router: Router,
        operation: String,
        owner: RmProject,
        body: String,
        relation: String = lastRelation,
        page: String = "edit",
        session: RequestSession? = null,
    ): Response = post(router, operation, mapOf("relation" to relation, "owner" to owner.getKey().toString(), "page" to page), body, session)

    /** Relation posted when a call does not name one. */
    private var lastRelation = "tasks"

    private fun post(router: Router, operation: String, query: Map<String, String>, body: String, session: RequestSession? = null): Response {
        val path = "/admin/projects/relations/$operation"
        val match = router.find("POST", path) ?: throw AssertionError("missing $path")
        val encoded = body.split('&').filter { it.isNotEmpty() }.joinToString("&") { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        val request = Request("POST", path, mapOf("Content-Type" to "application/x-www-form-urlencoded"), query, encoded)
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    private fun optionsResponse(router: Router, owner: RmProject, op: String, q: String, page: String = "edit", relation: String = lastRelation): Response {
        val path = "/admin/projects/relations/options"
        val match = router.find("GET", path) ?: throw AssertionError("missing $path")
        val query = mapOf("relation" to relation, "owner" to owner.getKey().toString(), "page" to page, "op" to op, "q" to q)
        return match.route.action.handle(Request("GET", path, emptyMap(), query, ""))
    }

    private fun options(router: Router, owner: RmProject, op: String, q: String, page: String = "edit", relation: String = lastRelation): String {
        val response = optionsResponse(router, owner, op, q, page, relation)
        assertEquals(200, response.status, response.body)
        return response.body
    }

    private fun assertToast(session: RmSession, title: String, status: String, body: String? = null) {
        val flash = session.get(FireflyNotification.FLASH_KEY) as? List<*> ?: throw AssertionError("no toast flashed")
        val last = flash.last() as Map<*, *>
        assertEquals(title, last["title"], flash.toString())
        assertEquals(status, last["status"], flash.toString())
        if (body != null) assertEquals(body, last["body"], flash.toString())
        session.put(FireflyNotification.FLASH_KEY, null)
    }
}

class RmSession : RequestSession {
    private val values = LinkedHashMap<String, Any?>()
    override fun get(key: String): Any? = values[key]
    override fun put(key: String, value: Any?): RequestSession = apply { values[key] = value }
    override fun flash(key: String, value: Any?): RequestSession = put(key, value)
    override fun flashInput(input: Map<String, String>): RequestSession = this
    override fun old(key: String, default: String?): String? = default
    override fun oldInput(): Map<String, String> = emptyMap()
    override fun previousUrl(): String? = null
    override fun setPreviousUrl(url: String): RequestSession = this
}

class RmProject : Cadence<RmProject>() {
    override val table = "rm_projects"
    override val timestamps = false
    override val fillable = setOf("name", "team")

    fun tasks() = hasMany(RmTask::class.java, "project_id")

    fun members() = belongsToMany(RmUser::class.java, "rm_project_user", "project_id", "user_id")

    fun comments() = morphMany(RmComment::class.java, "commentable")

    fun tags() = morphToMany(RmTag::class.java, "taggable", "rm_taggables", "taggable_id", "tag_id")

    fun checks() = hasManyThrough(RmCheck::class.java, RmMilestone::class.java, "project_id", "milestone_id")

    fun lead() = belongsTo(RmUser::class.java, "lead_id")

    fun teamTasks() = hasMany(RmTask::class.java, "project_id", "team")
}

class RmTask : Cadence<RmTask>(), SoftDeletes {
    override val table = "rm_tasks"
    override val timestamps = false
    override val fillable = setOf("project_id", "name")
}

class RmUser : Cadence<RmUser>() {
    override val table = "rm_users"
    override val timestamps = false
    override val fillable = setOf("name")
}

class RmComment : Cadence<RmComment>() {
    override val table = "rm_comments"
    override val timestamps = false
    override val fillable = setOf("commentable_type", "commentable_id", "body")
}

class RmTag : Cadence<RmTag>(), SoftDeletes {
    override val table = "rm_tags"
    override val timestamps = false
    override val fillable = setOf("name")
}

class RmMilestone : Cadence<RmMilestone>() {
    override val table = "rm_milestones"
    override val timestamps = false
    override val fillable = setOf("project_id", "name")
}

class RmCheck : Cadence<RmCheck>() {
    override val table = "rm_checks"
    override val timestamps = false
    override val fillable = setOf("milestone_id", "name")
}
