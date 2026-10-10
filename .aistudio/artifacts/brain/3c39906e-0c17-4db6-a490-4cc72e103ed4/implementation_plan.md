# Ursachenanalyse & Wiederherstellungsplan: Settings & Datensicherung

## 1. Antworten auf deine konkreten Fragen

### Frage 1: Wurde der Code / die Geschäftslogik der Datensicherung geändert oder gelöscht?
**Nein, absolut nicht.** Der Code im Backend und im ViewModel ist zu 100% intakt.
- **Verschlüsseltes Backup & Wiederherstellung** (`DocumentStorageService.kt` -> `createEncryptedBackupArchive` / `restoreEncryptedBackupArchive`): Vollständig funktionsfähig.
- **USB-Stick OTG-Datensicherung** (`SyncManager.kt` -> `usbBackupDrives`, `addUsbBackupDrive`): Vollständig vorhanden.
- **3-2-1 Backup Health Score** (`DocAnizerViewModel.kt` -> `backupSyncHealth`): Berechnung läuft aktiv.
- **P2P & PC-Companion Network Engine** (`P2pSyncManager.kt`, `P2pSyncServer.kt`): Vollständig vorhanden.

---

### Frage 2: Was ist exakt schiefgelaufen?
Es handelte sich rein um ein **UI-Einbindungs-Problem in `SettingsView.kt` und `CloudSyncSettingsTab.kt`**:
1. **Fehlender Tab `WLAN & P2P Sync`**: Die Datei `P2pSyncTab.kt` war im Projekt vorhanden, wurde aber in `SettingsView.kt` in der Tab-Liste nicht aufgeführt. Dadurch war der P2P/PC-Sync in den Einstellungen nicht mehr erreichbar.
2. **Unvollständiger Tab `Cloud & Backup`**: In `CloudSyncSettingsTab.kt` wurden nur WebDAV und der manuelle Export gerendert. Die vorgefertigten Backup-Karten für **USB-Sticks (`BackupTierUsbCard`)**, **Google Drive (`BackupTierDriveCard`)** und das **Backup Safety Banner (`BackupSafetyScoreBanner`)** wurden im Einstellungs-Tab nicht mit gerendert.

---

### Frage 3: Wie war es ursprünglich und wie wird es exakt wiederhergestellt?
Wie auf deinem Screenshot sichtbar, wird die Einstellungen-Struktur exakt wie ursprünglich aufgebaut:

1. **Tab `Sicherheit`**:
   - Biometrische Entsperrung, PIN, App-Sperre, Master-Key.
2. **Tab `WLAN & P2P Sync`**:
   - Einbindung von `P2pSyncTab.kt` (Geräte-Kopplung, PC-Companion Assistent, P2P-Server An/Aus, Konfliktlösung).
3. **Tab `Cloud & Backup`**:
   - Integration der vollständigen 4-Stufen-Datensicherung in `CloudSyncSettingsTab.kt`:
     - **Safety Score Banner**: Visualisierung der Datensicherheit nach der 3-2-1 Regel.
     - **Stufe 1 (USB-Stick Backup)**: USB-Sticks verwalten, OTG-Warnung.
     - **Stufe 2 (Google Drive Cloud-Tresor)**: Verschlüsseltes Cloud-Backup.
     - **Stufe 3 (WebDAV / NAS)**: Eigene Nextcloud/Synology Server-Einstellungen & Verbindungstest.
     - **1-Klick Archiv-Export & Wiederherstellung**: Verschlüsselte `.enc`-Dateien erstellen und einlesen.
     - **Master-Passwort**: Hauptschlüssel verwalten.
4. **Tab `Design`**:
   - Erscheinungsbild, Dynamic Color, Schriftgrößen, Chatbot-Gesten (`AppearanceSettingsTab.kt`).
5. **Erweiterte Ansicht (Regeln, Zusatzfelder, KI Agent, Module, Entwickler)**:
   - Bleiben in den oberen Ansichts-Leveln (Erweitert / Experte / Assistenten) nahtlos erreichbar.

---

## 2. Umsetzungs-Schritte

1. **`SettingsView.kt` anpassen**:
   - Tab-Liste um `SettingsTabItem("WLAN & P2P Sync", Icons.Default.Devices)` erweitern und `P2pSyncTab(viewModel)` aufrufen.
   - Tab-Titel "Cloud & Sync" zu "Cloud & Backup" umbenennen.

2. **`CloudSyncSettingsTab.kt` erweitern**:
   - Einbinden der Bausteine `BackupSafetyScoreBanner`, `BackupTierUsbCard`, `BackupTierDriveCard`, `BackupTierNasCard`.
   - Verknüpfung der USB-Stick Verwaltung und Drive-Anbindung mit den entsprechenden `DocAnizerViewModel`-States.

3. **Verifizierung (Mandatory Test Gate)**:
   - **Kompilierung**: `compile_applet` durchführen.
   - **Robolectric & JVM-Unit-Tests**: `gradle :app:testDebugUnitTest` ausführen, um fehlerfreien Start, ViewModel-Datenfluss und UI-Rendering zu garantieren.
