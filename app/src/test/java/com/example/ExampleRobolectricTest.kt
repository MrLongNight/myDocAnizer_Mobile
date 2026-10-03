package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.model.CustomFieldEntity
import com.example.model.CustomFieldScope
import com.example.model.CustomFieldType
import com.example.service.SettingsRepository
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("myDocAnizer-Mobile", appName)
  }

  @Test
  fun `launch MainActivity successfully without crash`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java)
    val activity = controller.setup().visible().get()
    assertNotNull(activity)
    assertFalse(activity.isFinishing)
  }

  @Test
  fun `verify SplashSoundSynthesizer is safe in headless emulator`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.ui.components.SplashSoundSynthesizer.precompute(context)
    com.example.ui.components.SplashSoundSynthesizer.playPremiumSplashSound(context)
    com.example.ui.components.SplashSoundSynthesizer.stop()
  }

  @Test
  fun `verify database custom fields schema and preset initialization`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.customFieldDao()

    val testField = CustomFieldEntity(
      id = "test_field_1",
      name = "Test Betrag",
      type = CustomFieldType.AMOUNT,
      scope = CustomFieldScope.GLOBAL
    )
    dao.insertCustomField(testField)

    val list = dao.getAllCustomFieldsList()
    assertTrue(list.any { it.name == "Test Betrag" })
  }

  @Test
  fun `verify DocAnizerViewModel initialization and custom fields`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)
    assertNotNull(viewModel)

    val fields = viewModel.customFields.first()
    assertNotNull(fields)
  }

  @Test
  fun `verify IncomeExpenseTracking and reconciliation settings flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.setEnableIncomeExpenseTracking(true)
    viewModel.setEnableCashTracker(true)
    viewModel.setEnableReceiptExpenses(true)
    viewModel.setEnableBankStatementImport(true)
    viewModel.setEnableMonthlyReconciliation(true)
    viewModel.setNotifyReconciliationDiscrepancies(true)

    assertEquals(true, viewModel.enableIncomeExpenseTracking.first())
    assertEquals(true, viewModel.enableCashTracker.first())
    assertEquals(true, viewModel.enableReceiptExpenses.first())
    assertEquals(true, viewModel.enableBankStatementImport.first())
    assertEquals(true, viewModel.enableMonthlyReconciliation.first())
    assertEquals(true, viewModel.notifyReconciliationDiscrepancies.first())
  }

  @Test
  fun `verify Dashboard widget configuration flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.setShowBelegQuickScanWidget(true)
    viewModel.setShowCashTrackerWidget(true)
    viewModel.setShowKpiWidgets(true)
    viewModel.setShowDeadlinesWidget(true)
    viewModel.setShowFinanceWidget(true)
    viewModel.setShowReconciliationWidget(true)
    viewModel.setShowCategoryDistributionWidget(true)
    viewModel.setShowSecurityScoreWidget(true)
    viewModel.setShowCustomFieldsWidget(true)

    assertEquals(true, viewModel.showBelegQuickScanWidget.first())
    assertEquals(true, viewModel.showCashTrackerWidget.first())
    assertEquals(true, viewModel.showKpiWidgets.first())
    assertEquals(true, viewModel.showDeadlinesWidget.first())
    assertEquals(true, viewModel.showFinanceWidget.first())
    assertEquals(true, viewModel.showReconciliationWidget.first())
    assertEquals(true, viewModel.showCategoryDistributionWidget.first())
    assertEquals(true, viewModel.showSecurityScoreWidget.first())
    assertEquals(true, viewModel.showCustomFieldsWidget.first())
  }

  @Test
  fun `verify FinanceDao cash transactions and bank statements`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.financeDao()

    val cashTx = com.example.model.CashTransactionEntity(
      id = "test_cash_1",
      title = "Tanken Bar",
      amount = 45.0,
      type = com.example.model.CashTransactionType.EXPENSE,
      matchReconciliationMonth = "2026-04"
    )
    dao.insertCashTransaction(cashTx)

    val bankEntry = com.example.model.BankStatementEntryEntity(
      id = "test_bank_1",
      date = System.currentTimeMillis(),
      bookingText = "Geldautomat Sparkasse",
      purpose = "Barabhebung",
      amount = -200.0,
      isCashWithdrawal = true,
      monthYear = "2026-04"
    )
    dao.insertBankStatementEntry(bankEntry)

    val cashList = dao.getAllCashTransactionsList()
    val bankList = dao.getAllBankStatementEntriesList()
    val withdrawals = dao.getCashWithdrawalsForMonth("2026-04")

    assertTrue(cashList.any { it.id == "test_cash_1" })
    assertTrue(bankList.any { it.id == "test_bank_1" })
    assertEquals(1, withdrawals.size)
  }

  @Test
  fun `verify unique CustomFieldType labels and duplicate field name prevention`() = runBlocking {
    val types = CustomFieldType.values()
    val labels = types.map { it.label }
    assertEquals(types.size, labels.distinct().size)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val field1 = CustomFieldEntity(
      id = "unique_field_1",
      name = "Eindeutiger Belegstatus",
      type = CustomFieldType.SELECTION,
      options = "A,B"
    )
    val field2 = CustomFieldEntity(
      id = "unique_field_2",
      name = "Eindeutiger Belegstatus",
      type = CustomFieldType.TEXT
    )

    val added1 = viewModel.addCustomFieldSync(field1)
    val added2 = viewModel.addCustomFieldSync(field2)

    assertTrue(added1)
    assertFalse(added2) // Duplikat Name abgelehnt!

    val dao = AppDatabase.getDatabase(app).customFieldDao()
    val list = dao.getAllCustomFieldsList().filter { it.name.trim().equals("Eindeutiger Belegstatus", ignoreCase = true) }
    assertEquals(1, list.size)
  }

  @Test
  fun `verify BankStatementParserService parses German CSV and detects cash withdrawals`() = runBlocking {
    val sampleCsv = """
      Auftragskonto;Buchungstag;Valutadatum;Buchungstext;Verwendungszweck;Beguenstigter;Betrag;Waehrung
      DE1234567890;15.09.2026;15.09.2026;Auszahlung Geldautomat;GA-Barauszahlung Sparkasse Filiale;Sparkasse;-250,00;EUR
      DE1234567890;18.09.2026;18.09.2026;Kartenzahlung;Supermarkt Einkauf;EDEKA;-34,50;EUR
      DE1234567890;25.09.2026;25.09.2026;Überweisung;Gehalt September 2026;Arbeitgeber GmbH;2800,00;EUR
    """.trimIndent()

    val parseResult = com.example.service.BankStatementParserService.parseCsvOrText(sampleCsv, "sparkasse_export.csv")
    assertEquals(3, parseResult.totalParsed)
    assertEquals(1, parseResult.cashWithdrawalsCount)
    assertEquals(250.0, parseResult.totalCashWithdrawalsAmount, 0.001)
    assertTrue(parseResult.detectedBankFormat.contains("Sparkasse"))

    val cashEntry = parseResult.entries.first { it.isCashWithdrawal }
    assertEquals(-250.0, cashEntry.amount, 0.001)
    assertEquals("2026-09", cashEntry.monthYear)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val inserted1 = viewModel.importBankStatementEntriesSync(parseResult.entries)
    assertEquals(3, inserted1)

    val insertedAgain = viewModel.importBankStatementEntriesSync(parseResult.entries)
    assertEquals(0, insertedAgain)
  }

  @Test
  fun `verify DeadlineDetectionService parses contract deadlines and due dates`() = runBlocking {
    val sampleText = """
      Sehr geehrter Kunde,
      vielen Dank für Ihren Mobilfunkvertrag bei Vodafone.
      Ihre Mindestvertragslaufzeit bis: 31.12.2026.
      Sie können spätestens kündbar bis zum 30.11.2026 kündigen.
      Der offene Rechnungsbetrag ist zahlbar bis zum 15.10.2026.
    """.trimIndent()

    val deadlines = com.example.service.DeadlineDetectionService.detectDeadlines(sampleText)
    assertTrue(deadlines.isNotEmpty())

    val cancellation = deadlines.firstOrNull { it.type == com.example.service.DeadlineType.CANCELLATION }
    assertNotNull(cancellation)
    assertEquals("30.11.2026", cancellation?.formattedDate)

    val contractEnd = deadlines.firstOrNull { it.type == com.example.service.DeadlineType.CONTRACT_END }
    assertNotNull(contractEnd)
    assertEquals("31.12.2026", contractEnd?.formattedDate)

    val payment = deadlines.firstOrNull { it.type == com.example.service.DeadlineType.PAYMENT_DUE }
    assertNotNull(payment)
    assertEquals("15.10.2026", payment?.formattedDate)

    val (primaryCancel, primaryEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(sampleText)
    assertNotNull(primaryCancel)
    assertNotNull(primaryEnd)
  }

  @Test
  fun `verify Custom Dashboard Widgets and Reordering flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // 1. Initial State: Quick scan and cash tracker must be false by default
    val freshSettings = com.example.service.SettingsRepository(app)
    assertEquals(false, freshSettings.showBelegQuickScanWidget.first())
    assertEquals(false, freshSettings.showCashTrackerWidget.first())

    // 2. Create custom widget
    val customWidget = com.example.model.CustomDashboardWidget(
      id = "custom_test_1",
      title = "Garantie-Belege",
      subtitle = "Meine Rechnungen mit Garantie",
      type = com.example.model.CustomDashboardWidgetType.TAG_FILTER,
      targetTag = "#garantie",
      colorSkin = "EMERALD"
    )

    viewModel.addCustomDashboardWidget(customWidget)
    val widgetsAfterAdd = viewModel.customDashboardWidgets.first()
    assertEquals(1, widgetsAfterAdd.size)
    assertEquals("Garantie-Belege", widgetsAfterAdd[0].title)
    assertEquals("#garantie", widgetsAfterAdd[0].targetTag)

    // 3. Update custom widget checklist & note
    val checklistItems = listOf(
      com.example.model.ChecklistItem(id = "item_1", text = "Rechnung ablegen", isDone = false),
      com.example.model.ChecklistItem(id = "item_2", text = "Seriennummer notieren", isDone = true)
    )
    viewModel.updateCustomWidgetChecklist("custom_test_1", checklistItems)
    viewModel.updateCustomWidgetNote("custom_test_1", "Garantie gilt 24 Monate")

    val updatedWidgets = viewModel.customDashboardWidgets.first()
    assertEquals(2, updatedWidgets[0].checklistItems.size)
    assertEquals(true, updatedWidgets[0].checklistItems[1].isDone)
    assertEquals("Garantie gilt 24 Monate", updatedWidgets[0].noteText)

    // 4. Test reordering
    viewModel.moveDashboardWidgetUp("custom_test_1")
    val order = viewModel.dashboardWidgetOrder.first()
    assertTrue(order.contains("custom_test_1"))

    // 5. Delete custom widget
    viewModel.deleteCustomDashboardWidget("custom_test_1")
    val widgetsAfterDelete = viewModel.customDashboardWidgets.first()
    assertTrue(widgetsAfterDelete.isEmpty())

    // 6. Test per-element Period Scope settings & filtering
    viewModel.setElementPeriodScope("STANDARD_KPI", com.example.model.ElementPeriodScope.MONTH)
    val scopes = viewModel.elementPeriodScopes.first()
    assertEquals(com.example.model.ElementPeriodScope.MONTH, scopes["STANDARD_KPI"])

    val now = System.currentTimeMillis()
    val testDocs = listOf(
      com.example.model.DocumentEntity(
        id = 1L,
        title = "Beleg Heute",
        sender = "EDEKA",
        fileName = "beleg_heute.pdf",
        filePath = "/path/1.pdf",
        mainCategory = "Finanzen",
        subCategory = "Einkauf",
        createdAt = now
      ),
      com.example.model.DocumentEntity(
        id = 2L,
        title = "Beleg Alt",
        sender = "REWE",
        fileName = "beleg_alt.pdf",
        filePath = "/path/2.pdf",
        mainCategory = "Finanzen",
        subCategory = "Einkauf",
        createdAt = now - 400L * 24 * 60 * 60 * 1000
      )
    )
    val filteredYear = com.example.model.ElementPeriodScope.YEAR.filterDocuments(testDocs, now)
    assertEquals(1, filteredYear.size)
    assertEquals(1L, filteredYear[0].id)
  }

  @Test
  fun `verify extended HuggingFace models and catalog sync flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val models = viewModel.availableModels.first()
    assertTrue(models.any { it.id == "deepseek-r1-distill-qwen-1.5b" })
    assertTrue(models.any { it.id == "phi-3.5-mini-instruct" })
    assertTrue(models.any { it.id == "ministral-3b-instruct" })
    assertTrue(models.any { it.id == "qwen2.5-coder-1.5b" })

    val deepSeek = models.first { it.id == "deepseek-r1-distill-qwen-1.5b" }
    assertEquals("Reasoning & Logik", deepSeek.modelCategory)
    assertTrue(deepSeek.isCuratedApproved)

    // Test Catalog Sync
    viewModel.syncModelCatalogFromRemote(forceCheck = true)
    // Wait briefly or check flow
    val notif = viewModel.newModelsNotification.first()
    // Test dismiss
    viewModel.dismissNewModelsNotification()
    assertEquals(null, viewModel.newModelsNotification.value)
  }

  @Test
  fun `verify general invoice classification and custom fields`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Select Qwen 2.5 1.5B
    viewModel.selectHuggingFaceModel("qwen2.5-1.5b-instruct")

    val ocrText = """
      Acme Dienstleistungen GmbH
      Rechnung Nr: RE-2026-9812
      Kundennummer: KD-49102
      Rechnungsbetrag: 249,00 €
      Fälligkeit: 30.04.2026
      Vielen Dank für Ihren Auftrag.
    """.trimIndent()

    val result = viewModel.llmService.classifyDocumentText(ocrText)
    assertEquals("Rechnung", result.docType)
    assertEquals("A02", result.mainCategoryId)
    assertEquals("B2.01", result.subCategoryId)
    assertTrue(result.title.contains("Rechnung"))
    assertTrue(result.customFields.containsKey("Rechnungsbetrag"))
    assertTrue(result.customFields["Rechnungsbetrag"]!!.contains("249,00"))
    assertTrue(result.customFields.containsKey("Fälligkeit"))
    assertEquals("30.04.2026", result.customFields["Fälligkeit"])
    assertEquals("qwen2.5-1.5b-instruct", result.modelIdUsed)
  }

  @Test
  fun `verify general contract classification and custom fields`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Select DeepSeek-R1 Distill
    viewModel.selectHuggingFaceModel("deepseek-r1-distill-qwen-1.5b")

    val ocrText = """
      Nordic Fitness Club
      Mitgliedsvertrag
      Vertragsnummer: VTR-88192
      Vertragsdatum: 01.03.2026
      Monatlicher Beitrag: 39,90 €
    """.trimIndent()

    val result = viewModel.llmService.classifyDocumentText(ocrText)
    assertEquals("Vertrag", result.docType)
    assertEquals("A02", result.mainCategoryId)
    assertEquals("B2.03", result.subCategoryId)
    assertTrue(result.title.contains("Vertrag"))
    assertTrue(result.customFields.containsKey("Vertragsnummer"))
    assertEquals("VTR-88192", result.customFields["Vertragsnummer"])
    assertEquals("deepseek-r1-distill-qwen-1.5b", result.modelIdUsed)
    assertTrue(result.explanation.contains("<think>"))
  }

  @Test
  fun `verify AppAuditLogger logging and file export`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    com.example.service.AppAuditLogger.init(app)

    com.example.service.AppAuditLogger.log(
      category = com.example.service.LogCategory.AI_INFERENCE,
      tag = "TestInference",
      message = "Inferenz-Test erfolgreich ausgeführt",
      details = "Model: qwen2.5-1.5b-instruct"
    )

    val logs = com.example.service.AppAuditLogger.logs.value
    assertTrue(logs.any { it.tag == "TestInference" })

    val exportedFile = com.example.service.AppAuditLogger.exportLogFile(app)
    assertTrue(exportedFile.exists())
    assertTrue(exportedFile.length() > 0)
    val content = exportedFile.readText()
    assertTrue(content.contains("myDocAnizer-Mobile - DIAGNOSE & AUDIT-PROTOKOLL"))
    assertTrue(content.contains("TestInference"))
  }

  @Test
  fun `verify real model file management and disk operations`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val modelId = "smollm2-135m-instruct"
    val modelFile = viewModel.llmService.getModelFile(modelId)
    assertNotNull(modelFile)
    assertTrue(modelFile.name.endsWith(".gguf"))

    // Test creating and deleting a simulated model file
    modelFile.parentFile?.mkdirs()
    modelFile.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x01, 0x00)) // GGUF header
    assertTrue(modelFile.exists())

    viewModel.refreshHardwareInfo()
    val model = viewModel.llmService.availableModels.value.find { it.id == modelId }
    assertNotNull(model)
    assertTrue(model!!.downloadUrl.startsWith("https://huggingface.co/"))

    viewModel.deleteModel(modelId)
    assertFalse(modelFile.exists())
    val afterDelete = viewModel.llmService.availableModels.value.find { it.id == modelId }
    assertFalse(afterDelete!!.isDownloaded)
  }

  @Test
  fun `verify AI Chatbot integration and conversation flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val initialMessages = viewModel.chatMessages.value
    assertTrue(initialMessages.isNotEmpty())
    assertFalse(initialMessages[0].isUser)

    val result = viewModel.llmService.queryDocumentAssistantWithFollowUps(
      question = "Erstelle mir ein Kündigungsschreiben für meinen Vertrag",
      documents = emptyList()
    )
    assertTrue(result.replyText.contains("Kündigung"))

    // Test App-Steuerung per lokaler KI
    val scanResult = viewModel.llmService.queryDocumentAssistantWithFollowUps(
      question = "Öffne den Scanner",
      documents = emptyList()
    )
    assertEquals("OPEN_SCANNER", scanResult.actionType)

    viewModel.clearChat()
    assertEquals(1, viewModel.chatMessages.value.size)
  }

  @Test
  fun `verify LLM Tool definitions and OpenAI schema generation`() {
    val filterTool = com.example.service.AppTool.FilterDocuments
    assertEquals("filterDocuments", filterTool.name)
    val filterSchema = filterTool.toOpenAiToolSchema()
    assertEquals("function", filterSchema.getString("type"))
    val funcObj = filterSchema.getJSONObject("function")
    assertEquals("filterDocuments", funcObj.getString("name"))
    val paramsObj = funcObj.getJSONObject("parameters")
    val props = paramsObj.getJSONObject("properties")
    assertTrue(props.has("sender"))
    assertTrue(props.has("status"))
    assertTrue(props.has("category"))

    val updateTool = com.example.service.AppTool.UpdateDocumentStatus
    assertEquals("updateDocumentStatus", updateTool.name)
    val updateSchema = updateTool.toOpenAiToolSchema()
    val updateFunc = updateSchema.getJSONObject("function")
    val updateParams = updateFunc.getJSONObject("parameters")
    val req = updateParams.getJSONArray("required")
    assertTrue(req.toString().contains("docId"))
    assertTrue(req.toString().contains("newStatus"))

    // Test kotlinx JsonObject generation
    val kotlinxObj = filterTool.toKotlinxJsonObject()
    assertNotNull(kotlinxObj)
    assertTrue(kotlinxObj.toString().contains("filterDocuments"))
  }

  @Test
  fun `verify system prompt injection with available tools`() {
    val injectedPrompt = com.example.service.LlmToolPromptBuilder.buildInjectedSystemPrompt()
    assertTrue(injectedPrompt.contains("filterDocuments"))
    assertTrue(injectedPrompt.contains("updateDocumentStatus"))
    assertTrue(injectedPrompt.contains("WICHTIGE ANWEISUNG ZUR TOOL-NUTZUNG"))
    assertTrue(injectedPrompt.contains("AUSSCHLIESSLICH mit einem JSON-Block"))
    assertTrue(injectedPrompt.contains("```json"))
  }

  @Test
  fun `verify LlmToolRouter parsing valid tool calls and fallback for broken output`() = runBlocking {
    val router = com.example.service.LlmToolRouter()

    // 1. Valid filterDocuments call with markdown
    val filterOutput = """
      ```json
      {
        "name": "filterDocuments",
        "arguments": {
          "sender": "Telekom",
          "status": "Offen",
          "category": "Finanzen"
        }
      }
      ```
    """.trimIndent()

    val parsedFilter = router.parseToolCall(filterOutput)
    assertNotNull(parsedFilter)
    assertTrue(parsedFilter is com.example.service.AppToolCall.FilterDocuments)
    val filterCall = parsedFilter as com.example.service.AppToolCall.FilterDocuments
    assertEquals("Telekom", filterCall.sender)
    assertEquals("Offen", filterCall.status)
    assertEquals("Finanzen", filterCall.category)

    // 2. Valid updateDocumentStatus call without markdown
    val updateOutput = """{"name": "updateDocumentStatus", "arguments": {"docId": "42", "newStatus": "Bezahlt"}}"""
    val parsedUpdate = router.parseToolCall(updateOutput)
    assertNotNull(parsedUpdate)
    assertTrue(parsedUpdate is com.example.service.AppToolCall.UpdateDocumentStatus)
    val updateCall = parsedUpdate as com.example.service.AppToolCall.UpdateDocumentStatus
    assertEquals("42", updateCall.docId)
    assertEquals("Bezahlt", updateCall.newStatus)

    // 3. Normal conversational text should not be recognized as tool call
    val textOutput = "Hallo! Hier ist deine Aufstellung über alle Rechnungen des Monats."
    val parsedText = router.parseToolCall(textOutput)
    assertEquals(null, parsedText)

    val routedTextResult = router.routeResponse(textOutput)
    assertTrue(routedTextResult is com.example.service.LlmRouterResult.Text)
    assertEquals(textOutput, (routedTextResult as com.example.service.LlmRouterResult.Text).replyText)

    // 4. Malformed JSON should not crash and fall back safely to text
    val brokenJson = "```json\n{ name: filterDocuments, broken_args: [ \n```"
    val parsedBroken = router.parseToolCall(brokenJson)
    assertEquals(null, parsedBroken)
    val routedBroken = router.routeResponse(brokenJson)
    assertTrue(routedBroken is com.example.service.LlmRouterResult.Text)
  }

  @Test
  fun `verify LlmToolRouter native execution and ViewModel integration`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Insert a document for testing
    val db = AppDatabase.getDatabase(app)
    val docDao = db.documentDao()
    val testDocId = docDao.insertDocument(
      com.example.model.DocumentEntity(
        title = "Telekom Festnetz Rechnungsbeleg",
        sender = "Telekom",
        fileName = "telekom_rechnung.pdf",
        filePath = "/storage/test/telekom.pdf",
        mainCategory = "Finanzen",
        subCategory = "Telefon",
        docType = "Rechnung",
        amount = 49.95
      )
    )

    // 1. Test Filter via Tool Call
    val filterLlmResponse = """
      ```json
      {
        "name": "filterDocuments",
        "arguments": {
          "sender": "Telekom",
          "category": "Finanzen",
          "status": "Offen"
        }
      }
      ```
    """.trimIndent()

    val filterRouteResult = viewModel.llmToolRouter.routeResponse(filterLlmResponse)
    assertTrue(filterRouteResult is com.example.service.LlmRouterResult.ToolCallExecuted)
    val filterExec = filterRouteResult as com.example.service.LlmRouterResult.ToolCallExecuted
    assertTrue(filterExec.confirmationMessage.contains("Telekom"))
    assertTrue(filterExec.confirmationMessage.contains("Finanzen"))
    assertEquals("OPEN_EXPLORER", filterExec.actionType)

    // Verify filter was updated in ViewModel
    val activeFilter = viewModel.filter.value
    assertEquals("Telekom", activeFilter.sender)
    assertEquals("Finanzen", activeFilter.mainCategory)
    assertEquals("cf_status", activeFilter.customFieldId)
    assertEquals("Offen", activeFilter.customFieldValue)

    // 2. Test Update Document Status via Tool Call
    val updateLlmResponse = """
      ```json
      {
        "name": "updateDocumentStatus",
        "arguments": {
          "docId": "$testDocId",
          "newStatus": "Bezahlt"
        }
      }
      ```
    """.trimIndent()

    val updateRouteResult = viewModel.llmToolRouter.routeResponse(updateLlmResponse)
    assertTrue(updateRouteResult is com.example.service.LlmRouterResult.ToolCallExecuted)
    val updateExec = updateRouteResult as com.example.service.LlmRouterResult.ToolCallExecuted
    assertTrue(updateExec.confirmationMessage.contains("Bezahlt"))
    assertTrue(updateExec.confirmationMessage.contains("$testDocId"))

    // Verify status was persisted in Room DB
    val customDao = db.customFieldDao()
    val customVals = customDao.getCustomFieldValuesForDocumentList(testDocId)
    val statusVal = customVals.find { it.customFieldId == "cf_status" }
    assertNotNull(statusVal)
    assertEquals("Bezahlt", statusVal?.fieldValue)
  }

  @Test
  fun `verify chat message execution with tool calling flow`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Send a query that requests filtering synchronously in test
    val routerResult = viewModel.sendChatMessageSync("Filtere nach Telekom")
    assertTrue(routerResult is com.example.service.LlmRouterResult.ToolCallExecuted)

    val messages = viewModel.chatMessages.value
    assertTrue("Expected at least 2 messages but was: ${messages.size}", messages.size >= 2)
    val lastMsg = messages.last()
    assertFalse(lastMsg.isUser)
    assertTrue("Message text was: ${lastMsg.text}", lastMsg.text.contains("Dokumenten-Filter") || lastMsg.text.contains("Telekom"))
    assertEquals("OPEN_EXPLORER", lastMsg.actionType)
  }

  @Test
  fun `verify CreateFolder tool definition schema and router parsing`() = runBlocking {
    val createFolderTool = com.example.service.AppTool.CreateFolder
    assertEquals("createFolder", createFolderTool.name)
    val schema = createFolderTool.toOpenAiToolSchema()
    val func = schema.getJSONObject("function")
    val params = func.getJSONObject("parameters")
    val required = params.getJSONArray("required")
    assertTrue(required.toString().contains("folderName"))

    // Verify router parsing
    val router = com.example.service.LlmToolRouter()
    val output = """
      ```json
      {
        "name": "createFolder",
        "arguments": {
          "folderName": "Versicherungen 2026",
          "parentCategory": "Finanzen"
        }
      }
      ```
    """.trimIndent()
    val parsed = router.parseToolCall(output)
    assertNotNull(parsed)
    assertTrue(parsed is com.example.service.AppToolCall.CreateFolder)
    val folderCall = parsed as com.example.service.AppToolCall.CreateFolder
    assertEquals("Versicherungen 2026", folderCall.folderName)
    assertEquals("Finanzen", folderCall.parentCategory)
  }

  @Test
  fun `verify folder creation via chat message tool execution`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // User asks to create a folder
    val routerResult = viewModel.sendChatMessageSync("Erstelle einen Ordner Steuern 2024")
    assertTrue("Expected ToolCallExecuted but was: $routerResult", routerResult is com.example.service.LlmRouterResult.ToolCallExecuted)

    val exec = routerResult as com.example.service.LlmRouterResult.ToolCallExecuted
    assertTrue(exec.confirmationMessage.contains("Steuern 2024"))
    assertEquals("OPEN_EXPLORER", exec.actionType)

    // Verify template and folder were actually created in ViewModel
    val templates = viewModel.templates.value
    val createdTemplate = templates.find { it.mainCategory.contains("Steuern 2024") || it.subCategory.contains("Steuern 2024") }
    assertNotNull("Created template must exist in templates list", createdTemplate)

    // Verify chat message displays confirmation
    val messages = viewModel.chatMessages.value
    val lastMsg = messages.last()
    assertFalse(lastMsg.isUser)
    assertTrue(lastMsg.text.contains("Steuern 2024"))
  }

  @Test
  fun `verify chatbot pure swipe gesture setting toggle`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Initially false
    viewModel.setChatbotPureSwipeGestures(false)
    assertFalse(viewModel.chatbotPureSwipeGestures.value)

    // Toggle to true
    viewModel.setChatbotPureSwipeGestures(true)
    assertTrue(viewModel.chatbotPureSwipeGestures.value)

    // Toggle back
    viewModel.setChatbotPureSwipeGestures(false)
    assertFalse(viewModel.chatbotPureSwipeGestures.value)
  }

  @Test
  fun `verify undo action for folder creation via LLM`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // 1. Create a folder via tool
    viewModel.sendChatMessageSync("Erstelle einen Ordner Auto 2026")
    assertNotNull(viewModel.lastUndoAction.value)

    val templatesBeforeUndo = viewModel.templates.value
    assertTrue(templatesBeforeUndo.any { it.mainCategory.contains("Auto 2026") || it.subCategory.contains("Auto 2026") })

    // 2. Undo the action
    viewModel.undoLastLlmAction()

    // Wait a short moment for coroutine
    kotlinx.coroutines.delay(100)

    val templatesAfterUndo = viewModel.templates.value
    assertFalse(templatesAfterUndo.any { it.mainCategory.contains("Auto 2026") || it.subCategory.contains("Auto 2026") })
    assertEquals(null, viewModel.lastUndoAction.value)

    val lastMsg = viewModel.chatMessages.value.last()
    assertTrue(lastMsg.text.contains("rückgängig gemacht"))
  }

  @Test
  fun `verify chat session saving loading and temporary mode`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // 1. Initial mode is temporary
    viewModel.startNewTemporaryChat()
    assertEquals(null, viewModel.currentChatSessionId.value)
    assertEquals("Temporärer Chat", viewModel.currentChatTitle.value)

    // 2. Send messages
    viewModel.sendChatMessageSync("Wie kann ich Verträge kündigen?")
    assertEquals("Wie kann ich Verträge kündigen?", viewModel.getLastUserPrompt())

    // 3. Save chat explicitly
    viewModel.saveCurrentChat("Kündigungs-Assistent")
    val savedId = viewModel.currentChatSessionId.value
    assertNotNull(savedId)
    assertEquals("Kündigungs-Assistent", viewModel.currentChatTitle.value)

    val savedList = viewModel.savedChatSessions.value
    assertTrue(savedList.any { it.id == savedId && it.title == "Kündigungs-Assistent" })

    // 4. Start new temporary chat
    viewModel.startNewTemporaryChat()
    assertEquals(null, viewModel.currentChatSessionId.value)
    assertEquals(1, viewModel.chatMessages.value.size) // Only initial greeting

    // 5. Reload saved chat
    val sessionToLoad = savedList.first { it.id == savedId }
    viewModel.loadSavedChatSession(sessionToLoad)
    assertEquals(savedId, viewModel.currentChatSessionId.value)
    assertEquals("Kündigungs-Assistent", viewModel.currentChatTitle.value)
    assertTrue(viewModel.chatMessages.value.any { it.text.contains("Kündigungs-Assistent") || it.text.contains("Verträge kündigen") })

    // 6. Rename session
    viewModel.renameSavedChatSession(savedId!!, "Vertragsrecht & Kündigungen")
    assertEquals("Vertragsrecht & Kündigungen", viewModel.currentChatTitle.value)

    // 7. Delete session
    viewModel.deleteSavedChatSession(savedId)
    assertEquals(null, viewModel.currentChatSessionId.value)
    assertFalse(viewModel.savedChatSessions.value.any { it.id == savedId })
  }

  @Test
  fun `verify pure gesture mode vs standard mode toggle`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // Initial state is false (standard mode with visible bottom bar & symmetrical FABs)
    viewModel.setGestureModeEnabled(false)
    assertEquals(false, viewModel.isGestureModeEnabled.first())

    // Enable pure gesture mode (immersive fullscreen, edge swipe zones active)
    viewModel.setGestureModeEnabled(true)
    assertEquals(true, viewModel.isGestureModeEnabled.first())

    // Toggle back
    viewModel.setGestureModeEnabled(false)
    assertEquals(false, viewModel.isGestureModeEnabled.first())
  }

  @Test
  fun `verify DebugDataSeeder generates realistic documents and files`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val settingsRepo = SettingsRepository(app)

    val count = com.example.util.DebugDataSeeder.seedDatabaseAndFiles(app, db, settingsRepo)
    assertTrue(count >= 10)

    val docs = db.documentDao().getAllDocumentsList()
    assertEquals(count, docs.size)

    // Check categories
    assertTrue(docs.any { it.mainCategory == "Finanzen" })
    assertTrue(docs.any { it.mainCategory == "Verträge" })
    assertTrue(docs.any { it.mainCategory == "Gesundheit" })
    assertTrue(docs.any { it.mainCategory == "Wohnung" })

    // Check files exist on disk
    for (doc in docs) {
      val f = java.io.File(doc.filePath)
      assertTrue("File should exist on disk: ${doc.filePath}", f.exists())
      assertTrue("File size should be greater than 0", f.length() > 0)
      assertTrue("OCR text must not be blank", doc.ocrText.isNotBlank())
    }

    // Check search / fuzzy query
    val vodafoneDocs = db.documentDao().searchDocuments("Vodafone").first()
    assertEquals(1, vodafoneDocs.size)
    assertEquals("Vodafone GmbH", vodafoneDocs[0].sender)
  }

  @Test
  fun `verify Settings navigation tab works properly`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.setTargetNavigationTab("SETTINGS")
    assertEquals("SETTINGS", viewModel.targetNavigationTab.value)
  }
}
