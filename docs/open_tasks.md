# Offene Refactoring-Aufgaben & Code-Optimierung (myDocAnizer-Mobile)

Dieses Dokument enthält den aktuellen Status der Modularisierung und Dateigrößen in einer einzigen konsolidierten Liste sowie strukturierte Prompts für anstehende Refactoring-Pakete.

---

## 📋 Status der Refactoring-Pakete & Dateigrößen

- **Paket 1 (`SettingsView.kt`)**: ✅ **Erledigt** – Auf 81 Zeilen reduziert (`ui/views/settings/`).
- **Paket 2 (`DocAnizerViewModel.kt`)**: ✅ **Erledigt** – Modularisiert mit Fassaden-Pattern (`DmsManager.kt`, `FinanceManager.kt`, `SyncManager.kt`).
- **Paket 3 (`SetupWizardDialog.kt`)**: ✅ **Erledigt** – Auf 320 Zeilen reduziert (`ui/views/wizard/`).
- **Paket 4 (`LlmService.kt`)**: ✅ **Erledigt** – Modularisiert mit `LlmPrompts.kt`, `LlmToolExecutor.kt` & `LlmHardwareDetector.kt`.
- **Paket 5 (Audit-Logger & Diagnose)**: ✅ **Erledigt** – `AppAuditLogger.kt` mit Log-Leveln und Settings-Integration.
- **Paket 6 (`DmsExplorerView.kt`)**: ✅ **Erledigt** – Von 3.342 auf 1.977 Zeilen reduziert (`ui/views/dms/`: `DmsTrashPane`, `DmsDeadlinesPane`, `DmsCustomFieldsPane`, `DmsFolderNavPane`, `DmsDocumentListView`, `DmsFilterBar`, `DmsBatchActionToolbar`).
- **Paket 7 & 19 (`DashboardView.kt`)**: ✅ **Erledigt** – Von 1.793 auf 742 Zeilen reduziert (`ui/views/dashboard/`: `DashboardKpiWidget`, `DashboardQuickScanWidget`, `DashboardCashTrackerWidget`, `DashboardDeadlinesWidget`, `DashboardQuickNoteWidget`, `DashboardCategoryPieWidget`, `DashboardCustomFieldsWidget`).
- **Paket 8 (`DashboardCustomWidgetsUi.kt`)**: ✅ **Erledigt** – Auf 155 Zeilen reduziert (`ui/views/dashboard/custom/`: `CustomWidgetRenderer`, `CustomWidgetEditorDialog`, `CustomWidgetTypesUi`).
- **Paket 9 (`RulesAndAiSettingsView.kt`)**: ✅ **Erledigt** – Auf 434 Zeilen reduziert (`ui/views/rules/`: `RuleListAndEditorPane`, `AiProviderConfigPane`, `RuleTestingDialog`).
- **Paket 10 (`ScannerView.kt`)**: ✅ **Erledigt** – Auf 593 Zeilen reduziert (`ui/views/scanner/`: `ScannerCameraPreviewPane`, `ScannerCropOverlayPane`, `ScannerPageReviewPane`).
- **Paket 11 (`CloudStep.kt`)**: ✅ **Erledigt** – Von 1.195 auf 365 Zeilen reduziert (`ui/views/wizard/cloud/`: `BackupSafetyScoreBanner`, `BackupTierUsbCard`, `BackupTierDriveCard`, `BackupTierNasCard`, `BackupTierMultiDeviceCard`).
- **Paket 12 (`P2pSyncTab.kt`)**: ✅ **Erledigt** – Von 1.010 auf 139 Zeilen reduziert (`ui/views/p2p/`: `P2pInfoBanner`, `P2pServerStatusCard`, `P2pConflictAndProgressCard`, `P2pPairedDevicesList`, `PairingWizardDialog`).
- **Paket 13 (`MainScreen.kt`)**: ✅ **Erledigt** – Von 1.194 auf 610 Zeilen reduziert (`ui/navigation/`: `MainTopAppBar`, `MainBottomBar`, `FabSpeedDialOverlay`, `ScanImportBottomSheet`, `MainGestureZones`).
- **Paket 14 (`AiDocumentChatDrawer.kt`)**: ✅ **Erledigt** – Von 994 auf 566 Zeilen reduziert (`ui/chat/`: `ChatMessageListPane`, `ChatInputBarPane`, `ChatContextChipsPane`).
- **Paket 15 (`PcCompanionWizardDialog.kt`)**: ✅ **Erledigt** – Von 979 auf 254 Zeilen reduziert (`ui/views/p2p/companion/`: `Step1SelectConnectionType`, `Step2ConfigureConnection`, `Step3ConfigureDestination`, `Step4SummaryAndTest`).
- **Paket 16 (`ReconciliationDialog.kt`)**: ✅ **Erledigt** – Von 898 auf 574 Zeilen reduziert (`ui/views/reconciliation/`: `ReconciliationFilterBarPane`, `ReconciliationMatchCardPane`, `AddCashTransactionDialog`, `AddBankWithdrawalDialog`, `AddPrivateWithdrawalDialog`).
- **Paket 17 (`DocumentDetailModal.kt`)**: ✅ **Erledigt** – Von 896 auf 843 Zeilen reduziert (`ui/views/detail/`: `DocumentMetadataEditorPane`, `DocumentOcrPreviewPane`).
- **Paket 18 (`ImportView.kt`)**: ✅ **Erledigt** – Von 884 auf 558 Zeilen reduziert (`ui/views/import/`: `ImportFilePickerPane`, `ImportMetadataFormPane`, `ImportOcrAndFormatPane`).

---

## 🛠️ Status: Alle geplanten Refactoring-Pakete erfolgreich abgeschlossen!
