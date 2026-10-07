package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.model.DocumentEntity
import com.example.model.TemplateItem
import com.example.service.DocumentStorageService
import com.example.service.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * DebugDataSeeder & DummyDataFactory
 * 
 * Generiert realistische Testdokumente, Metadaten, Ordnerstrukturen und echte PDF-Dateien
 * für Emulator-, Preview- und Sandbox-Tests.
 * 
 * SICHERHEIT / RELEASE-SCHUTZ:
 * Der gesamte Seeder-Code ist strikt mit `if (BuildConfig.DEBUG)` abgesichert.
 * In Release-Builds wird keine Aktion ausgeführt.
 */
object DebugDataSeeder {

    data class MockDocumentSpec(
        val title: String,
        val sender: String,
        val mainCategory: String,
        val subCategory: String,
        val mainCategoryId: String,
        val subCategoryId: String,
        val docType: String,
        val tags: String,
        val amount: Double?,
        val contractEndDate: Long?,
        val cancellationDeadline: Long?,
        val companyLogo: String,
        val customIcon: String,
        val ocrText: String,
        val daysAgo: Int = 0
    )

    /**
     * DummyDataFactory mit 12 realistischen Dokumenten aus Finanzen, Verträgen, Gesundheit & Wohnen.
     */
    val DUMMY_DOCUMENTS: List<MockDocumentSpec> = listOf(
        // 1. Vodafone Mobilfunkrechnung
        MockDocumentSpec(
            title = "Vodafone Mobilfunkrechnung 03/2026 - GigaMobil M",
            sender = "Vodafone GmbH",
            mainCategory = "Verträge",
            subCategory = "Telekommunikation",
            mainCategoryId = "A02",
            subCategoryId = "B2.01",
            docType = "Fixkosten & Verträge",
            tags = "#vodafone, #mobilfunk, #vertrag, #rechnung, #telekommunikation",
            amount = 39.99,
            contractEndDate = 1798761600000L, // 31.12.2026
            cancellationDeadline = 1796083200000L, // 30.11.2026
            companyLogo = "vodafone",
            customIcon = "phone_android",
            daysAgo = 2,
            ocrText = """
                Vodafone GmbH - Ferdinand-Braun-Platz 1, 40549 Düsseldorf
                RECHNUNG & EINZELVERBINDUNGSNACHWEIS
                Rechnungsnummer: VF-2026-8893412
                Kundennummer: 0019482019
                Rechnungsdatum: 15.03.2026
                Abrechnungszeitraum: 01.02.2026 - 28.02.2026
                
                Tarif: Vodafone GigaMobil M mit 5G & Allnet Flat
                Monatlicher Grundpreis: 33,61 €
                Optionen / Roaming: 0,00 €
                Mehrwertsteuer (19%): 6,38 €
                Gesamtbetrag fällig zum 25.03.2026: 39,99 €
                
                Zahlungsart: SEPA-Lastschrift von IBAN DE89 7001 0080 0987 6543 21
                Vertragslaufzeit bis: 31.12.2026 | Kündigungsfrist: 1 Monat zum Laufzeitende
            """.trimIndent()
        ),

        // 2. Allianz Versicherungsschein
        MockDocumentSpec(
            title = "Versicherungsschein Privathaftpflicht Komfort",
            sender = "Allianz Versicherungs-AG",
            mainCategory = "Verträge",
            subCategory = "Versicherungen",
            mainCategoryId = "A02",
            subCategoryId = "B2.02",
            docType = "Fixkosten & Verträge",
            tags = "#allianz, #versicherung, #haftpflicht, #police, #schutz",
            amount = 74.50,
            contractEndDate = 1806537600000L, // 01.04.2027
            cancellationDeadline = 1801267200000L, // 01.01.2027
            companyLogo = "allianz",
            customIcon = "shield",
            daysAgo = 5,
            ocrText = """
                Allianz Versicherungs-AG - Königinstraße 28, 80802 München
                VERSICHERUNGSSCHEIN / POLICE
                Versicherungsschein-Nr.: AZ-HP-2024-998231
                Versicherungsnehmer: Max Mustermann
                Produkt: Allianz Privat-Haftpflicht Komfort inkl. Mietsachschäden
                Deckungssumme: 50.000.000 EUR für Personen-, Sach- und Vermögensschäden
                Selbstbeteiligung: 0,00 EUR
                
                Jahresbeitrag (inkl. 19% VersSt): 74,50 EUR
                Hauptfälligkeit: 01.04. jährlich
                Kündigungsfrist: 3 Monate vor Ablauf des Versicherungsjahres
            """.trimIndent()
        ),

        // 3. Stadtwerke Jahresabrechnung Energie
        MockDocumentSpec(
            title = "SWM Jahresabrechnung 2025/2026 M-Strom & M-Erdgas",
            sender = "Stadtwerke München GmbH",
            mainCategory = "Finanzen",
            subCategory = "Energie & Strom",
            mainCategoryId = "A01",
            subCategoryId = "B1.02",
            docType = "Rechnungen & Belege",
            tags = "#strom, #stadtwerke, #energie, #jahresabrechnung, #gas",
            amount = 842.10,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "swm",
            customIcon = "bolt",
            daysAgo = 8,
            ocrText = """
                Stadtwerke München GmbH - Emmy-Noether-Straße 2, 80992 München
                JAHRESABRECHNUNG ENERGIE
                Vertragskontonummer: 4002819401
                Zählernummer Strom: 1SML72819401 (Stand: 3.420 kWh)
                Zählernummer Gas: 7GAS991204 (Stand: 11.200 kWh)
                Abrechnungszeitraum: 01.03.2025 - 28.02.2026
                
                Geleistete Abschlagszahlungen: 800,00 €
                Tatsächlicher Jahresverbrauch: 842,10 €
                Ihre Nachzahlung beträgt: 42,10 €
                Neuer monatlicher Abschlag ab 01.04.2026: 72,00 €
            """.trimIndent()
        ),

        // 4. Deutsche Bank Quartalsauszug
        MockDocumentSpec(
            title = "Depotauszug & Vermögensaufstellung Q1/2026",
            sender = "Deutsche Bank AG",
            mainCategory = "Finanzen",
            subCategory = "Banken & Konten",
            mainCategoryId = "A01",
            subCategoryId = "B1.01",
            docType = "Kontoauszug",
            tags = "#bank, #depot, #aktien, #etf, #finanzen, #auszug",
            amount = 1250.00,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "deutsche_bank",
            customIcon = "account_balance",
            daysAgo = 12,
            ocrText = """
                Deutsche Bank AG - Taunusanlage 12, 60325 Frankfurt am Main
                QUARTALSDEPOTAUSZUG ZUM 31.03.2026
                Depotnummer: 450 9182 00
                Inhaber: Max Mustermann
                
                Wertpapierbestand:
                - 50x Vanguard FTSE All-World UCITS ETF (A1JX52) Kurs: 124,50 € Wert: 6.225,00 €
                - 20x iShares Core MSCI World (A0RPWH) Kurs: 91,20 € Wert: 1.824,00 €
                Quartalsdividende gutgeschrieben: 125,40 €
                Gesamtdepotwert zum Stichtag: 14.850,20 €
                Verrechnungskonto Stand: 1.250,00 €
            """.trimIndent()
        ),

        // 5. Helios Klinikum MRT Befund
        MockDocumentSpec(
            title = "Radiologischer MRT-Befund Lendenwirbelsäule & Arztbrief",
            sender = "Helios Klinikum München West",
            mainCategory = "Gesundheit",
            subCategory = "Befunde & Berichte",
            mainCategoryId = "A03",
            subCategoryId = "B3.01",
            docType = "Gesundheitsdokument",
            tags = "#gesundheit, #mrt, #arztbrief, #befund, #radiologie, #klinik",
            amount = null,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "helios",
            customIcon = "medical_services",
            daysAgo = 16,
            ocrText = """
                Helios Klinikum München West - Abteilung für Radiologie & Orthopädie
                Steinerweg 5, 81241 München - Chefarzt Dr. med. Thomas Weber
                ARZTBRIEF / RADIOLOGISCHER BEFUNDBERICHT
                Patient: Max Mustermann, geb. 14.08.1988
                Untersuchungsdatum: 12.02.2026
                Klinische Angaben: Lumboischialgie linksseitig, Verdacht auf Bandscheibenvorfall L5/S1
                
                Sequenzen: T1/T2 sagittal, T2 axial.
                Beurteilung: Regelrechte Knochenbinnenstruktur. Kein frischer Wirbelkörperkollaps.
                Leichte Bandscheibenprotrusion L4/L5 ohne signifikante Spinalkanalstenose.
                Freie Neuroforamina. Konservative Physiotherapie empfohlen.
            """.trimIndent()
        ),

        // 6. HUK Coburg KFZ-Versicherung
        MockDocumentSpec(
            title = "KFZ-Versicherungsrechnung 2026 - BMW 320d (M-AB 1234)",
            sender = "HUK-COBURG",
            mainCategory = "Verträge",
            subCategory = "KFZ & Mobilität",
            mainCategoryId = "A02",
            subCategoryId = "B2.03",
            docType = "Fixkosten & Verträge",
            tags = "#kfz, #auto, #versicherung, #huk, #vollkasko, #beitrag",
            amount = 389.00,
            contractEndDate = 1798761600000L, // 31.12.2026
            cancellationDeadline = 1796083200000L, // 30.11.2026 (Stichtag!)
            companyLogo = "huk",
            customIcon = "directions_car",
            daysAgo = 20,
            ocrText = """
                HUK-COBURG Versicherungen - Bahnhofsplatz, 96440 Coburg
                BEITRAGSRECHNUNG KRAFTFAHRT-VERSICHERUNG 2026
                Versicherungsschein: 12-345-678-901
                Amtliches Kennzeichen: M-AB 1234 (BMW 320d Touring)
                Schadenfreiheitsklasse: SF 12 (Haftpflicht) / SF 12 (Kasko)
                
                Haftpflicht Komfort: 100 Mio. € pauschal
                Vollkasko 300 € SB inkl. Teilkasko 150 € SB
                Gesamtjahresbeitrag: 389,00 €
                Fälligkeit: 01.01.2026 | Kündigungsstichtag: 30.11.2026
            """.trimIndent()
        ),

        // 7. BARMER Bonusprogramm Bescheinigung
        MockDocumentSpec(
            title = "Bescheinigung über gezahlte Vorsorgebeiträge & Bonusgutschrift",
            sender = "BARMER Ersatzkasse",
            mainCategory = "Gesundheit",
            subCategory = "Krankenkasse & Vorsorge",
            mainCategoryId = "A03",
            subCategoryId = "B3.02",
            docType = "Steuerbeleg & Nachweis",
            tags = "#krankenkasse, #barmer, #bonus, #vorsorge, #nachweis, #steuern",
            amount = 120.00,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "barmer",
            customIcon = "health_and_safety",
            daysAgo = 25,
            ocrText = """
                BARMER - Lichtscheider Str. 89, 42285 Wuppertal
                MITGLIEDER-BESCHEINIGUNG FÜR DAS FINANZAMT & BONUSPROGRAMM
                Versichertennummer: X120938475
                Versicherter: Max Mustermann
                
                Bestätigung über die Teilnahme am BARMER Bonusprogramm 2025:
                - Zahnvorsorge durchgeführt (+20 €)
                - Sportverein-Nachweis erbracht (+50 €)
                - Gesundheits-Check-Up durchgeführt (+50 €)
                Gesamtprämie ausgezahlt: 120,00 € auf Girokonto
                Gezahlte Kranken- und Pflegeversicherungsbeiträge 2025 steuerlich absetzbar.
            """.trimIndent()
        ),

        // 8. Telekom Glasfaser Internetvertrag
        MockDocumentSpec(
            title = "Telekom Glasfaser 250 Auftragsbestätigung & Tarifdetails",
            sender = "Telekom Deutschland GmbH",
            mainCategory = "Verträge",
            subCategory = "Telekommunikation",
            mainCategoryId = "A02",
            subCategoryId = "B2.01",
            docType = "Fixkosten & Verträge",
            tags = "#telekom, #glasfaser, #internet, #router, #magentatv, #vertrag",
            amount = 49.95,
            contractEndDate = 1811808000000L, // 01.06.2027
            cancellationDeadline = 1809129600000L, // 01.05.2027
            companyLogo = "telekom",
            customIcon = "wifi",
            daysAgo = 30,
            ocrText = """
                Telekom Deutschland GmbH - Landgrabenweg 151, 53227 Bonn
                AUFTRAGSBESTÄTIGUNG ZUM TELEKOM GLASFASER-VERTRAG
                Auftragsnummer: 8092817401
                Kundennummer: 3019827361
                Produkt: MagentaZuhause XL mit Glasfaser 250 MBit/s Download & 50 MBit/s Upload
                Inklusive MagentaTV Smart & Router Speedport Smart 4
                
                Monatlicher Grundpreis: 49,95 €
                Bereitstellungstermin: 01.06.2025 | Mindestvertragslaufzeit: 24 Monate
                Kündigungsfrist: 1 Monat zum Laufzeitende (danach monatlich kündbar)
            """.trimIndent()
        ),

        // 9. Amazon Hardware Rechnung
        MockDocumentSpec(
            title = "Amazon Rechnung INV-DE-2026-991204 - Apple MacBook Air",
            sender = "Amazon EU S.à r.l.",
            mainCategory = "Finanzen",
            subCategory = "Rechnungen & Quittungen",
            mainCategoryId = "A01",
            subCategoryId = "B1.03",
            docType = "Rechnungen & Belege",
            tags = "#amazon, #hardware, #elektronik, #rechnung, #apple, #quittung",
            amount = 1199.00,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "amazon",
            customIcon = "shopping_bag",
            daysAgo = 35,
            ocrText = """
                Amazon EU S.à r.l. - 38 avenue John F. Kennedy, L-1855 Luxembourg
                USt-IdNr.: DE 814588194
                RECHNUNG Nr. INV-DE-2026-991204
                Bestelldatum: 18.01.2026 | Lieferdatum: 20.01.2026
                
                Artikel:
                1x Apple MacBook Air 15" M3 Chip 16GB RAM 512GB SSD Spacegrau - 1.007,56 € netto
                Zwischensumme: 1.007,56 €
                MwSt. (19%): 191,44 €
                Rechnungsbetrag gesamt: 1.199,00 €
                Zahlungsart: VISA Kreditkarte (Kreditkartenabrechnung)
            """.trimIndent()
        ),

        // 10. Hausverwaltung Nebenkostenabrechnung
        MockDocumentSpec(
            title = "Nebenkostenabrechnung 2025 Wohnung 3. OG links",
            sender = "Hausverwaltung Residenz GmbH",
            mainCategory = "Wohnung",
            subCategory = "Nebenkosten & Heizung",
            mainCategoryId = "A04",
            subCategoryId = "B4.01",
            docType = "Mietdokument & Nebenkosten",
            tags = "#wohnung, #miete, #nebenkosten, #heizung, #hausverwaltung, #abrechnung",
            amount = 215.30,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "residenz",
            customIcon = "apartment",
            daysAgo = 40,
            ocrText = """
                Hausverwaltung Residenz GmbH - Maximilianstraße 40, 80539 München
                JAHRES-NEBENKOSTENABRECHNUNG FÜR DEN ZEITRAUM 01.01.2025 - 31.12.2025
                Mietobjekt: Leopoldstraße 120, 80802 München, Whg. Nr. 14 (3. OG links, 78 qm)
                Mieter: Max Mustermann
                
                Umlagefähige Betriebskosten:
                - Heizung und Warmwasseraufbereitung: 980,40 €
                - Kaltwasser & Abwasser: 240,10 €
                - Müllabfuhr & Straßenreinigung: 185,60 €
                - Hausmeister, Hausreinigung & Gartenpflege: 310,20 €
                - Gebäudeversicherung & Haftpflicht: 199,00 €
                Gesamtkosten Anteil Mieter: 1.915,30 €
                Geleistete Vorauszahlungen 2025: 1.700,00 €
                Nachzahlungsbetrag fällig bis 30.04.2026: 215,30 €
            """.trimIndent()
        ),

        // 11. Finanzamt Einkommensteuerbescheid
        MockDocumentSpec(
            title = "Einkommensteuerbescheid 2024 - Erstattungsguthaben",
            sender = "Finanzamt München",
            mainCategory = "Finanzen",
            subCategory = "Steuern & Abgaben",
            mainCategoryId = "A01",
            subCategoryId = "B1.04",
            docType = "Steuerbescheid",
            tags = "#finanzamt, #steuern, #einkommensteuer, #bescheid, #erstattung",
            amount = 450.00,
            contractEndDate = null,
            cancellationDeadline = null,
            companyLogo = "finanzamt",
            customIcon = "account_balance",
            daysAgo = 50,
            ocrText = """
                Finanzamt München - Deroystraße 6, 80335 München
                Steuernummer: 143/892/01928
                BESCHEID FÜR 2024 ÜBER EINKOMMENSTEUER UND SOLIDARITÄTSZUSCHLAG
                Zu versteuerndes Einkommen: 48.200 €
                Festgesetzte Einkommensteuer: 9.850 €
                Abzug durch Lohnsteuer: 10.300 €
                Erstattungsguthaben zu Ihren Gunsten: 450,00 €
                
                Das Guthaben wird auf das angegebene Girokonto überwiesen.
                Rechtsbehelfsbelehrung: Gegen diesen Bescheid kann innerhalb eines Monats Einspruch eingelegt werden.
            """.trimIndent()
        ),

        // 12. Fitness First Mitgliedsvertrag
        MockDocumentSpec(
            title = "Fitnessstudio Mitgliedsvertrag Platinum Club & Wellness",
            sender = "Fitness First Germany GmbH",
            mainCategory = "Verträge",
            subCategory = "Freizeit & Sport",
            mainCategoryId = "A02",
            subCategoryId = "B2.04",
            docType = "Fixkosten & Verträge",
            tags = "#fitness, #vertrag, #sport, #abo, #wellness, #mitgliedschaft",
            amount = 44.90,
            contractEndDate = 1798761600000L, // 31.01.2027
            cancellationDeadline = 1796083200000L, // 31.12.2026
            companyLogo = "fitness_first",
            customIcon = "fitness_center",
            daysAgo = 60,
            ocrText = """
                Fitness First Germany GmbH - Hanauer Landstraße 148, 60314 Frankfurt am Main
                MITGLIEDSVEREINBARUNG & STUDIOVERTRAG
                Mitgliedsnummer: FF-9918274
                Tarif: Platinum All-Access inklusive Spa, Sauna & Pool
                Monatlicher Beitrag: 44,90 € (Abbuchung jeweils zum 1. des Monats)
                
                Vertragsbeginn: 01.02.2025 | Erstlaufzeit: 24 Monate bis 31.01.2027
                Kündigungsfrist: 1 Monat vor Ablauf der Erstlaufzeit.
                SEPA-Lastschriftmandat Gläubiger-ID: DE92ZZZ00001928371
            """.trimIndent()
        )
    )

    /**
     * Hauptmethode: Führt das Seeding von Room-DB und Sandbox-Dateien aus.
     * Streng geschützt mit `if (!BuildConfig.DEBUG) return 0`.
     */
    suspend fun seedDatabaseAndFiles(
        context: Context,
        database: AppDatabase,
        settingsRepo: SettingsRepository
    ): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) {
            return@withContext 0
        }

        val docDao = database.documentDao()

        // 1. Vorhandene Dokumente leeren
        val existingDocs = docDao.getAllDocumentsList()
        for (doc in existingDocs) {
            docDao.deleteDocument(doc)
            try {
                val f = File(doc.filePath)
                if (f.exists()) f.delete()
            } catch (_: Exception) {}
        }

        // 2. Ordner-Vorlagen in SettingsRepository initialisieren / sicherstellen
        val requiredTemplates = listOf(
            TemplateItem("tmpl_fin_bank", "Finanzen", "Banken & Konten", "A01", "B1.01", "Kontoauszug", "Deutsche Bank", defaultTags = listOf("bank", "konto")),
            TemplateItem("tmpl_fin_strom", "Finanzen", "Energie & Strom", "A01", "B1.02", "Rechnungen & Belege", "Stadtwerke", defaultTags = listOf("strom", "energie")),
            TemplateItem("tmpl_fin_rechnung", "Finanzen", "Rechnungen & Quittungen", "A01", "B1.03", "Rechnungen & Belege", "Händler", defaultTags = listOf("rechnung", "quittung")),
            TemplateItem("tmpl_fin_steuer", "Finanzen", "Steuern & Abgaben", "A01", "B1.04", "Steuerbescheid", "Finanzamt", defaultTags = listOf("steuern", "finanzamt")),
            TemplateItem("tmpl_vert_telekom", "Verträge", "Telekommunikation", "A02", "B2.01", "Fixkosten & Verträge", "Telekom", defaultTags = listOf("telekom", "vertrag")),
            TemplateItem("tmpl_vert_vers", "Verträge", "Versicherungen", "A02", "B2.02", "Fixkosten & Verträge", "Allianz", defaultTags = listOf("versicherung", "allianz")),
            TemplateItem("tmpl_vert_kfz", "Verträge", "KFZ & Mobilität", "A02", "B2.03", "Fixkosten & Verträge", "HUK-COBURG", defaultTags = listOf("kfz", "auto")),
            TemplateItem("tmpl_vert_sport", "Verträge", "Freizeit & Sport", "A02", "B2.04", "Fixkosten & Verträge", "Fitness First", defaultTags = listOf("fitness", "sport")),
            TemplateItem("tmpl_ges_befunde", "Gesundheit", "Befunde & Berichte", "A03", "B3.01", "Gesundheitsdokument", "Klinikum", defaultTags = listOf("befund", "gesundheit")),
            TemplateItem("tmpl_ges_kasse", "Gesundheit", "Krankenkasse & Vorsorge", "A03", "B3.02", "Steuerbeleg & Nachweis", "BARMER", defaultTags = listOf("krankenkasse", "barmer")),
            TemplateItem("tmpl_wohn_neben", "Wohnung", "Nebenkosten & Heizung", "A04", "B4.01", "Mietdokument & Nebenkosten", "Hausverwaltung", defaultTags = listOf("wohnung", "nebenkosten"))
        )

        val currentTemplates = settingsRepo.templates.value.toMutableList()
        for (tmpl in requiredTemplates) {
            if (currentTemplates.none { it.mainCategory == tmpl.mainCategory && it.subCategory == tmpl.subCategory }) {
                currentTemplates.add(tmpl)
            }
        }
        settingsRepo.saveTemplates(currentTemplates)

        // 3. Dokumente & echte PDF-Dateien generieren
        var count = 0
        val now = System.currentTimeMillis()

        for (spec in DUMMY_DOCUMENTS) {
            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = context,
                mainCategory = spec.mainCategory,
                subCategory = spec.subCategory,
                mainCategoryId = spec.mainCategoryId,
                subCategoryId = spec.subCategoryId,
                useIdPrefixes = true,
                folderPrefixStyle = "AKTENPLAN_STANDARD",
                locationType = "APP_STORAGE",
                customPath = ""
            )

            val creationTime = now - (spec.daysAgo.toLong() * 86400000L)
            val fileName = DocumentStorageService.formatFileName(
                sender = spec.sender,
                title = spec.title,
                date = Date(creationTime),
                extension = "pdf",
                namingStyle = "DATE_SENDER_TITLE"
            )
            val pdfFile = File(targetDir, fileName)

            // Echte druckbare Dummy-PDF-Datei in der Sandbox erzeugen
            generateMockPdfFile(pdfFile, spec)

            val entity = DocumentEntity(
                title = spec.title,
                sender = spec.sender,
                fileName = fileName,
                filePath = pdfFile.absolutePath,
                mainCategory = spec.mainCategory,
                subCategory = spec.subCategory,
                mainCategoryId = spec.mainCategoryId,
                subCategoryId = spec.subCategoryId,
                docType = spec.docType,
                tags = spec.tags,
                ocrText = spec.ocrText,
                colorMode = "COLOR",
                pageCount = 1,
                fileSizeFormatted = "${(pdfFile.length() / 1024).coerceAtLeast(12)} KB",
                isEncrypted = false,
                isSynced = true,
                createdAt = creationTime,
                isDeleted = false,
                deletedAt = null,
                contractEndDate = spec.contractEndDate,
                cancellationDeadline = spec.cancellationDeadline,
                reminderDaysBefore = 14,
                hasCalendarReminder = spec.cancellationDeadline != null,
                calendarEventType = "BOTH",
                reminderNotes = if (spec.cancellationDeadline != null) "Automatische Erinnerung vor Ablauf der Kündigungsfrist" else "",
                amount = spec.amount,
                customIcon = spec.customIcon,
                companyLogo = spec.companyLogo
            )

            docDao.insertDocument(entity)
            count++
        }

        return@withContext count
    }

    /**
     * Erzeugt eine Liste realistischer Dokumente für eine gegebene Spezifikation (z.B. UserPersona).
     */
    suspend fun seedDocumentsForSpecs(
        context: Context,
        database: AppDatabase,
        specs: List<MockDocumentSpec>,
        sandboxSubdir: String = "persona_sandbox",
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<DocumentEntity> = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext emptyList()
        val docDao = database.documentDao()
        val targetDir = File(context.filesDir, sandboxSubdir).apply { mkdirs() }
        val createdEntities = mutableListOf<DocumentEntity>()
        val now = System.currentTimeMillis()

        specs.forEachIndexed { index, spec ->
            val uniqueSuffix = UUID.randomUUID().toString().take(6)
            val pdfFile = File(targetDir, "doc_${index + 1}_$uniqueSuffix.pdf")
            generateMockPdfFile(pdfFile, spec)

            val creationTime = now - (spec.daysAgo * 86400000L)
            val entity = DocumentEntity(
                title = spec.title,
                sender = spec.sender,
                fileName = pdfFile.name,
                filePath = pdfFile.absolutePath,
                mainCategory = spec.mainCategory,
                subCategory = spec.subCategory,
                mainCategoryId = spec.mainCategoryId,
                subCategoryId = spec.subCategoryId,
                docType = spec.docType,
                tags = spec.tags,
                ocrText = spec.ocrText,
                colorMode = "COLOR",
                pageCount = 1,
                fileSizeFormatted = "${(pdfFile.length() / 1024).coerceAtLeast(14)} KB",
                isEncrypted = false,
                isSynced = false,
                createdAt = creationTime,
                isDeleted = false,
                deletedAt = null,
                contractEndDate = spec.contractEndDate,
                cancellationDeadline = spec.cancellationDeadline,
                reminderDaysBefore = 14,
                hasCalendarReminder = spec.cancellationDeadline != null,
                calendarEventType = "BOTH",
                reminderNotes = if (spec.cancellationDeadline != null) "Automatische Frist-Erinnerung" else "",
                amount = spec.amount,
                customIcon = spec.customIcon,
                companyLogo = spec.companyLogo
            )
            docDao.insertDocument(entity)
            createdEntities.add(entity)
            onProgress?.invoke(index + 1, specs.size)
        }
        return@withContext createdEntities
    }

    /**
     * Erzeugt eine große Menge an Testdokumenten für Stress- & Performance-Tests.
     */
    suspend fun seedMassDocuments(
        context: Context,
        database: AppDatabase,
        count: Int
    ): Int = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext 0
        
        val docDao = database.documentDao()
        val now = System.currentTimeMillis()
        var created = 0
        
        for (i in 1..count) {
            val spec = DUMMY_DOCUMENTS[i % DUMMY_DOCUMENTS.size]
            val uniqueId = UUID.randomUUID().toString().take(6)
            val title = "StressTest #$i - ${spec.sender} ($uniqueId)"
            
            val targetDir = File(context.filesDir, "stress_test_sandbox").apply { mkdirs() }
            val pdfFile = File(targetDir, "stress_$i.pdf")
            
            // Minimal PDF für Speed
            writeMinimalPdfFallback(pdfFile, spec.copy(title = title))
            
            val entity = DocumentEntity(
                title = title,
                sender = spec.sender,
                fileName = pdfFile.name,
                filePath = pdfFile.absolutePath,
                mainCategory = spec.mainCategory,
                subCategory = spec.subCategory,
                mainCategoryId = spec.mainCategoryId,
                subCategoryId = spec.subCategoryId,
                docType = spec.docType,
                tags = "#stresstest, ${spec.tags}",
                ocrText = "STRESS TEST OCR CONTENT #$i\n" + spec.ocrText,
                createdAt = now - (i * 1000L), // Millisekunden Abstand
                isSynced = false,
                amount = (10..500).random().toDouble(),
                companyLogo = spec.companyLogo,
                customIcon = spec.customIcon
            )
            docDao.insertDocument(entity)
            created++
        }
        return@withContext created
    }

    /**
     * Erzeugt eine visuell ansprechende und standardkonforme PDF-Datei im Sandbox-Speicher.
     */
    fun generateMockPdfFile(targetFile: File, spec: MockDocumentSpec) {
        targetFile.parentFile?.mkdirs()
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // DIN A4
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Hintergrund
            val bgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            // Header Balken (Modernes Teal/Cyan)
            val headerPaint = Paint().apply {
                color = Color.rgb(8, 145, 178) // Cyan/Teal #0891B2
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, 595f, 55f, headerPaint)

            // Header Text
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("myDocAnizer Document Preview", 35f, 35f, headerTextPaint)

            // Absender & Titel
            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42) // Slate 900
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(spec.sender, 35f, 90f, titlePaint)

            val subTitlePaint = Paint().apply {
                color = Color.rgb(71, 85, 105) // Slate 600
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText(spec.title, 35f, 115f, subTitlePaint)

            // Trennlinie
            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240) // Slate 200
                strokeWidth = 1.5f
            }
            canvas.drawLine(35f, 130f, 560f, 130f, linePaint)

            // Metadaten Badge Box
            val boxPaint = Paint().apply {
                color = Color.rgb(241, 245, 249) // Slate 100
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(35f, 145f, 560f, 205f, 8f, 8f, boxPaint)

            val metaPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }
            canvas.drawText("Kategorie: ${spec.mainCategory} / ${spec.subCategory}  (${spec.mainCategoryId}/${spec.subCategoryId})", 45f, 168f, metaPaint)
            val amountStr = if (spec.amount != null) String.format(Locale.GERMANY, "%.2f €", spec.amount) else "Kein Betrag"
            canvas.drawText("Dokumenttyp: ${spec.docType}   |   Rechnungsbetrag: $amountStr", 45f, 190f, metaPaint)

            // OCR-Text Body (Multi-Line)
            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                isAntiAlias = true
            }

            var yPos = 235f
            val lines = spec.ocrText.lines()
            for (line in lines) {
                if (yPos > 780f) break
                canvas.drawText(line, 35f, yPos, bodyPaint)
                yPos += 18f
            }

            // Footer
            val footerPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            canvas.drawLine(35f, 800f, 560f, 800f, linePaint)
            canvas.drawText("Generiert von myDocAnizer DebugDataSeeder • 100% Offline App Sandbox", 35f, 820f, footerPaint)

            pdfDocument.finishPage(page)

            FileOutputStream(targetFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
        } catch (_: Throwable) {
            writeMinimalPdfFallback(targetFile, spec)
        }
    }

    private fun writeMinimalPdfFallback(targetFile: File, spec: MockDocumentSpec) {
        val content = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 120 >>\nstream\nBT\n/F1 12 Tf\n50 800 Td\n(${spec.sender} - ${spec.title}) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000201 00000 n \ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n340\n%%EOF"
        targetFile.writeText(content, Charsets.ISO_8859_1)
    }
}
