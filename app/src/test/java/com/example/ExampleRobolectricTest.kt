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
  fun `verify DocAnizerApp initialization and CrashGuard setup`() {
    val app = DocAnizerApp()
    app.onCreate()
    assertNotNull(app)
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
    assertEquals("Garantie gilt 24 Monate", updatedWidgets[0].noteContent)

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

  @Test
  fun `verify AP-1 OcrPreFilterMatrix slices top header and key lines for First Run`() {
    val longOcrText = buildString {
      appendLine("Deutsche Telekom Kundenservice")
      appendLine("Landgrabenweg 151, 53227 Bonn")
      appendLine("Datum: 10.04.2026")
      appendLine("Rechnung für März 2026")
      appendLine("Sehr geehrter Kunde,")
      for (i in 1..80) {
        appendLine("Posten $i: Verbindung nach Deutschland Einzelnachweis $i Minuten 0,05 EUR")
      }
      appendLine("Rechnungsbetrag: 79,90 €")
      appendLine("Fälligkeit: 25.04.2026")
      appendLine("IBAN: DE12345678901234567890")
      appendLine("Vielen Dank für Ihr Vertrauen.")
    }

    assertTrue(longOcrText.length > 2500)
    val slice = com.example.service.OcrPreFilterMatrix.extractFirstRunSlice(longOcrText, maxChars = 900)
    assertTrue("Slice must be <= 900 characters but was ${slice.length}", slice.length <= 900)
    assertTrue(slice.contains("Deutsche Telekom"))
    assertTrue(slice.contains("KOPFZEILEN"))
    assertTrue(slice.contains("Rechnungsbetrag") || slice.contains("79,90") || slice.contains("Fälligkeit"))
  }

  @Test
  fun `verify AP-1 Two-Stage Pipeline first run and second run with custom fields whitelist`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    val sampleOcr = """
      Stadtwerke München GmbH
      Emmy-Noether-Straße 2, 80992 München
      Jahresabrechnung 2025/2026
      Kundennummer: SWM-882193
      Zählernummer: Z-99210
      Fälligkeit: 15.05.2026
      Rechnungsbetrag: 412,50 €
      Vielen Dank für Ihre Energienutzung.
    """.trimIndent()

    // 1. Team 1: First Run (Schnelle Triage)
    val firstRun = viewModel.llmService.classifyDocumentFirstRun(sampleOcr)
    assertEquals("Stadtwerke", firstRun.sender)
    assertEquals("Rechnung", firstRun.docType)
    assertTrue(firstRun.title.contains("Stadtwerke"))
    assertTrue(firstRun.title.contains("abrechnung", ignoreCase = true) || firstRun.title.contains("Rechnung"))
    assertTrue(firstRun.explanation.contains("Team 1 Triage") || firstRun.explanation.contains("Inferenz"))

    // 2. Team 2 & 3: Second Run (Tiefenanalyse mit Whitelist existierender Zusatzfelder)
    val whitelisted = listOf("Kundennummer", "Zählernummer", "Fälligkeit", "Monatliche Kosten")
    val secondRun = viewModel.llmService.classifyDocumentSecondRun(
      ocrText = sampleOcr,
      whitelistedCustomFields = whitelisted,
      userCorrection = "Absender ist Stadtwerke München"
    )

    assertEquals("Stadtwerke München", secondRun.sender)
    assertEquals("Rechnung", secondRun.docType)
    assertEquals("Stadtwerke München Rechnung", secondRun.title)
    assertTrue(secondRun.customFields.containsKey("Kundennummer"))
    assertTrue(secondRun.customFields.containsKey("Fälligkeit"))
    assertTrue(secondRun.explanation.contains("Team 2 Tiefenanalyse"))

    // 3. Test ViewModel runSecondRunAnalysis persistence
    val db = AppDatabase.getDatabase(app)
    val docId = db.documentDao().insertDocument(
      com.example.model.DocumentEntity(
        title = "Stadtwerke Rechnung",
        sender = "Stadtwerke",
        fileName = "swm_rechnung.pdf",
        filePath = "/test/swm.pdf",
        mainCategory = "Finanzen",
        subCategory = "Energie",
        ocrText = sampleOcr
      )
    )

    var completed = false
    viewModel.runSecondRunAnalysis(docId, "Absender ist Stadtwerke München") { res ->
      completed = true
      assertNotNull(res)
    }

    var attempts = 0
    while (!completed && attempts < 30) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
      kotlinx.coroutines.delay(50)
      attempts++
    }
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertTrue(completed)
    val lastRes = viewModel.lastAiAnalysisResult.value[docId]
    assertNotNull(lastRes)
    assertEquals("Stadtwerke München", lastRes?.sender)
  }

  @Test
  fun `verify AP-2 Department Teams FolderStrategy and rule repository integration`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    // 1. FolderStrategy Speicherung und ViewModel-Flows
    viewModel.setFolderStrategy(com.example.model.FolderStrategy.BY_SENDER)
    assertEquals(com.example.model.FolderStrategy.BY_SENDER, viewModel.folderStrategy.first())

    viewModel.setFolderStrategy(com.example.model.FolderStrategy.CHRONOLOGICAL)
    assertEquals(com.example.model.FolderStrategy.CHRONOLOGICAL, viewModel.folderStrategy.first())

    // 2. Dispatcher für Fachabteilungs-Teams (Finanzen, Verträge, Verwaltung)
    assertEquals(
      com.example.service.DepartmentTeam.FINANCE,
      com.example.service.DepartmentTeam.dispatch("Rechnung")
    )
    assertEquals(
      com.example.service.DepartmentTeam.FINANCE,
      com.example.service.DepartmentTeam.dispatch("Stromabrechnung Stadtwerke")
    )
    assertEquals(
      com.example.service.DepartmentTeam.CONTRACTS,
      com.example.service.DepartmentTeam.dispatch("Versicherungspolice Allianz")
    )
    assertEquals(
      com.example.service.DepartmentTeam.CONTRACTS,
      com.example.service.DepartmentTeam.dispatch("Mietvertrag")
    )
    assertEquals(
      com.example.service.DepartmentTeam.ADMINISTRATION,
      com.example.service.DepartmentTeam.dispatch("Rentenbescheid")
    )
    assertEquals(
      com.example.service.DepartmentTeam.ADMINISTRATION,
      com.example.service.DepartmentTeam.dispatch("Allgemeiner Brief")
    )

    // 3. Department System Prompt & FolderStrategy-Injektion
    val prompt = com.example.service.LlmToolPromptBuilder.buildDepartmentSystemPrompt(
      department = com.example.service.DepartmentTeam.FINANCE,
      folderStrategy = com.example.model.FolderStrategy.BY_SENDER,
      whitelistedCustomFields = listOf("Rechnungsnummer", "Fälligkeit")
    )
    assertTrue(prompt.contains("Abteilung A (Finanzen & Buchhaltung)"))
    assertTrue(prompt.contains("Nach Absender (Firmen)"))
    assertTrue(prompt.contains("vorgeschlagener_pfad"))
    assertTrue(prompt.contains("Rechnungsnummer"))

    // 4. Second Run mit Department & FolderStrategy-Pfad
    val sampleInvoice = """
      Vodafone GmbH
      Ferdinand-Braun-Platz 1, 40549 Düsseldorf
      Mobilfunkrechnung März 2026
      Rechnungsnummer: VF-2026-9810
      Kundennummer: KD-118822
      Rechnungsbetrag: 49,99 €
      Fälligkeit: 20.03.2026
    """.trimIndent()

    val result = viewModel.llmService.classifyDocumentSecondRun(
      ocrText = sampleInvoice,
      folderStrategy = com.example.model.FolderStrategy.BY_SENDER,
      department = com.example.service.DepartmentTeam.FINANCE
    )
    assertEquals("Vodafone GmbH", result.sender)
    assertEquals("Rechnung", result.docType)
    assertEquals("Vodafone GmbH Rechnung", result.title)
    assertTrue(result.suggestedFolderPath.contains("Vodafone") || result.suggestedFolderPath.contains("Rechnung"))
    assertTrue(result.explanation.contains("Finanzen") || result.explanation.contains("Abt. A"))

    // 5. DocRuleRepository Abgleich und Pfad-Persistierung
    val ruleRepo = viewModel.ruleRepository
    val testRule = com.example.model.DocRule(
      id = "rule_vf_test",
      name = "Vodafone GmbH Rechnung",
      matchKeywords = listOf("vodafone", "mobilfunkrechnung"),
      targetDocType = "Rechnung",
      detectedSender = "Vodafone GmbH",
      targetFolderPath = "Vodafone/Rechnungen"
    )
    ruleRepo.addRule(testRule)

    val foundSimilar = ruleRepo.findMatchingOrSimilarRule("Vodafone GmbH", "Rechnung")
    assertNotNull(foundSimilar)
    assertEquals("Vodafone/Rechnungen", foundSimilar?.targetFolderPath)
  }

  @Test
  fun `verify AP-3 Quality Gatekeeper deterministischer cross-check and normalization`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    com.example.service.AppAuditLogger.init(app)

    // 1. Normalisierungs-Logik: Datumsangaben
    val normDate1 = com.example.service.QualityGatekeeper.normalizeDate("12. März 2024")
    assertNotNull(normDate1)
    assertEquals("12.03.2024", normDate1?.standardFormatted)

    val normDate2 = com.example.service.QualityGatekeeper.normalizeDate("5. Jan. 24")
    assertNotNull(normDate2)
    assertEquals("05.01.2024", normDate2?.standardFormatted)

    val normDate3 = com.example.service.QualityGatekeeper.normalizeDate("2024-11-20")
    assertNotNull(normDate3)
    assertEquals("20.11.2024", normDate3?.standardFormatted)

    // 2. Normalisierungs-Logik: Beträge
    val normAmount1 = com.example.service.QualityGatekeeper.normalizeAmount("1.000,00 €")
    assertNotNull(normAmount1)
    assertEquals(1000.00, normAmount1?.numericValue ?: 0.0, 0.001)
    assertEquals("1000.00", normAmount1?.standardFormatted)
    assertEquals("1.000,00", normAmount1?.germanFormatted)

    val normAmount2 = com.example.service.QualityGatekeeper.normalizeAmount("49,99 EUR")
    assertNotNull(normAmount2)
    assertEquals(49.99, normAmount2?.numericValue ?: 0.0, 0.001)

    // 3. Normalisierungs-Logik: IBAN
    val normIban = com.example.service.QualityGatekeeper.normalizeIban("DE12 3456 7890 1234 5678 90")
    assertEquals("DE12345678901234567890", normIban)

    // 4. Text-Anchor-Cross-Check: Erfolgreich verankerte Entitäten
    val authenticOcr = """
      Telekom Deutschland GmbH
      Rechnung Nr. 99182371
      Rechnungsdatum: 12.03.2024
      Gesamtbetrag: 1.000,00 €
      Zahlbar bis: 26.03.2024
      IBAN: DE12 3456 7890 1234 5678 90
    """.trimIndent()

    val validReport = com.example.service.QualityGatekeeper.auditExtraction(
      ocrText = authenticOcr,
      sender = "Telekom Deutschland GmbH",
      docType = "Rechnung",
      amountStr = "1.000,00 €",
      dueDateStr = "26.03.2024",
      docDateStr = "12.03.2024",
      ibanStr = "DE12345678901234567890"
    )

    assertTrue(validReport.isApproved)
    assertTrue(validReport.overallConfidence >= 0.90f)
    assertTrue(validReport.warnings.isEmpty())

    // 5. Halluzinations-Erkennung: Betrag kommt nicht im Text vor
    val hallucinatedReport = com.example.service.QualityGatekeeper.auditExtraction(
      ocrText = authenticOcr,
      sender = "Telekom Deutschland GmbH",
      docType = "Rechnung",
      amountStr = "5.432,10 €", // Halluzination!
      dueDateStr = "26.03.2024"
    )

    val amountCheck = hallucinatedReport.getCheckForField("Betrag")
    assertNotNull(amountCheck)
    assertFalse(amountCheck!!.isFoundInText)
    assertTrue(hallucinatedReport.warnings.any { it.contains("Betrag bitte kurz prüfen") })
    assertTrue(hallucinatedReport.overallConfidence < 0.80f)

    // 6. Logik-Plausibilität: Fälligkeitsdatum steht neben 'geb.' (Geburtsdatum-Verwechslung)
    val patientOcr = """
      Allgemeines Krankenhaus
      Patient: Max Mustermann, geb. 15.05.1982
      Rechnungsbetrag: 150,00 €
      Zahlungsziel: 30.06.2026
    """.trimIndent()

    val confusedReport = com.example.service.QualityGatekeeper.auditExtraction(
      ocrText = patientOcr,
      amountStr = "150,00 €",
      dueDateStr = "15.05.1982" // Verwechslung: Geburtsdatum fälschlicherweise als Fälligkeit genommen
    )

    val dueDateCheck = confusedReport.getCheckForField("Fälligkeit")
    assertNotNull(dueDateCheck)
    assertFalse(dueDateCheck!!.isPlausible)
    assertTrue(dueDateCheck.warningMessage!!.contains("Geburtsdatum-Verwechslung"))
    assertEquals("30.06.2026", dueDateCheck.suggestedCorrection)
    assertFalse(confusedReport.isApproved)

    // 7. Audit Logging Nachweis
    val logs = com.example.service.AppAuditLogger.logs.value
    assertTrue(logs.any { it.tag == "QualityGatekeeper" && it.message.contains("Team 4 Revision") })
  }

  @Test
  fun `verify FirstStepsTour step progression and completion`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.startFirstStepsTour()
    assertTrue(viewModel.showFirstStepsTour.first())
    assertEquals(1, viewModel.tourStep.first())

    viewModel.setTourStep(2)
    assertEquals(2, viewModel.tourStep.first())

    viewModel.setTourStep(4)
    assertEquals(4, viewModel.tourStep.first())

    viewModel.setTourStep(5)
    assertEquals(5, viewModel.tourStep.first())

    // Step 7: KI-Agent
    viewModel.setTourStep(7)
    assertEquals(7, viewModel.tourStep.first())

    // Step 8: Zwischenschritt 1 (KI-Chat Konversation)
    viewModel.setTourStep(8)
    assertEquals(8, viewModel.tourStep.first())

    // Step 9: Zwischenschritt 2 (KI-Chat Eingabe)
    viewModel.setTourStep(9)
    assertEquals(9, viewModel.tourStep.first())

    // Step 10: Dashboard & Fristen
    viewModel.setTourStep(10)
    assertEquals(10, viewModel.tourStep.first())

    // Step 11: Tour-Abschluss Dialog
    viewModel.setTourStep(11)
    assertEquals(11, viewModel.tourStep.first())

    viewModel.dismissFirstStepsTour(markCompleted = true)
    assertFalse(viewModel.showFirstStepsTour.first())
    assertTrue(viewModel.isFirstStepsTourCompleted.first())
  }

  @Test
  fun `verify Tour test document scan offers additional custom fields from OCR`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = DocAnizerViewModel(app)

    viewModel.startFirstStepsTour()
    viewModel.setTourStep(4)
    viewModel.triggerSampleScanForTour()

    val pending = viewModel.pendingRuleSuggestion.first()
    assertNotNull(pending)
    val rule = pending!!.suggestedRule
    assertNotNull(rule)

    // Core fields are assigned
    assertTrue(rule.targetCustomFields.containsKey("Rechnungsnummer"))
    assertTrue(rule.targetCustomFields.containsKey("Rechnungsbetrag"))

    // Audit report offers additional fields detected from OCR text
    val auditReport = com.example.service.llm.CustomFieldHarmonizer.auditRuleFields(
      ruleName = rule.name,
      docType = rule.targetDocType,
      sender = rule.detectedSender,
      currentCustomFields = rule.targetCustomFields,
      existingFields = emptyList(),
      ocrText = pending.ocrText
    )

    assertTrue("Zusätzliche Beleg-Felder müssen angeboten werden", auditReport.missingRecommendedFields.isNotEmpty())
    val offeredFieldNames = auditReport.missingRecommendedFields.map { it.fieldName }
    assertTrue("Fälligkeit muss als zusätzliches Feld angeboten werden", offeredFieldNames.contains("Fälligkeit"))
    assertTrue("Kundennummer muss als zusätzliches Feld angeboten werden", offeredFieldNames.contains("Kundennummer"))

    val dueItem = auditReport.missingRecommendedFields.first { it.fieldName == "Fälligkeit" }
    assertEquals("26.10.2026", dueItem.valueHint)
  }

  @Test
  fun `verify VaultSecurityManager Argon2id derivation and AES-GCM roundtrip`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    val dek = secManager.generateRandomDek()
    assertEquals(32, dek.size)

    val salt = ByteArray(16) { it.toByte() }
    val kek = secManager.deriveKekArgon2id("TestPass123!", salt, iterations = 1, memoryKb = 128)
    assertEquals(32, kek.size)

    val (ciphertext, iv) = secManager.encryptAesGcm(dek, kek)
    assertFalse(ciphertext.contentEquals(dek))

    val decrypted = secManager.decryptAesGcm(ciphertext, iv, kek)
    assertTrue(decrypted.contentEquals(dek))
  }

  @Test
  fun `verify BIP-39 12 words generation validation and recovery`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    val words = secManager.generateBip39Mnemonic()
    assertEquals(12, words.size)
    assertTrue(secManager.validateBip39Mnemonic(words))

    val invalidWords = words.toMutableList()
    invalidWords[0] = "invalidwordxyz"
    assertFalse(secManager.validateBip39Mnemonic(invalidWords))

    val setupRes = secManager.initializeVault(
      masterPassword = "MasterPassword2026!",
      enableBiometrics = false,
      recoveryMethod = com.example.service.VaultRecoveryMethod.BIP39_WORDS,
      bip39Words = words
    )
    assertNotNull(setupRes)
    val recoveredDek = secManager.recoverDekWithBip39(words)
    assertNotNull(recoveredDek)
    assertTrue(recoveredDek!!.contentEquals(setupRes.dek))
  }

  @Test
  fun `verify Shamir Secret Sharing 2 of 3 math and reconstruction`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    val dek = secManager.generateRandomDek()
    val shares = secManager.splitSecretShamir2of3(dek)
    assertEquals(3, shares.size)

    // Test combinations: (1, 2), (1, 3), (2, 3)
    val rec12 = secManager.recoverDekWithShamir(shares[0].token, shares[1].token)
    assertNotNull(rec12)
    assertTrue(rec12!!.contentEquals(dek))

    val rec13 = secManager.recoverDekWithShamir(shares[0].token, shares[2].token)
    assertNotNull(rec13)
    assertTrue(rec13!!.contentEquals(dek))

    val rec23 = secManager.recoverDekWithShamir(shares[1].token, shares[2].token)
    assertNotNull(rec23)
    assertTrue(rec23!!.contentEquals(dek))

    // Same share repeated must fail
    val recSame = secManager.recoverDekWithShamir(shares[0].token, shares[0].token)
    org.junit.Assert.assertNull(recSame)
  }

  @Test
  fun `verify Emergency Kit PDF generation and recovery`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    val setupRes = secManager.initializeVault(
      masterPassword = "MasterPassword2026!",
      enableBiometrics = false,
      recoveryMethod = com.example.service.VaultRecoveryMethod.EMERGENCY_PDF,
      emergencyPin = "987654"
    )

    val pdfFile = java.io.File(context.cacheDir, "test_emergency_kit.pdf")
    val generated = secManager.generateEmergencyPdf(pdfFile, "987654")
    assertNotNull(generated)
    assertTrue(generated!!.exists())
    assertTrue(generated.length() > 100)

    val base64Key = android.util.Base64.encodeToString(setupRes.dek, android.util.Base64.NO_WRAP)
    val directRecovery = secManager.recoverDekFromEmergencyKit(base64Key)
    assertNotNull(directRecovery)
    assertTrue(directRecovery!!.contentEquals(setupRes.dek))
  }

  @Test
  fun `verify Master Password change re-encrypts DEK`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    secManager.initializeVault(
      masterPassword = "InitialPassword123!",
      enableBiometrics = false,
      recoveryMethod = com.example.service.VaultRecoveryMethod.NONE
    )
    val originalDek = secManager.getActiveDek()!!.copyOf()
    assertNotNull(originalDek)

    val changed = secManager.changeMasterPassword("InitialPassword123!", "NewSecretPassword456!")
    assertTrue(changed)

    secManager.lockVault()
    assertFalse(secManager.isUnlocked())

    // Old password must fail
    assertFalse(secManager.unlockWithPassword("InitialPassword123!"))
    assertFalse(secManager.isUnlocked())

    // New password succeeds and restores identical DEK
    assertTrue(secManager.unlockWithPassword("NewSecretPassword456!"))
    assertTrue(secManager.isUnlocked())
    assertTrue(secManager.getActiveDek()!!.contentEquals(originalDek!!))
  }

  @Test
  fun `verify TOTP engine generation computation and verification`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    val secret = secManager.generateTotpSecret()
    assertTrue(secret.length >= 20)

    val codeNow = secManager.computeTotpCode(secret)
    assertEquals(6, codeNow.length)
    assertTrue(codeNow.all { it.isDigit() })

    // Verification of current code
    assertTrue(secManager.verifyTotpCode(secret, codeNow))
    assertFalse(secManager.verifyTotpCode(secret, "999999".takeIf { it != codeNow } ?: "123456"))

    // URI generation
    val uri = secManager.getTotpUri(secret, "TestUser")
    assertTrue(uri.startsWith("otpauth://totp/myDocAnizer:TestUser?secret="))
  }

  @Test
  fun `verify 2FA keyfile and recovery code generation and recovery`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val secManager = com.example.service.VaultSecurityManager.getInstance(context)

    val keyToken = secManager.generateKeyFileToken()
    assertEquals(64, keyToken.length)

    val recCode = secManager.generateRecoveryCode()
    assertEquals(39, recCode.length) // 32 chars + 7 hyphens = 39

    val setupRes = secManager.initializeVault(
      masterPassword = "StrongPassword789!",
      enableBiometrics = true,
      recoveryMethod = com.example.service.VaultRecoveryMethod.RECOVERY_CODE,
      securityTier = com.example.service.VaultSecurityTier.TWO_FACTOR,
      factorType = com.example.service.VaultFactorType.PASSWORD_AND_KEYFILE,
      keyFileToken = keyToken,
      recoveryCode = recCode,
      autoLockMinutes = 10
    )

    assertEquals(com.example.service.VaultSecurityTier.TWO_FACTOR, secManager.vaultStatus.value.securityTier)
    assertEquals(com.example.service.VaultFactorType.PASSWORD_AND_KEYFILE, secManager.vaultStatus.value.factorType)
    assertEquals(10, secManager.getAutoLockMinutes())

    // Recovery test with recovery code
    secManager.lockVault()
    assertFalse(secManager.isUnlocked())

    val recoveredDek = secManager.recoverDekWithRecoveryCode(recCode)
    assertNotNull(recoveredDek)
    assertTrue(recoveredDek!!.contentEquals(setupRes.dek))
    assertTrue(secManager.isUnlocked())
  }

  @Test
  fun `verify FTS query sanitization and safe query fallback for special characters`() = runBlocking {
    val sanitizedDash = com.example.data.DocumentDao.sanitizeFtsQuery("---")
    assertEquals("", sanitizedDash)

    val sanitizedStar = com.example.data.DocumentDao.sanitizeFtsQuery("*")
    assertEquals("", sanitizedStar)

    val sanitizedValid = com.example.data.DocumentDao.sanitizeFtsQuery("Rechnung 2026")
    assertEquals("Rechnung* AND 2026*", sanitizedValid)

    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val docDao = db.documentDao()

    val results = docDao.searchDocumentsFtsSafe("---").first()
    assertNotNull(results)
  }

  @Test
  fun `verify trash clearing file deletion and custom field value cleanup`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val docDao = db.documentDao()
    val customFieldDao = db.customFieldDao()

    val tempFile = java.io.File(app.cacheDir, "trash_test_doc.pdf").apply {
      writeText("Trash Test Content")
    }
    assertTrue(tempFile.exists())

    val doc = com.example.model.DocumentEntity(
      title = "Trash Doc",
      sender = "Test Sender",
      fileName = "trash_test_doc.pdf",
      filePath = tempFile.absolutePath,
      mainCategory = "Allgemein",
      subCategory = "Sonstiges",
      isDeleted = true,
      deletedAt = System.currentTimeMillis()
    )
    val docId = docDao.insertDocument(doc)

    val fieldValue = com.example.model.DocumentCustomFieldValueEntity(
      documentId = docId,
      customFieldId = "field_test_123",
      fieldValue = "Orphan Value"
    )
    customFieldDao.insertOrUpdateFieldValue(fieldValue)

    val valuesBefore = customFieldDao.getCustomFieldValuesForDocumentList(docId)
    assertEquals(1, valuesBefore.size)

    val viewModel = DocAnizerViewModel(app)
    viewModel.emptyTrash()

    kotlinx.coroutines.delay(500)

    val trashList = docDao.getTrashDocumentsList()
    assertTrue(trashList.isEmpty())

    val valuesAfter = customFieldDao.getCustomFieldValuesForDocumentList(docId)
    assertTrue(valuesAfter.isEmpty())

    assertFalse(tempFile.exists())
  }

  @Test
  fun `verify backup archive creation and restore roundtrip with vault metadata and 2-level folder structure`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(context)
    val ruleRepo = com.example.service.DocRuleRepository(context)

    // 1. Create a sample document file
    val sampleFile = java.io.File(context.cacheDir, "sample_invoice.pdf").apply {
      writeText("%PDF-1.4 Mock PDF Content for Backup Test")
    }

    val sampleDoc = com.example.model.DocumentEntity(
      id = 9999L,
      title = "Test Rechnung Telekom",
      sender = "Telekom",
      fileName = "sample_invoice.pdf",
      filePath = sampleFile.absolutePath,
      mainCategory = "Finanzen",
      subCategory = "Telekommunikation",
      mainCategoryId = "A02",
      subCategoryId = "B2.01",
      docType = "Rechnung"
    )

    // 2. Insert metadata: custom field, custom field value, cash tx, bank entry, widget, rule
    val testField = CustomFieldEntity(
      id = "field_roundtrip_1",
      name = "Vertragsnummer",
      type = CustomFieldType.TEXT,
      scope = CustomFieldScope.FOLDER_SPECIFIC,
      targetMainCategory = "Finanzen"
    )
    db.customFieldDao().insertCustomField(testField)

    val testFieldValue = com.example.model.DocumentCustomFieldValueEntity(
      documentId = 9999L,
      customFieldId = "field_roundtrip_1",
      fieldValue = "VK-12345678"
    )
    db.customFieldDao().insertOrUpdateFieldValue(testFieldValue)

    val testCashTx = com.example.model.CashTransactionEntity(
      id = "tx_roundtrip_1",
      title = "Büromaterial Bar",
      amount = 14.99,
      type = com.example.model.CashTransactionType.EXPENSE,
      category = "Büro"
    )
    db.financeDao().insertCashTransaction(testCashTx)

    val testBankEntry = com.example.model.BankStatementEntryEntity(
      id = "bank_roundtrip_1",
      date = System.currentTimeMillis(),
      bookingText = "Telekom Deutschland",
      purpose = "Monatsrechnung",
      amount = -49.95,
      isCashWithdrawal = false,
      monthYear = "2026-10"
    )
    db.financeDao().insertBankStatementEntry(testBankEntry)

    val testWidget = com.example.model.CustomDashboardWidgetEntity(
      id = "widget_roundtrip_1",
      title = "Telekom Ausgaben",
      targetMainCategory = "Finanzen"
    )
    db.customDashboardWidgetDao().insertOrUpdateWidget(testWidget)

    val testRule = com.example.model.DocRule(
      id = "rule_roundtrip_1",
      name = "Telekom Auto-Kategorie",
      matchKeywords = listOf("Telekom", "Festnetz"),
      targetMainCategoryId = "A02",
      targetSubCategoryId = "B2.01"
    )
    ruleRepo.addRule(testRule)

    // 3. Create encrypted backup archive
    val backupFile = java.io.File(context.cacheDir, "test_vault_backup.enc")
    val passwordKey = "MasterPasswordSafe123"

    val createdArchive = com.example.service.DocumentStorageService.createEncryptedBackupArchive(
      context = context,
      docs = listOf(sampleDoc),
      outputEncFile = backupFile,
      passwordKey = passwordKey,
      includeVaultMetadata = true
    )

    assertTrue("Backup-Datei sollte existieren", createdArchive.exists())
    assertTrue("Backup-Datei sollte Inhalt haben", createdArchive.length() > 0)

    // Delete existing entries to verify full restore
    db.customFieldDao().deleteCustomFieldById("field_roundtrip_1")
    db.customFieldDao().deleteAllValuesForDocument(9999L)
    db.financeDao().deleteCashTransactionById("tx_roundtrip_1")
    db.financeDao().deleteBankStatementEntryById("bank_roundtrip_1")
    db.customDashboardWidgetDao().deleteWidgetById("widget_roundtrip_1")
    ruleRepo.deleteRule("rule_roundtrip_1")

    // 4. Restore encrypted backup archive
    val restoreResult = com.example.service.DocumentStorageService.restoreEncryptedBackupArchive(
      context = context,
      inputEncFile = createdArchive,
      passwordKey = passwordKey,
      database = db,
      ruleRepository = ruleRepo
    )

    assertTrue("Restore sollte erfolgreich sein", restoreResult.isSuccess)
    val restoredDocs = restoreResult.getOrNull()
    assertNotNull(restoredDocs)
    assertEquals(1, restoredDocs!!.size)

    val restoredDoc = restoredDocs[0]
    assertEquals(9999L, restoredDoc.id)
    assertEquals("Finanzen", restoredDoc.mainCategory)
    assertEquals("Telekommunikation", restoredDoc.subCategory)

    // Verify 2-level folder path
    val restoredFile = java.io.File(restoredDoc.filePath)
    assertTrue("Wiederhergestellte Datei muss auf der Festplatte existieren", restoredFile.exists())
    assertTrue(
      "Pfad muss Unterordner für Finanzen/Telekommunikation enthalten", 
      restoredDoc.filePath.contains("Finanzen") || restoredDoc.filePath.contains("A02") || restoredDoc.filePath.contains("Telekommunikation")
    )

    // Verify restored database vault metadata
    val restoredFields = db.customFieldDao().getAllCustomFieldsList()
    assertTrue(restoredFields.any { it.id == "field_roundtrip_1" })

    val restoredFieldValues = db.customFieldDao().getCustomFieldValuesForDocumentList(9999L)
    assertTrue(restoredFieldValues.any { it.customFieldId == "field_roundtrip_1" && it.fieldValue == "VK-12345678" })

    val restoredTxs = db.financeDao().getAllCashTransactionsList()
    assertTrue(restoredTxs.any { it.id == "tx_roundtrip_1" })

    val restoredBankEntries = db.financeDao().getAllBankStatementEntriesList()
    assertTrue(restoredBankEntries.any { it.id == "bank_roundtrip_1" })

    val restoredWidgets = db.customDashboardWidgetDao().getAllWidgetsList()
    assertTrue(restoredWidgets.any { it.id == "widget_roundtrip_1" })

    val restoredRules = ruleRepo.rules.value
    assertTrue(restoredRules.any { it.id == "rule_roundtrip_1" })

    // Clean up
    if (backupFile.exists()) backupFile.delete()
    if (sampleFile.exists()) sampleFile.delete()
  }
}


