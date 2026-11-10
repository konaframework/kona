package org.konaframework.firefly.imports

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.EncryptionMode
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.firefly.MemoryPanelGuard
import org.konaframework.firefly.Panel
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.time.LocalDate

class ImportActionTest {
    @Test
    fun columnsAndTheActionLink() {
        assertThrows<FireflyException> { ImportColumn.make("9bad") }
        assertThrows<FireflyException> { ImportColumn.make("name").label(" ") }
        val column = ImportColumn.make("name").label("Name").requiredMapping().aliases("title", " ", "name")
        assertEquals("name", column.name)
        assertEquals("Name", column.label)
        assertTrue(column.requiredMapping)
        assertEquals(listOf("title", "name"), column.aliases)
        assertFalse(column.requiredMapping(false).requiredMapping)
        assertTrue(column.aliases().aliases.isEmpty())
        assertThrows<FireflyException> { ImportAction.make("bad-name") }

        assertThrows<IllegalStateException> {
            ImportContext.use("/admin/import") { throw IllegalStateException("boom") }
        }
        assertThrows<IllegalStateException> {
            ImportContext.use("/outer") {
                ImportContext.use("/inner") { throw IllegalStateException("boom") }
            }
        }
        assertNull(ImportContext.pagePath())

        ImportPaths.base = null
        assertEquals("/import?importer=items", ImportLinks.href("items"))
        ImportPaths.remember("/desk/import")
        assertEquals("/desk/import?importer=items", ImportLinks.href("items"))
        val nested = ImportContext.use("/admin/import") {
            ImportContext.use("/tenant/import") { ImportLinks.href("items") }
        }
        assertEquals("/tenant/import?importer=items", nested)
        assertEquals("/desk/import?importer=items", ImportLinks.href("items"))

        val html = ImportContext.use("/admin/import") {
            val action = ImportAction.make().importer(sheetImporter()).label("Load")
            Schema.make().components(action).render()
        }
        assertTrue(html.contains("/admin/import?importer=items"))
        assertTrue(html.contains("Load"))
        assertEquals("named", ImportAction.make("named").name)
    }

    @Test
    fun registrationRejectsABlankOrRepeatedImporter() {
        val panel = Panel()
        assertThrows<FireflyException> { panel.imports(sheetImporter(), sheetImporter()) }
        assertThrows<FireflyException> { panel.imports(sheetImporter(uploadText = " ")) }
        assertThrows<FireflyException> { panel.importMaxBytes(0) }
        panel.importMaxBytes(12)
        panel.imports(sheetImporter(key = "other", title = "Other"))
        panel.imports(sheetImporter())
        assertThrows<FireflyException> {
            panel.imports(
                sheetImporter(
                    columns = listOf(
                        ImportColumn.make("name").label("Name").requiredMapping(),
                        ImportColumn.make("name").label("Again"),
                    ),
                ),
            )
        }
    }

    @Test
    fun csvSuggestsOnlyTheseColumnsAndKeepsLineNumbers() {
        val csv = ImportCsv.parse("route,connection,customer,\"meter, no\"\nMAIN,1001,\"Amina, Otieno\",M-1\n")
        assertEquals(listOf("route", "connection", "customer", "meter, no"), csv.headers)
        assertEquals("Amina, Otieno", csv.rows.single().cells[2])
        assertEquals(2, csv.rows.single().line)

        val semi = ImportCsv.parse("connection;customer name;meter number\n1001;Amina;M-1\n")
        assertEquals("Amina", semi.rows.single().cells[1])
        val fields = listOf(
            ImportColumn.make("connection").label("Connection").requiredMapping().aliases("connection"),
            ImportColumn.make("name").label("Name").requiredMapping().aliases("customer", "customer name"),
            ImportColumn.make("sku").label("Sku").aliases("title", "name"),
        )
        assertEquals(1, ImportCsv.suggest(semi.headers, fields)["name"])
        assertEquals(setOf("connection", "name"), ImportCsv.suggest(semi.headers, fields).keys)

        val taken = ImportCsv.suggest(listOf("name", "customer"), fields)
        assertEquals(0, taken["name"])
        assertNull(taken["sku"])
        assertEquals(0, ImportCsv.suggest(listOf("title"), fields)["sku"])
        assertNull(ImportCsv.suggest(listOf("title"), fields)["name"])

        val tabs = ImportCsv.parse("a\tb\n1\t2")
        assertEquals(listOf("1", "2"), tabs.rows.single().cells)
        val quoted = ImportCsv.parse("a,b\n\"hello\n\"\"world\"\"\",x\n")
        assertEquals("hello\n\"world\"", quoted.rows.single().cells[0])
        assertEquals(2, quoted.rows.single().line)
        assertEquals("a\"b", ImportCsv.parse("h\na\"b\n").rows.single().cells.single())
        assertEquals(listOf("A", "A (2)"), ImportCsv.parse("A,A\n1,2\n").headers)
        assertEquals(listOf("Column 1", "A"), ImportCsv.parse(",A\n1,2\n").headers)
        assertEquals(listOf("1", ""), ImportCsv.parse("a,b\n1\n").rows.single().cells)
        assertTrue(ImportCsv.parse("   ").headers.isEmpty())
        assertTrue(ImportCsv.parse("\uFEFFa\r\n1\r\n").rows.single().cells.single() == "1")
        assertEquals("route code", ImportCsv.normalize("  Route_code   "))
    }

    @Test
    fun mappingDropsAnUnmappedOptionalAndBlocksARequiredHole() {
        val columns = listOf(
            ImportColumn.make("name").label("Name").requiredMapping(),
            ImportColumn.make("note").label("Note"),
        )
        val sheet = ImportCsv.parse("Name,Note\nAda,hi\n")
        assertNull(ImportMapping.issues(columns, mapOf("name" to 0, "note" to 1), sheet))
        val rows = ImportMapping.rows(columns, mapOf("name" to 0, "note" to 9), sheet)
        assertEquals("Ada", rows.single().values["name"])
        assertFalse(rows.single().values.containsKey("note"))
        val blocked = ImportMapping.issues(columns, mapOf("note" to 1), sheet)
        assertEquals("Match a column to Name.", blocked?.single()?.message)
        assertEquals(
            "The file needs a header row.",
            ImportMapping.issues(columns, emptyMap(), ImportSheet(emptyList(), emptyList()))?.single()?.message,
        )
        val empty = ImportMapping.issues(columns, mapOf("name" to 0), ImportCsv.parse("Name\n"))
        assertTrue(empty!!.any { it.message == "The file has no data rows." })
        val extra = ImportMapping.issues(columns, mapOf("name" to 0), sheet, listOf(ImportProblem(1, "Extra.")))
        assertEquals("Extra.", extra?.single()?.message)
        assertEquals(mapOf("name" to null), ImportMapping.clean(mapOf("name" to 4), 1))
    }

    @Test
    fun sessionRoundTripAcceptsJsonShapesAndRejectsJunk() {
        val sheet = ImportCsv.parse("a,b\n1,2\n")
        val stored = sheet.toSession()
        val restored = ImportSheet.fromSession(stored)
        assertEquals(2, restored?.rows?.single()?.line)
        assertEquals("1", restored?.rows?.single()?.cells?.first())
        assertEquals("1", ImportSheet.fromSession("a\n1\n")?.rows?.single()?.cells?.single())
        assertNull(ImportSheet.fromSession("   "))
        assertNull(ImportSheet.fromSession(4))
        assertNull(ImportSheet.fromSession(mapOf("headers" to emptyList<String>())))
        assertNull(ImportSheet.fromSession(mapOf("headers" to "a")))
        assertNull(ImportSheet.fromSession(mapOf("headers" to listOf("a"))))
        val skipped = ImportSheet.fromSession(
            mapOf(
                "headers" to listOf("a"),
                "rows" to listOf(
                    "nope",
                    mapOf("line" to "x", "cells" to listOf("1")),
                    mapOf("line" to 2, "cells" to "nope"),
                    mapOf("line" to 3, "cells" to listOf("9")),
                ),
            ),
        )
        assertEquals(3, skipped?.rows?.single()?.line)
        assertEquals("9", skipped?.rows?.single()?.cells?.single())
    }

    @Test
    fun excelReadsDisplayedValuesFormulasAndRefusesPasswords() {
        assertFalse(Excel.looksLikeWorkbook(ByteArray(4)))
        assertFalse(Excel.looksLikeWorkbook("connection,customer\n".toByteArray()))
        assertTrue(Excel.looksLikeWorkbook(byteArrayOf(0x50, 0x4B, 1, 2, 3, 4, 5, 6)))
        assertTrue(Excel.looksLikeWorkbook(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0)))
        assertEquals("The file is empty.", assertThrows<IllegalArgumentException> { Excel.read(ByteArray(0)) }.message)
        assertEquals(
            "This spreadsheet could not be read.",
            assertThrows<IllegalArgumentException> { Excel.read(byteArrayOf(0x50, 0x4B, 1, 2, 3, 4, 5, 6)) }.message,
        )
        assertEquals(
            "This spreadsheet is password protected.",
            assertThrows<IllegalArgumentException> { Excel.read(encrypted()) }.message,
        )

        val empty = workbookBytes(XSSFWorkbook()) { }
        assertEquals("", Excel.read(empty).name)
        val blank = workbookBytes(XSSFWorkbook()) { book ->
            book.createSheet("Blank").createRow(0)
        }
        assertTrue(Excel.read(blank).headers.isEmpty())
        assertTrue(Excel.read(blank).name.isNotBlank())

        val bytes = workbookBytes(XSSFWorkbook()) { book ->
            book.createSheet("Empty")
            val sheet = book.createSheet("Routes")
            val header = sheet.createRow(0)
            listOf("Account", "Customer", "Reading", "Customer").forEachIndexed { index, value ->
                header.createCell(index).setCellValue(value)
            }
            sheet.createRow(1)
            val row = sheet.createRow(2)
            row.createCell(0).setCellValue(1001.0)
            row.createCell(1).setCellValue("01001")
            row.createCell(2).cellFormula = "A3+5"
            val dated = sheet.createRow(4)
            val date = dated.createCell(0)
            date.setCellValue(LocalDate.of(2026, 11, 1))
            val style = book.createCellStyle()
            style.dataFormat = book.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
            date.cellStyle = style
            dated.createCell(1).setCellValue("Amina")
        }
        val sheet = Excel.read(bytes)
        assertEquals("Routes", sheet.name)
        assertEquals(2, sheet.sheetCount)
        assertEquals(listOf("Account", "Customer", "Reading", "Customer (2)"), sheet.headers)
        assertEquals(3, sheet.rows[0].number)
        assertEquals(listOf("1001", "01001", "1006", ""), sheet.rows[0].cells)
        assertEquals("2026-11-01", sheet.rows[1].cells[0])
        val restored = ImportSheet.fromSession(sheet.toImportSheet().toSession())
        assertEquals("1006", restored?.rows?.first()?.cells?.get(2))

        val xls = workbookBytes(HSSFWorkbook()) { book ->
            while (book.numberOfSheets > 0) book.removeSheetAt(0)
            val rows = book.createSheet("Meters")
            rows.createRow(0).createCell(0).setCellValue("Meter")
            rows.createRow(3).createCell(0).setCellValue("M-1001")
        }
        val meters = Excel.read(xls)
        assertEquals("Meters", meters.name)
        assertEquals(4, meters.rows.single().number)
        assertEquals("M-1001", meters.rows.single().cells.single())

        assertEquals("import", importFileLabel("", "", 1))
        assertEquals("import · Routes", importFileLabel("", "Routes", 2))
        assertEquals("book", importFileLabel("book", "", 2))
        assertEquals("book", importFileLabel("book", "Routes", 1))
        assertEquals("book · Routes", importFileLabel("book", "Routes", 2))
        assertEquals("That file is larger than 2 MB.", importTooLarge(2_000_000))
        assertEquals("That file is larger than 8 bytes.", importTooLarge(8))
        val refused = assertThrows<IllegalArgumentException> { openImport("a,b\n1,2\n".toByteArray(), "xlsx") }
        assertEquals("This spreadsheet could not be read.", refused.message)
        assertEquals("1", openImport("a,b\n1,2\n".toByteArray(), "csv").sheet.rows.single().cells[0])
        assertEquals(1, openImport(xls, "xls").sheetCount)
    }

    @Test
    fun thePageMatchesOnlyTheImporterAndTestDoesNotWrite() {
        val items = sheetImporter()
        val other = sheetImporter(key = "other", title = "Import other", listLabel = "Other", uploadText = "Upload other.")
        val router = Router()
        Panel().path("admin").authenticate(false).importMaxBytes(8).imports(other).imports(items, other).mount(router)
        assertNull(Router().let { bare ->
            Panel().path("admin").authenticate(false).mount(bare)
            bare.find("GET", "/admin/import")
        })

        val home = send(router, "GET", "/admin/import")
        assertEquals(200, home.status)
        assertTrue(home.body.contains("Import items"))
        assertTrue(home.body.contains("Import other"))
        assertTrue(home.body.contains("choose one here"))
        assertFalse(home.body.contains("name=\"map_name\""))

        val guest = Router()
        Panel().path("admin").imports(items).mount(guest)
        val locked = send(guest, "GET", "/admin/import")
        assertEquals(302, locked.status)
        assertEquals("/admin/login", locked.headers["Location"])

        val guard = MemoryPanelGuard()
        guard.register("Ada", "ada@example.test", "secret")
        assertTrue(guard.attempt("ada@example.test", "secret"))
        val signed = Router()
        Panel().path("admin").authenticate(true).guard(guard).imports(items).mount(signed)
        assertEquals(200, send(signed, "GET", "/admin/import", query = mapOf("importer" to "items")).status)

        val hosted = Router()
        Panel().path("admin").domain("billing.test").authenticate(false).imports(items).mount(hosted)
        val wrongHost = send(hosted, "GET", "/admin/import")
        assertEquals(404, wrongHost.status)
        assertTrue(wrongHost.body.contains("billing.test"))
        assertEquals(200, send(hosted, "GET", "/admin/import", headers = mapOf("Host" to "billing.test")).status)

        val session = MemSession()
        val opened = send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = session)
        assertTrue(opened.body.contains("Upload items."))
        assertTrue(opened.body.contains("Download a sample file"))
        assertTrue(opened.body.contains(".csv,.txt,.xls,.xlsx"))
        assertTrue(opened.body.contains("Select a column") || opened.body.contains("Upload and match fields"))

        val token = session.get("_token") as String
        val cancelForm = form(mapOf("_token" to token, "importer" to "items"))
        val missing = send(
            router,
            "POST",
            "/admin/import/cancel",
            session = session,
            body = cancelForm.second,
            headers = cancelForm.first,
        )
        assertEquals(302, missing.status)
        assertEquals("/admin/import?importer=items", missing.headers["Location"])

        session.put("_token", "t")
        val csrf = send(router, "POST", "/admin/import", session = session, body = "importer=items".toByteArray())
        assertEquals(419, csrf.status)

        val big = multipart(mapOf("importer" to "items", "_token" to "t"), "rows.csv", "name\nAda\n".toByteArray())
        val tooBig = send(router, "POST", "/admin/import", session = session, body = big.second, headers = big.first)
        assertEquals(302, tooBig.status)
        val tooBigPage = send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = session)
        assertTrue(tooBigPage.body.contains("That file is larger than 8 bytes."))

        panelWithRoom(items, other).let { (room, wide) ->
            val up = multipart(mapOf("importer" to "items", "_token" to "t"), "rows.csv", "Name,Phone\nAda,555\n".toByteArray())
            wide.put("_token", "t")
            val uploaded = send(room, "POST", "/admin/import", session = wide, body = up.second, headers = up.first)
            assertEquals("/admin/import?importer=items", uploaded.headers["Location"])
            val match = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(match.body.contains("name=\"map_name\""))
            assertTrue(match.body.contains("name=\"map_note\""))
            assertFalse(match.body.contains("name=\"map_phone\""))
            assertTrue(match.body.contains("Phone"))
            assertTrue(match.body.contains("Select a column"))
            assertTrue(match.body.contains("Don't import"))
            assertTrue(match.body.contains("option value=\"0\" selected"))
            assertTrue(match.body.contains(">Test<"))
            assertTrue(match.body.contains(">Import<"))
            assertEquals(0, items.applied)

            val tested = form(mapOf("importer" to "items", "_token" to "t", "map_name" to "0", "map_note" to "", "commit" to "0"))
            send(room, "POST", "/admin/import/run", session = wide, body = tested.second, headers = tested.first)
            assertEquals(0, items.applied)
            assertTrue(items.resolved)
            val report = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(report.body.contains("1 new item."))
            assertTrue(report.body.contains("Don't import"))

            items.resolved = false
            val blocked = form(mapOf("importer" to "items", "_token" to "t", "map_name" to "", "commit" to "1"))
            send(room, "POST", "/admin/import/run", session = wide, body = blocked.second, headers = blocked.first)
            assertFalse(items.resolved)
            assertEquals(0, items.applied)
            val blockedPage = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(blockedPage.body.contains("Match a column to Name."))

            val bad = sheetImporter(key = "bad", outcome = { rows ->
                SimpleResolution(0, 0, listOf(ImportProblem(rows.single().line, "Name is empty.")), "The file still has 1 problem.")
            })
            val badRouter = Router()
            val badSession = MemSession().also { it.put("_token", "t") }
            Panel().path("admin").authenticate(false).imports(bad).mount(badRouter)
            val badFile = multipart(mapOf("importer" to "bad", "_token" to "t"), "rows.csv", "Name\n\nAda\n".toByteArray())
            send(badRouter, "POST", "/admin/import", session = badSession, body = badFile.second, headers = badFile.first)
            val badRun = form(mapOf("importer" to "bad", "_token" to "t", "map_name" to "0", "commit" to "1"))
            send(badRouter, "POST", "/admin/import/run", session = badSession, body = badRun.second, headers = badRun.first)
            assertEquals(0, bad.applied)
            val badPage = send(badRouter, "GET", "/admin/import", query = mapOf("importer" to "bad"), session = badSession)
            assertTrue(badPage.body.contains("Name is empty."))

            val boom = sheetImporter(key = "boom", outcome = { SimpleResolution(1, 0, emptyList(), "1 new item.") })
            boom.explode = true
            val boomRouter = Router()
            val boomSession = MemSession().also { it.put("_token", "t") }
            Panel().path("admin").authenticate(false).imports(boom).mount(boomRouter)
            val boomFile = multipart(mapOf("importer" to "boom", "_token" to "t"), "", "Name\nAda\n".toByteArray())
            send(boomRouter, "POST", "/admin/import", session = boomSession, body = boomFile.second, headers = boomFile.first)
            val boomRun = form(mapOf("importer" to "boom", "_token" to "t", "map_name" to "0", "commit" to "1"))
            send(boomRouter, "POST", "/admin/import/run", session = boomSession, body = boomRun.second, headers = boomRun.first)
            val boomPage = send(boomRouter, "GET", "/admin/import", query = mapOf("importer" to "boom"), session = boomSession)
            assertTrue(boomPage.body.contains("The import did not finish."))

            items.resolved = false
            val ran = form(mapOf("importer" to "items", "_token" to "t", "map_name" to "0", "map_note" to "", "commit" to "1"))
            send(room, "POST", "/admin/import/run", session = wide, body = ran.second, headers = ran.first)
            assertEquals(1, items.applied)
            val done = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(done.body.contains("1 new item."))
            assertTrue(done.body.contains("Upload and match fields"))
            assertTrue(done.body.contains("Items imported"))

            val again = send(room, "GET", "/admin/import", query = mapOf("importer" to "other"), session = wide)
            assertTrue(again.body.contains("Upload other."))
            assertFalse(again.body.contains("1 new item."))

            val back = send(room, "GET", "/admin/import", session = wide)
            assertTrue(back.body.contains("Upload other."))

            val emptyFile = multipart(mapOf("importer" to "items", "_token" to "t"), "empty.csv", ByteArray(0))
            send(room, "POST", "/admin/import", session = wide, body = emptyFile.second, headers = emptyFile.first)
            val emptyPage = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(emptyPage.body.contains("Choose a CSV or Excel file"))

            val blank = multipart(mapOf("importer" to "items", "_token" to "t"), "blank.csv", "   ".toByteArray())
            send(room, "POST", "/admin/import", session = wide, body = blank.second, headers = blank.first)
            val blankPage = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(blankPage.body.contains("header row"))

            val fakeXls = multipart(mapOf("importer" to "items", "_token" to "t"), "bad.xls", "Name\nAda\n".toByteArray())
            send(room, "POST", "/admin/import", session = wide, body = fakeXls.second, headers = fakeXls.first)
            val fakePage = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(fakePage.body.contains("could not be read"))

            val noSession = send(room, "POST", "/admin/import/run", body = ran.second, headers = ran.first)
            assertEquals(500, noSession.status)
            val cancel = send(room, "POST", "/admin/import/cancel")
            assertEquals("/admin/import", cancel.headers["Location"])

            val unknownPart = multipart(mapOf("_token" to "t", "importer" to "missing"), "a.csv", "Name\nA\n".toByteArray())
            val unknown = send(room, "POST", "/admin/import", session = wide, body = unknownPart.second, headers = unknownPart.first)
            assertEquals("/admin/import", unknown.headers["Location"])

            val sample = send(room, "GET", "/admin/import/template", query = mapOf("importer" to "items"))
            assertEquals(200, sample.status)
            assertTrue(sample.body.startsWith("name,note\n"))
            assertTrue(sample.headers["Content-Disposition"]!!.contains("items-import.csv"))
            val noSample = send(room, "GET", "/admin/import/template")
            assertEquals(422, noSample.status)

            val book = workbookBytes(XSSFWorkbook()) { workbook ->
                workbook.createSheet("Empty")
                val sheet = workbook.createSheet("Items")
                sheet.createRow(0).createCell(0).setCellValue("Name")
                sheet.createRow(1).createCell(0).setCellValue("Ada")
            }
            val xlsx = multipart(mapOf("importer" to "items", "_token" to "t"), "items.xlsx", book, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            send(room, "POST", "/admin/import", session = wide, body = xlsx.second, headers = xlsx.first)
            val excelPage = send(room, "GET", "/admin/import", query = mapOf("importer" to "items"), session = wide)
            assertTrue(excelPage.body.contains("items.xlsx · Items"))
        }
    }

    @Test
    fun markupCoversTheReportAndABlankSample() {
        val sheet = ImportSheet(listOf("Name", "Note"), listOf(ImportSheetRow(2, listOf("", ""))))
        val issues = (1..41).map { ImportProblem(it, "Bad $it") }
        val html = importMarkup(
            importers = listOf(sheetImporter()),
            importer = sheetImporter(),
            sheet = sheet,
            fileName = "rows.csv",
            csrf = "",
            error = "",
            done = "Saved.",
            storedMap = mapOf("name" to "1", "note" to ""),
            report = mapOf("summary" to "Many.", "issues" to (issues.map { mapOf("line" to it.line, "message" to it.message) } + "skip")),
            pagePath = "/admin/import",
        )
        assertTrue(html.contains("Saved."))
        assertTrue(html.contains("2 more."))
        assertTrue(html.contains("Many."))
        assertTrue(html.contains("option value=\"1\" selected"))
        val quiet = importMarkup(
            importers = emptyList(),
            importer = sheetImporter(),
            sheet = sheet,
            fileName = "rows.csv",
            csrf = "",
            error = "Nope.",
            done = "Saved.",
            storedMap = null,
            report = mapOf("summary" to "Ok.", "issues" to emptyList<Any>()),
            pagePath = "/admin/import",
        )
        assertTrue(quiet.contains("Nope."))
        assertTrue(quiet.contains("Ok."))
        assertFalse(quiet.contains("<th>Problem</th>"))
    }

    @Test
    fun edgesTheMatcherReaderAndGuards() {
        val panel = Panel()
        for (importer in listOf(
            sheetImporter(key = "bad key"),
            sheetImporter(title = " "),
            sheetImporter(listLabel = " "),
            sheetImporter(listPath = " "),
            sheetImporter(matchText = " "),
            sheetImporter(columns = emptyList()),
        )) {
            assertThrows<FireflyException> { panel.imports(importer) }
        }
        assertEquals("sku\n", PlainImporter().template())
        assertTrue(ImportFailures(listOf(ImportProblem(1, "A"), ImportProblem(2, "B"))).summary().contains("2 problems"))
        assertTrue(ImportFailures(listOf(ImportProblem(1, "A"))).summary().contains("1 problem."))
        assertNull(blankToNull(null))
        assertNull(blankToNull("  "))
        assertEquals("Ada", blankToNull(" Ada "))
        assertNull(textOf(null))
        assertEquals("4", textOf(4))
        assertEquals("", textOrEmpty(null))
        assertEquals("book", textOrEmpty("book"))
        assertNull(columnIndex(null))
        assertNull(columnIndex("  "))
        assertNull(columnIndex("nope"))
        assertEquals(0, columnIndex("0"))
        assertEquals(2, columnIndex(" 2 "))
        assertEquals("fallback", stated(null, "fallback"))
        assertEquals("fallback", stated(" ", "fallback"))
        assertEquals("kept", stated("kept", "fallback"))
        assertEquals("That file is larger than 0 bytes.", importTooLarge(0))

        assertFalse(Excel.looksLikeWorkbook(byteArrayOf(0xD0.toByte(), 1, 2, 3, 4, 5, 6, 7)))
        assertFalse(Excel.looksLikeWorkbook(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 1, 3, 4, 5, 6, 7)))
        assertFalse(Excel.looksLikeWorkbook(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 1, 4, 5, 6, 7)))
        assertFalse(Excel.looksLikeWorkbook(byteArrayOf(0x50, 1, 2, 3, 4, 5, 6, 7)))
        val gap = workbookBytes(XSSFWorkbook()) { book ->
            val sheet = book.createSheet("Gap")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("A")
            header.createCell(2).setCellValue("C")
            val blank = sheet.createRow(1)
            blank.createCell(0).setCellValue(" ")
            blank.createCell(1).setCellValue("")
            sheet.createRow(2).createCell(0).setCellValue("x")
        }
        val gaps = Excel.read(gap)
        assertEquals(listOf("A", "Column 2", "C"), gaps.headers)
        assertEquals(listOf("x", "", ""), gaps.rows.single().cells)

        assertTrue(ImportCsv.parse("\n").headers.isEmpty())
        assertTrue(ImportCsv.parse(",,,").headers.isEmpty())
        assertEquals("", ImportCsv.headerLine("\n\n"))
        assertEquals(" a,b", ImportCsv.headerLine("\n a,b"))
        assertEquals(listOf("1", "2"), ImportCsv.parse("a,b\n,\n1,2\n").rows.single().cells)
        assertEquals("abc", ImportCsv.parse("h\n\"abc\"").rows.single().cells.single())
        assertEquals(listOf("1", ""), ImportCsv.parse("a,b\n1,").rows.single().cells)
        assertEquals(listOf("a\tb", "c", "d"), ImportCsv.parse("a\tb;c;d\n1\t2;3\n").headers)

        val restored = ImportSheet.fromSession(
            mapOf(
                "headers" to listOf(null, "b"),
                "rows" to listOf(mapOf("line" to 2, "cells" to listOf(null, "z"))),
            ),
        )
        assertEquals("", restored?.headers?.first())
        assertEquals("", restored?.rows?.single()?.cells?.first())

        val columns = listOf(
            ImportColumn.make("name").label("Name").requiredMapping(),
            ImportColumn.make("note").label("Note"),
        )
        val short = ImportSheet(listOf("A", "B"), listOf(ImportSheetRow(2, listOf("only"))))
        assertEquals("", ImportMapping.rows(columns, mapOf("name" to 1), short).single().values["name"])
        assertEquals(mapOf("name" to null), ImportMapping.clean(mapOf("name" to null), 2))
        assertEquals(mapOf("name" to 0), ImportMapping.clean(mapOf("name" to 0), 2))
        assertNull(ImportMapping.issues(columns, mapOf("name" to 0), ImportCsv.parse("Name\nAda\n")))

        val longSample = "n".repeat(50)
        val wide = sheetImporter(
            columns = listOf(
                ImportColumn.make("name").label("Name").requiredMapping().aliases("name", "title"),
                ImportColumn.make("note").label("Note"),
                ImportColumn.make("extra").label("Extra"),
            ),
        )
        val preview = importMarkup(
            importers = emptyList(),
            importer = wide,
            sheet = ImportSheet(
                listOf("Name", "Note"),
                listOf(ImportSheetRow(2, listOf(" "))) +
                    (1..8).map { ImportSheetRow(it + 2, listOf(longSample, "note")) },
            ),
            fileName = "rows.csv",
            csrf = "",
            error = "",
            done = "",
            storedMap = mapOf("note" to "1", "extra" to null),
            report = mapOf(
                "issues" to listOf(
                    "nope",
                    mapOf("line" to 2, "message" to "Bad"),
                    mapOf<String, Any?>(),
                    mapOf("line" to null, "message" to null),
                ),
            ),
            pagePath = "/admin/import",
        )
        assertFalse(preview.contains("<p class=\"ni-error\">"))
        assertFalse(preview.contains("<p class=\"ni-done\">"))
        assertTrue(preview.contains("— ${"n".repeat(40)}"))
        assertFalse(preview.contains("— ${"n".repeat(41)}"))
        val uploadAgain = importMarkup(
            importers = listOf(sheetImporter()),
            importer = sheetImporter(),
            sheet = ImportSheet(emptyList(), emptyList()),
            fileName = "",
            csrf = "",
            error = "",
            done = "Saved.",
            storedMap = null,
            report = null,
            pagePath = "/admin/import",
        )
        assertTrue(uploadAgain.contains("Upload and match fields"))
        assertTrue(uploadAgain.contains("Saved."))
        val suggested = importMarkup(
            importers = emptyList(),
            importer = sheetImporter(),
            sheet = ImportSheet(listOf("Name"), listOf(ImportSheetRow(2, listOf("Ada")))),
            fileName = "rows.csv",
            csrf = "",
            error = "",
            done = "",
            storedMap = null,
            report = mapOf("summary" to "Ok.", "issues" to "nope"),
            pagePath = "/admin/import",
        )
        assertTrue(suggested.contains("option value=\"0\" selected"))
        assertTrue(suggested.contains("Ok."))

        val items = sheetImporter()
        val router = Router()
        Panel().path("admin").authenticate(false).imports(items, PlainImporter()).mount(router)
        val session = MemSession().also { it.put("_token", "t") }
        send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = session)
        val again = send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = session)
        assertTrue(again.body.contains("Upload items."))
        val blankKey = send(router, "GET", "/admin/import", query = mapOf("importer" to "   "), session = session)
        assertTrue(blankKey.body.contains("Upload items."))
        send(router, "GET", "/admin/import", query = mapOf("importer" to "items"))
        val unknownGet = send(router, "GET", "/admin/import", query = mapOf("importer" to "missing"), session = session)
        assertTrue(unknownGet.body.contains("choose one here"))

        val noFile = form(mapOf("importer" to "items", "_token" to "t"))
        send(router, "POST", "/admin/import", session = session, body = noFile.second, headers = noFile.first)
        val noFilePage = send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = session)
        assertTrue(noFilePage.body.contains("Choose a CSV or Excel file"))

        val bareRun = form(mapOf("importer" to "items", "_token" to "t", "map_name" to "nope", "commit" to "1"))
        send(router, "POST", "/admin/import/run", session = session, body = bareRun.second, headers = bareRun.first)
        val barePage = send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = session)
        assertTrue(barePage.body.contains("Upload a file first."))

        val postedBlank = multipart(mapOf("importer" to "   ", "_token" to "t"), "rows.csv", "Name\nAda\n".toByteArray())
        val storedUpload = send(router, "POST", "/admin/import", session = session, body = postedBlank.second, headers = postedBlank.first)
        assertEquals("/admin/import?importer=items", storedUpload.headers["Location"])

        session.put("firefly_import_module", "   ")
        val lost = form(mapOf("_token" to "t", "importer" to "   "))
        val lostRes = send(router, "POST", "/admin/import/cancel", session = session, body = lost.second, headers = lost.first)
        assertEquals("/admin/import", lostRes.headers["Location"])

        val sample = send(router, "GET", "/admin/import/template", query = mapOf("importer" to "plain"))
        assertEquals("sku\n", sample.body)

        val fresh = MemSession()
        val bare = send(router, "GET", "/admin/import", session = fresh)
        assertTrue(bare.body.contains("choose one here"))
        send(router, "GET", "/admin/import", query = mapOf("importer" to ""), session = fresh)
        assertEquals(500, send(router, "POST", "/admin/import").status)
        fresh.put("_token", "t")
        fresh.put("firefly_import_module", "items")
        val remembered = multipart(mapOf("_token" to "t"), "rows.csv", "Name\nAda\n".toByteArray())
        val rememberedUpload = send(
            router,
            "POST",
            "/admin/import",
            session = fresh,
            body = remembered.second,
            headers = remembered.first,
        )
        assertEquals("/admin/import?importer=items", rememberedUpload.headers["Location"])
        fresh.put(
            "firefly_import_report",
            mapOf("summary" to "Other.", "module" to "plain", "issues" to emptyList<Any>()),
        )
        val otherReport = send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = fresh)
        assertFalse(otherReport.body.contains("Other."))
        fresh.put("firefly_import_report", "nope")
        send(router, "GET", "/admin/import", query = mapOf("importer" to "items"), session = fresh)
        val unknownRun = form(mapOf("_token" to "t", "importer" to "missing", "commit" to "0"))
        val unknownRunResult = send(
            router,
            "POST",
            "/admin/import/run",
            session = fresh,
            body = unknownRun.second,
            headers = unknownRun.first,
        )
        assertEquals("/admin/import", unknownRunResult.headers["Location"])
        val spaced = form(
            mapOf("importer" to "items", "_token" to "t", "map_name" to "  ", "map_note" to "nope", "commit" to "0"),
        )
        send(router, "POST", "/admin/import/run", session = fresh, body = spaced.second, headers = spaced.first)

        val guest = Router()
        Panel().path("admin").imports(items).mount(guest)
        assertEquals(302, send(guest, "GET", "/admin/import/template", query = mapOf("importer" to "items")).status)

        val hosted = Router()
        Panel().path("admin").domain("billing.test").authenticate(false).imports(items).mount(hosted)
        assertEquals(404, send(hosted, "POST", "/admin/import/cancel", headers = mapOf("Host" to "nope.test")).status)
        assertEquals(404, send(hosted, "GET", "/admin/import/template", headers = mapOf("Host" to "other.test")).status)

        val loud = sheetImporter(key = "loud")
        loud.explode = true
        loud.explodeMessage = "Stopped."
        val loudRouter = Router()
        val loudSession = MemSession().also { it.put("_token", "t") }
        Panel().path("admin").authenticate(false).imports(loud).mount(loudRouter)
        val loudFile = multipart(mapOf("importer" to "loud", "_token" to "t"), "rows.csv", "Name\nAda\n".toByteArray())
        send(loudRouter, "POST", "/admin/import", session = loudSession, body = loudFile.second, headers = loudFile.first)
        val loudRun = form(mapOf("importer" to "loud", "_token" to "t", "map_name" to "0", "commit" to "1"))
        send(loudRouter, "POST", "/admin/import/run", session = loudSession, body = loudRun.second, headers = loudRun.first)
        val loudPage = send(loudRouter, "GET", "/admin/import", query = mapOf("importer" to "loud"), session = loudSession)
        assertTrue(loudPage.body.contains("Stopped."))
    }

    private class PlainImporter : Importer() {
        override val key = "plain"
        override val title = "Plain"
        override val listLabel = "Plain"
        override val listPath = "/plain"
        override val columns = listOf(ImportColumn.make("sku").label("SKU"))
        override val uploadText = "Upload."
        override val matchText = "Match SKU."
        override fun resolve(rows: List<ImportRow>): ImportResolution = SimpleResolution(0, 0, emptyList(), "none")
        override fun apply(resolution: ImportResolution) = Unit
    }

    private fun panelWithRoom(vararg importers: RecordingImporter): Pair<Router, MemSession> {
        val router = Router()
        Panel().path("admin").authenticate(false).imports(*importers).mount(router)
        return router to MemSession()
    }

    private fun sheetImporter(
        key: String = "items",
        title: String = "Import items",
        listLabel: String = "Items",
        listPath: String = "/admin/items/manage",
        uploadText: String = "Upload items.",
        matchText: String = "Name has to be matched. Note can stay on Don't import.",
        columns: List<ImportColumn> = listOf(
            ImportColumn.make("name").label("Name").requiredMapping().aliases("name", "title"),
            ImportColumn.make("note").label("Note"),
        ),
        outcome: (List<ImportRow>) -> ImportResolution = { rows ->
            SimpleResolution(rows.count { it.values["name"].orEmpty().isNotEmpty() }, 0, emptyList(), "1 new item.")
        },
    ) = RecordingImporter(key, title, listLabel, listPath, uploadText, matchText, columns, outcome)

    private class RecordingImporter(
        override val key: String,
        override val title: String,
        override val listLabel: String,
        override val listPath: String,
        override val uploadText: String,
        override val matchText: String,
        override val columns: List<ImportColumn>,
        private val outcome: (List<ImportRow>) -> ImportResolution,
    ) : Importer() {
        var applied: Int = 0
        var resolved: Boolean = false
        var explode: Boolean = false
        var explodeMessage: String? = null

        override fun template(): String = "name,note\nAda,hello\n"

        override fun resolve(rows: List<ImportRow>): ImportResolution {
            resolved = true
            return outcome(rows)
        }

        override fun apply(resolution: ImportResolution) {
            if (explode) {
                val message = explodeMessage
                if (message == null) throw IllegalArgumentException() else throw IllegalArgumentException(message)
            }
            applied += 1
        }
    }

    private class SimpleResolution(
        override val creates: Int,
        override val updates: Int,
        override val issues: List<ImportProblem>,
        private val text: String,
    ) : ImportResolution {
        override fun summary(): String = text
    }

    private class MemSession : RequestSession {
        val data = linkedMapOf<String, Any?>()
        private val pending = mutableSetOf<String>()
        private val visible = mutableSetOf<String>()

        fun begin() {
            for (key in visible) data.remove(key)
            visible.clear()
            visible.addAll(pending)
            pending.clear()
        }

        override fun get(key: String): Any? = data[key]
        override fun put(key: String, value: Any?): RequestSession = apply { data[key] = value }
        override fun flash(key: String, value: Any?): RequestSession {
            data[key] = value
            pending.add(key)
            visible.remove(key)
            return this
        }
        override fun flashInput(input: Map<String, String>): RequestSession = this
        override fun old(key: String, default: String?): String? = default
        override fun oldInput(): Map<String, String> = emptyMap()
        override fun previousUrl(): String? = null
        override fun setPreviousUrl(url: String): RequestSession = this
    }

    private fun send(
        router: Router,
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        session: RequestSession? = null,
        body: ByteArray = ByteArray(0),
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val match = router.find(method, path) ?: throw AssertionError("missing $method $path")
        if (session is MemSession) session.begin()
        val request = Request(method, path, headers, query, "", linkedMapOf(), body)
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    private fun form(fields: Map<String, String>): Pair<Map<String, String>, ByteArray> {
        val body = fields.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, Charsets.UTF_8) + "=" + URLEncoder.encode(value, Charsets.UTF_8)
        }
        return mapOf("Content-Type" to "application/x-www-form-urlencoded") to body.toByteArray()
    }

    private fun multipart(
        fields: Map<String, String>,
        fileName: String,
        bytes: ByteArray,
        contentType: String = "text/csv",
    ): Pair<Map<String, String>, ByteArray> {
        val boundary = "----kona-import"
        val out = ByteArrayOutputStream()
        fun write(text: String) = out.write(text.toByteArray())
        for ((key, value) in fields) {
            write("--$boundary\r\n")
            write("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
            write(value)
            write("\r\n")
        }
        write("--$boundary\r\n")
        write("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
        write("Content-Type: $contentType\r\n\r\n")
        out.write(bytes)
        write("\r\n--$boundary--\r\n")
        return mapOf("Content-Type" to "multipart/form-data; boundary=$boundary") to out.toByteArray()
    }

    private fun workbookBytes(workbook: Workbook, fill: (Workbook) -> Unit): ByteArray {
        workbook.use {
            fill(it)
            val out = ByteArrayOutputStream()
            it.write(out)
            return out.toByteArray()
        }
    }

    private fun encrypted(): ByteArray {
        val fs = POIFSFileSystem()
        val info = EncryptionInfo(EncryptionMode.agile)
        val enc = info.encryptor
        enc.confirmPassword("secret")
        enc.getDataStream(fs).use { stream ->
            XSSFWorkbook().use { book ->
                book.createSheet("S").createRow(0).createCell(0).setCellValue("a")
                book.write(stream)
            }
        }
        val out = ByteArrayOutputStream()
        fs.writeFilesystem(out)
        fs.close()
        return out.toByteArray()
    }
}
