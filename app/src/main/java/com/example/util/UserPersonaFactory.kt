package com.example.util

import com.example.util.DebugDataSeeder.MockDocumentSpec

data class UserProfileSimulation(
    val id: String,
    val name: String,
    val roleTitle: String,
    val description: String,
    val iconType: String,
    val highlightTags: List<String>,
    val documentSpecs: List<MockDocumentSpec>
) {
    val docCount: Int get() = documentSpecs.size
    val rulesCount: Int get() = documentSpecs.map { it.mainCategory }.distinct().size
    val avgFieldsPerDoc: Int get() = 4
}

/**
 * UserPersonaFactory
 * 
 * Definiert lebensnahe, realistische Benutzerprofile (Personas) für Routine 4
 * (Dauereinsatz & Stress-Simulation).
 * 
 * Anstatt bloßer Dokumenten-Quantität werden für jedes Nutzerprofil
 * authentische Dokumente mit realistischen Absendern, OCR-Volltexten,
 * Kategorien, Beträgen, Fristen und Tags erzeugt.
 */
object UserPersonaFactory {

    val PERSONA_FAMILY = UserProfileSimulation(
        id = "family_weber",
        name = "Familie Weber",
        roleTitle = "Angestellter mit Familie & Haushalt",
        description = "Festangestellter Projektingenieur, verheiratet, 2 Kinder. Typischer deutscher Privathaushalt mit Lohnsteuer, Mietwohnung, Pkw, Stadtwerken, Versicherungen & Haushaltsausgaben.",
        iconType = "family",
        highlightTags = listOf("Gehalt", "Miete", "Stadtwerke", "Kfz-Versicherung", "Kita", "Einkommensteuer", "Haushalt"),
        documentSpecs = listOf(
            MockDocumentSpec(
                title = "Entgeltabrechnung 02/2026 - Siemens AG",
                sender = "Siemens AG",
                mainCategory = "Finanzen",
                subCategory = "Gehalt & Einkommen",
                mainCategoryId = "A01",
                subCategoryId = "B1.01",
                docType = "Gehaltsabrechnungen",
                tags = "#siemens, #gehalt, #entgelt, #steuerklasse3, #nettoauszahlung",
                amount = 3120.40,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "siemens",
                customIcon = "payments",
                daysAgo = 2,
                ocrText = """
                    Siemens AG - Otto-Hahn-Ring 6, 81739 München
                    ENTGELTABRECHNUNG FÜR DEN MONAT FEBRUAR 2026
                    Personalnummer: 00481920
                    Mitarbeiter: Markus Weber
                    Steuerklasse: III | Kinderfreibetrag: 2,0 | Konfession: kath.
                    Krankenkasse: Techniker Krankenkasse (1,2% Zusatzbeitrag)
                    
                    Bruttobezüge:
                    - Grundgehalt Tarif ERA 11: 4.850,00 €
                    - Leistungszulage: 242,50 €
                    Gesamtbrutto: 5.092,50 €
                    
                    Gesetzliche Abzüge:
                    - Lohnsteuer: 612,40 €
                    - Solidaritätszuschlag: 0,00 €
                    - Kirchensteuer: 48,99 €
                    - Krankenversicherung (AN): 402,31 €
                    - Rentenversicherung (AN): 473,60 €
                    - Arbeitslosenversicherung (AN): 66,20 €
                    - Pflegeversicherung (AN): 112,03 €
                    
                    Auszahlungsbetrag (Überweisung zum 28.02.2026): 3.120,40 €
                    Bankverbindung: Sparkasse München, IBAN: DE89 7005 0000 1234 5678 90
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Betriebs- & Heizkostenabrechnung 2025/2026",
                sender = "Hausverwaltung Stadt & Raum GmbH",
                mainCategory = "Wohnen",
                subCategory = "Mietnebenkosten",
                mainCategoryId = "A04",
                subCategoryId = "B4.01",
                docType = "Nebenkosten",
                tags = "#wohnung, #nebenkosten, #heizung, #hausverwaltung, #abrechnung",
                amount = 218.40,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "hausverwaltung",
                customIcon = "apartment",
                daysAgo = 5,
                ocrText = """
                    Stadt & Raum Hausverwaltungs GmbH - Leopoldstr. 102, 80802 München
                    BETRIEBS- UND HEIZKOSTENABRECHNUNG 2025
                    Mietobjekt: Gotthardstraße 45, 80686 München, 3. OG links (92 qm)
                    Mietvertrag Nr.: MV-2019-9402
                    Mieter: Markus und Stefanie Weber
                    Abrechnungszeitraum: 01.01.2025 - 31.12.2025
                    
                    Zusammenstellung der Betriebskosten:
                    - Heizung und Warmwasser (Ista Wärmemengenzähler): 1.140,20 €
                    - Kaltwasser / Abwasser: 342,10 €
                    - Hausreinigung & Gartenpflege: 280,00 €
                    - Müllabfuhr & Strassenreinigung: 198,50 €
                    - Grundsteueranteil: 215,60 €
                    - Gebäudeversicherung: 182,00 €
                    Gesamtkosten Mieter: 2.358,40 €
                    
                    Geleistete Vorauszahlungen (12 x 178,33 €): 2.140,00 €
                    Nachzahlung fällig zum 31.03.2026: 218,40 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "HUK-COBURG Kfz-Versicherung VW Touran Beitragsrechnung",
                sender = "HUK-COBURG Versicherung",
                mainCategory = "Verträge",
                subCategory = "Kfz & Mobilität",
                mainCategoryId = "A02",
                subCategoryId = "B2.03",
                docType = "Versicherungspolicen",
                tags = "#kfz, #versicherung, #huk, #vwtouran, #vollkasko, #auto",
                amount = 486.20,
                contractEndDate = 1798761600000L, // 31.12.2026
                cancellationDeadline = 1796083200000L, // 30.11.2026
                companyLogo = "huk",
                customIcon = "directions_car",
                daysAgo = 10,
                ocrText = """
                    HUK-COBURG - Bahnhofsplatz, 96450 Coburg
                    BEITRAGSRECHNUNG FÜR DIE KRAFTFAHRTVERSICHERUNG
                    Versicherungsschein-Nr.: KFZ-2021-893041
                    Fahrzeug: Volkswagen Touran 2.0 TDI (M-MW 2021)
                    
                    Haftpflichtversicherung:
                    - Schadenfreiheitsklasse: SF 14 (Beitragssatz: 32%)
                    - Beitrag: 194,10 €
                    
                    Kaskoversicherung (Vollkasko 300 € SB / Teilkasko 150 € SB):
                    - Schadenfreiheitsklasse: SF 12 (Beitragssatz: 36%)
                    - Beitrag: 292,10 €
                    
                    Jahresbeitrag Gesamt (01.01.2026 - 31.12.2026): 486,20 €
                    Fälligkeit: 01.01.2026 per SEPA-Lastschrift
                    Kündigungsfrist: 1 Monat zum 31.12. eines jeden Jahres (Stichtag: 30.11.)
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Stadtwerke München - Jahresrechnung M-Strom & M-Erdgas",
                sender = "Stadtwerke München GmbH",
                mainCategory = "Finanzen",
                subCategory = "Energie & Strom",
                mainCategoryId = "A01",
                subCategoryId = "B1.02",
                docType = "Rechnungen & Belege",
                tags = "#swm, #stadtwerke, #strom, #gas, #energie, #jahresrechnung",
                amount = 145.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "swm",
                customIcon = "bolt",
                daysAgo = 14,
                ocrText = """
                    Stadtwerke München GmbH - Emmy-Noether-Straße 2, 80992 München
                    JAHRESABRECHNUNG ENERGIE 2025/2026
                    Vertragskontonummer: 4001928371
                    Zähler Strom (HT): 1SML74921401 - Verbrauch: 3.240 kWh
                    Zähler Gas: 7GAS994102 - Verbrauch: 9.850 kWh
                    
                    Abrechnungsergebnis:
                    - Gesamtverbrauch Strom & Gas: 1.710,00 €
                    - Geleistete Abschlagszahlungen: 1.680,00 €
                    - Nachzahlungsbetrag: 30,00 €
                    
                    Neuer monatlicher Abschlag ab 01.04.2026: 145,00 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Telekom Deutschland - Monatsabrechnung MagentaZuhause XL",
                sender = "Telekom Deutschland GmbH",
                mainCategory = "Verträge",
                subCategory = "Telekommunikation",
                mainCategoryId = "A02",
                subCategoryId = "B2.01",
                docType = "Rechnungen & Belege",
                tags = "#telekom, #internet, #dsl, #glasfaser, #vertrag",
                amount = 54.95,
                contractEndDate = 1790726400000L, // 30.09.2026
                cancellationDeadline = 1788134400000L, // 31.08.2026
                companyLogo = "telekom",
                customIcon = "router",
                daysAgo = 18,
                ocrText = """
                    Telekom Deutschland GmbH - Landgrabenweg 151, 53227 Bonn
                    RECHNUNG TELEKOM FESTNETZ & INTERNET
                    Buchungskonto: 2948201948
                    Rechnungsnummer: 9948201940
                    Rechnungsdatum: 12.02.2026
                    
                    Tarif: MagentaZuhause XL mit bis zu 250 MBit/s Download
                    - Monatlicher Grundpreis: 46,18 €
                    - Mietgerät Speedport Smart 4: 5,84 €
                    - Umsatzsteuer 19%: 8,77 €
                    Gesamtbetrag fällig: 54,95 €
                    Kündigungsfrist: 1 Monat zum Laufzeitende (30.09.2026)
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Allianz Privat-Haftpflicht & Hausrat Family Versicherungsschein",
                sender = "Allianz Versicherungs-AG",
                mainCategory = "Verträge",
                subCategory = "Versicherungen",
                mainCategoryId = "A02",
                subCategoryId = "B2.02",
                docType = "Versicherungspolicen",
                tags = "#allianz, #haftpflicht, #hausrat, #familie, #versicherung",
                amount = 118.50,
                contractEndDate = 1803859200000L, // 01.03.2027
                cancellationDeadline = 1798761600000L, // 31.12.2026
                companyLogo = "allianz",
                customIcon = "shield",
                daysAgo = 25,
                ocrText = """
                    Allianz Versicherungs-AG - Königinstraße 28, 80802 München
                    VERSICHERUNGSSCHEIN KOMBI-POLICE FAMILIE
                    Police-Nr.: AZ-FAM-2022-77192
                    Versicherungsnehmer: Markus Weber & Familie
                    
                    Versicherungsumfang:
                    1. Privat-Haftpflicht Komfort Familie (50 Mio. € Deckung)
                    2. Hausratversicherung (Versicherungssumme: 65.000 €)
                    
                    Jahresbeitrag gesamt (inkl. 19% VersSt): 118,50 €
                    Fälligkeit: 01.03. jährlich
                    Kündigungsfrist: 3 Monate vor Ablauf des Versicherungsjahres
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Stadtjugendamt München - Kita-Gebührenbescheid",
                sender = "Landeshauptstadt München - Stadtjugendamt",
                mainCategory = "Behörden",
                subCategory = "Familie & Kinder",
                mainCategoryId = "A03",
                subCategoryId = "B3.02",
                docType = "Bescheide",
                tags = "#jugendamt, #kita, #kindergarten, #bescheid, #gebuehren",
                amount = 162.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "stadt_muenchen",
                customIcon = "child_care",
                daysAgo = 32,
                ocrText = """
                    Landeshauptstadt München - Referat für Bildung und Sport
                    GEBÜHRENBESCHEID FÜR KINDERTAGESEINRICHTUNG
                    Aktenzeichen: RJA-2025-4491
                    Kind: Leonie Weber, geb. 14.05.2021
                    Einrichtung: Städtische Kooperationseinrichtung Gotthardstr. 12
                    Buchungszeit: 7 bis 8 Stunden täglich
                    
                    Festgesetzter Elternbeitrag:
                    - Besuchsgebühr nach Münchner Förderformel: 0,00 € (beitragsfrei)
                    - Verpflegungsgeld / Mittagessen: 162,00 € monatlich
                    
                    Fälligkeit: Zum 01. eines jeden Monats per SEPA-Einzug
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Finanzamt München - Einkommensteuerbescheid 2024",
                sender = "Finanzamt München",
                mainCategory = "Finanzen",
                subCategory = "Steuern",
                mainCategoryId = "A01",
                subCategoryId = "B1.04",
                docType = "Steuerbescheide",
                tags = "#finanzamt, #steuerbescheid, #einkommensteuer, #erstattung",
                amount = 1420.80,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "finanzamt",
                customIcon = "account_balance",
                daysAgo = 45,
                ocrText = """
                    Finanzamt München - Deroystraße 6, 80335 München
                    BESCHEID FÜR 2024 ÜBER EINKOMMENSTEUER
                    Steuernummer: 143/281/90214
                    Identifikationsnummer: 82 918 204 192
                    
                    Festgesetzte Einkommensteuer: 8.420,00 €
                    Bereits entrichtete Lohnsteuer: 9.840,80 €
                    
                    Guthaben / Erstattungsbetrag: 1.420,80 €
                    Der Betrag wird auf das angegebene Konto überwiesen.
                    Rechtsbehelfsbelehrung: Einspruchsfrist 1 Monat ab Bekanntgabe.
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "MediaMarkt Kaufbeleg & 5-Jahre-Garantie Bosch Waschmaschine",
                sender = "MediaMarkt Saturn München",
                mainCategory = "Dokumente",
                subCategory = "Kaufbelege & Garantie",
                mainCategoryId = "A05",
                subCategoryId = "B5.01",
                docType = "Rechnungen & Belege",
                tags = "#kaufbeleg, #quittung, #garantie, #mediamarkt, #waschmaschine",
                amount = 649.00,
                contractEndDate = 1924905600000L, // 31.12.2030 (5 Jahre Garantie)
                cancellationDeadline = null,
                companyLogo = "mediamarkt",
                customIcon = "receipt_long",
                daysAgo = 60,
                ocrText = """
                    Media-Saturn Deutschland GmbH - Einsteinstr. 130, 81675 München
                    KASSENBELEG / RECHNUNG
                    Beleg-Nr.: MM-8841-2025
                    Datum: 06.01.2026
                    
                    Artikel:
                    1x Bosch Waschmaschine Serie 6 WAV28M43 (9kg, 1400 U/min): 569,00 €
                    1x PlusGarantie 5 Jahre Geräteschutz: 80,00 €
                    Gesamtbetrag (inkl. 19% MwSt 103,62 €): 649,00 €
                    Zahlart: Girocard
                    Garantiezeitraum gültig bis: 06.01.2031
                """.trimIndent()
            )
        )
    )

    val PERSONA_FREELANCER = UserProfileSimulation(
        id = "freelancer_rossi",
        name = "Marco Rossi",
        roleTitle = "Freiberufler & IT-Consultant",
        description = "Selbstständiger Cloud-Architekt im Homeoffice. Betriebsausgaben mit Vorsteuerabzug, Ausgangsrechnungen, SaaS-Abos, Hardware-Leasing und Reisekosten.",
        iconType = "freelancer",
        highlightTags = listOf("Ausgangsrechnung", "Apple Hardware", "Hetzner Cloud", "USt-Voranmeldung", "Bewirtung", "Dienstreise", "Leasing"),
        documentSpecs = listOf(
            MockDocumentSpec(
                title = "Ausgangsrechnung #RE-2026-042 an BMW Group (Cloud Consulting)",
                sender = "Marco Rossi - Cloud Engineering & Architecture",
                mainCategory = "Finanzen",
                subCategory = "Rechnungen & Belege",
                mainCategoryId = "A01",
                subCategoryId = "B1.01",
                docType = "Ausgangsrechnungen",
                tags = "#ausgangsrechnung, #freelance, #bmw, #consulting, #it, #umsatzsteuer",
                amount = 8092.00,
                contractEndDate = null,
                cancellationDeadline = 1774915200000L, // Zahlungsziel 14 Tage
                companyLogo = "freelance_rossi",
                customIcon = "request_quote",
                daysAgo = 3,
                ocrText = """
                    Marco Rossi - Cloud Engineering & Architecture
                    Lindwurmstraße 88, 80337 München • USt-IdNr.: DE319482019
                    RECHNUNG NR. RE-2026-042
                    Rechnungsdatum: 02.03.2026 • Leistungszeitraum: 01.02.2026 - 28.02.2026
                    Empfänger: BMW AG - Abt. Software Architecture, Petuelring 130, 80788 München
                    
                    Positionen:
                    1. Cloud Architektur & Kubernetes Migration (68 Std. á 100,00 €): 6.800,00 €
                    
                    Nettobetrag: 6.800,00 €
                    Umsatzsteuer 19%: 1.292,00 €
                    Rechnungs-Gesamtbetrag: 8.092,00 €
                    
                    Zahlbar ohne Abzug innerhalb von 14 Tagen bis zum 16.03.2026 auf Konto IBAN DE44 5001 0517 0987 6543 21
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Apple Store München - Rechnung MacBook Pro 16\" M3 Max",
                sender = "Apple Retail Germany B.V. & Co. KG",
                mainCategory = "Finanzen",
                subCategory = "Betriebsausgaben & Hardware",
                mainCategoryId = "A01",
                subCategoryId = "B1.03",
                docType = "Rechnungen & Belege",
                tags = "#apple, #hardware, #macbook, #betriebsausgabe, #vorsteuer",
                amount = 3999.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "apple",
                customIcon = "laptop_mac",
                daysAgo = 8,
                ocrText = """
                    Apple Retail Germany B.V. & Co. KG - Rosenstraße 1, 80331 München
                    RECHNUNG / STEUERBELEG
                    Rechnungsnummer: APP-DE-2026-991204
                    Kunde: Marco Rossi, Lindwurmstr. 88, 80337 München
                    
                    Artikel:
                    1x MacBook Pro 16 Zoll, Space Schwarz, Apple M3 Max, 64GB RAM, 2TB SSD
                    Seriennummer: C02G9014Q8M
                    Einzelpreis netto: 3.360,50 €
                    Umsatzsteuer 19%: 638,50 €
                    Gesamtbetrag brutto: 3.999,00 €
                    Zahlungsart: Firmen-Kreditkarte Mastercard Business
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Hetzner Online GmbH - Root Server & Cloud Monatsrechnung",
                sender = "Hetzner Online GmbH",
                mainCategory = "Verträge",
                subCategory = "IT & Software",
                mainCategoryId = "A02",
                subCategoryId = "B2.04",
                docType = "Rechnungen & Belege",
                tags = "#hetzner, #server, #hosting, #cloud, #it, #betriebsausgabe",
                amount = 176.12,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "hetzner",
                customIcon = "dns",
                daysAgo = 12,
                ocrText = """
                    Hetzner Online GmbH - Industriestr. 25, 91710 Gunzenhausen
                    RECHNUNG NR. R001948201
                    Kunden-Nr.: K948201
                    Leistungszeitraum: 01.02.2026 - 28.02.2026
                    
                    Leistungen:
                    - Dedicated Root Server AX101 (AMD Ryzen 9): 110,00 €
                    - Hetzner Cloud Volume 1TB NVMe: 28,00 €
                    - Snapshot Storage & Floating IPs: 10,00 €
                    Nettobetrag: 148,00 €
                    zzgl. 19% MwSt: 28,12 €
                    Rechnungsbetrag: 176,12 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Finanzamt München II - USt-Vorauszahlung & Fristverlängerung",
                sender = "Finanzamt München II",
                mainCategory = "Finanzen",
                subCategory = "Steuern",
                mainCategoryId = "A01",
                subCategoryId = "B1.04",
                docType = "Steuerbescheide",
                tags = "#finanzamt, #umsatzsteuer, #voranmeldung, #dauerfrist, #steuern",
                amount = 2450.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "finanzamt",
                customIcon = "account_balance",
                daysAgo = 19,
                ocrText = """
                    Finanzamt München II - Winzererstraße 47, 80797 München
                    BESCHEID ÜBER UMSATZSTEUER-VORAUSZAHLUNG Q1/2026
                    Steuernummer: 144/192/40192
                    Dauerfristverlängerung gem. § 46 UStDV ist genehmigt.
                    
                    Festgesetzte Vorauszahlung für das 1. Quartal 2026: 2.450,00 €
                    Fälligkeit: 10.04.2026
                    Der Betrag wird fristgerecht per SEPA-Firmenlastschrift eingezogen.
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Bewirtungsbeleg Projektbesprechung Mandant - Zum Alten Zoll",
                sender = "Gasthaus Zum Alten Zoll",
                mainCategory = "Finanzen",
                subCategory = "Betriebsausgaben & Reise",
                mainCategoryId = "A01",
                subCategoryId = "B1.03",
                docType = "Bewirtungsbelege",
                tags = "#bewirtung, #geschaeftsessen, #beleg, #spesen, #restaurant",
                amount = 139.50,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "restaurant",
                customIcon = "restaurant",
                daysAgo = 22,
                ocrText = """
                    Gasthaus Zum Alten Zoll - Tal 14, 80331 München
                    BEWIRTUNGSBELEG GEMÄSS § 4 ABS. 5 NR. 2 EStG
                    Datum: 14.02.2026 • Rechnungs-Nr.: 94021
                    
                    Bewirtete Personen: Marco Rossi, Dr. Florian Meier (BMW AG)
                    Anlass der Bewirtung: Projektabstimmung Cloud-Infrastruktur Sprint Q2
                    Speisen & Getränke netto: 104,62 €
                    Umsatzsteuer 19%: 19,88 €
                    Trinkgeld: 15,00 €
                    Gesamtbetrag: 139,50 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Deutsche Bahn - Reisekostenbeleg ICE München-Frankfurt Business",
                sender = "Deutsche Bahn AG",
                mainCategory = "Finanzen",
                subCategory = "Betriebsausgaben & Reise",
                mainCategoryId = "A01",
                subCategoryId = "B1.03",
                docType = "Reisekosten",
                tags = "#bahn, #reisekosten, #ice, #ticket, #dienstreise",
                amount = 142.60,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "db",
                customIcon = "train",
                daysAgo = 26,
                ocrText = """
                    Deutsche Bahn AG - DB Vertrieb GmbH, Europa-Allee 78, 60486 Frankfurt
                    ONLINE-TICKET / BUCHUNGSBESTÄTIGUNG
                    Buchungscode: ZK94MR
                    Reisender: Marco Rossi (BahnCard 50 Business 1. Klasse)
                    Fahrt: München Hbf -> Frankfurt(Main) Hbf (ICE 628)
                    
                    Fahrpreis (inkl. 7% MwSt 9,33 €): 142,60 €
                    Zahlart: Business Kreditkarte
                    Gültig am: 10.02.2026
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Hiscox Europe - IT-Haftpflicht & Cyber-Risk Police",
                sender = "Hiscox SA Niederlassung Deutschland",
                mainCategory = "Verträge",
                subCategory = "Versicherungen",
                mainCategoryId = "A02",
                subCategoryId = "B2.02",
                docType = "Versicherungspolicen",
                tags = "#hiscox, #ithaftpflicht, #cyber, #versicherung, #freiberufler",
                amount = 642.60,
                contractEndDate = 1803859200000L, // 01.03.2027
                cancellationDeadline = 1798761600000L, // 31.12.2026
                companyLogo = "hiscox",
                customIcon = "security",
                daysAgo = 30,
                ocrText = """
                    Hiscox SA - Arnulfstraße 31, 80636 München
                    VERSICHERUNGSSCHEIN BERUFSHAFTPFLICHT FÜR IT-EXPERTEN
                    Police-Nr.: HIS-IT-2023-48192
                    Versicherungsnehmer: Marco Rossi
                    
                    Deckungssummen:
                    - Vermögensschadenhaftpflicht: 2.000.000 €
                    - Betriebshaftpflicht: 5.000.000 €
                    - Cyber- und Daten-Eigenschaden: 500.000 €
                    
                    Jahresbeitrag (inkl. VersSt): 642,60 €
                    Hauptfälligkeit: 01.03. jährlich
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Design Offices München - Monatsrechnung Coworking Flex-Desk",
                sender = "Design Offices GmbH",
                mainCategory = "Wohnen",
                subCategory = "Gewerbemiete & Büro",
                mainCategoryId = "A04",
                subCategoryId = "B4.02",
                docType = "Rechnungen & Belege",
                tags = "#coworking, #buero, #designoffices, #miete, #betriebsausgabe",
                amount = 357.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "design_offices",
                customIcon = "business",
                daysAgo = 35,
                ocrText = """
                    Design Offices GmbH - Luise-Ullrich-Straße 20, 80636 München
                    MONATSRECHNUNG COWORKING & MEETING
                    Rechnungs-Nr.: DO-MUC-2026-192
                    Kunde: Marco Rossi
                    
                    Posten:
                    - Monatspass Coworking Flex Desk Arnulfpark: 250,00 €
                    - Konferenzraumnutzung (2 Stunden): 50,00 €
                    Nettobetrag: 300,00 €
                    zzgl. 19% USt: 57,00 €
                    Rechnungsbetrag brutto: 357,00 €
                """.trimIndent()
            )
        )
    )

    val PERSONA_SENIOR = UserProfileSimulation(
        id = "senior_wagner",
        name = "Margarete Wagner",
        roleTitle = "Ruheständlerin & Seniorin (74 J.)",
        description = "Alleinstehende Rentnerin im Eigentum. Fokus auf Rentenbescheide, Beihilfe & Krankenkasse, Apotheken-Sammelbelege, Handwerkerrechnungen nach § 35a und Pflegeabsicherung.",
        iconType = "senior",
        highlightTags = listOf("Rentenbescheid", "Handwerker §35a", "Apotheke", "Optiker", "Krankenkasse", "Rundfunkbeitrag", "Pflegezusatz"),
        documentSpecs = listOf(
            MockDocumentSpec(
                title = "Deutsche Rentenversicherung - Rentenanpassungsmitteilung 2026",
                sender = "Deutsche Rentenversicherung Bund",
                mainCategory = "Finanzen",
                subCategory = "Rente & Altersvorsorge",
                mainCategoryId = "A01",
                subCategoryId = "B1.01",
                docType = "Rentenbescheide",
                tags = "#rente, #rentenbescheid, #rentenversicherung, #ruhestand",
                amount = 1458.95,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "rentenversicherung",
                customIcon = "elderly",
                daysAgo = 4,
                ocrText = """
                    Deutsche Rentenversicherung Bund - 10704 Berlin
                    RENTENANPASSUNGSMITTEILUNG / BESCHEID
                    Versicherungsnummer: 65 140252 W 502
                    Versicherte: Margarete Wagner, geb. 14.02.1952
                    
                    Ihre monatliche Rente ab dem 01.07.2026:
                    - Bisheriger Rentenbetrag: 1.584,20 €
                    - Anpassung um 3,7%: + 58,60 €
                    Neuer Bruttorentenbetrag: 1.642,80 €
                    
                    Abzüge für Kranken- und Pflegeversicherung:
                    - Eigenanteil Krankenversicherung (8,15%): 133,89 €
                    - Pflegeversicherung (3,4%): 55,86 €
                    Monatlicher Auszahlungsbetrag: 1.458,95 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Meisterbetrieb Huber - Handwerkerrechnung § 35a Rohrbruch",
                sender = "Huber Sanitär & Heizungsbau Meisterbetrieb",
                mainCategory = "Wohnen",
                subCategory = "Handwerker & Reparatur",
                mainCategoryId = "A04",
                subCategoryId = "B4.03",
                docType = "Rechnungen & Belege",
                tags = "#handwerker, #paragraph35a, #steuerabzug, #sanitaer, #rechnung",
                amount = 840.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "handwerker_huber",
                customIcon = "build",
                daysAgo = 9,
                ocrText = """
                    Huber Sanitär & Heizungsbau GmbH - Forstenrieder Allee 142, 81476 München
                    RECHNUNG NR. 2026-118 (STEUERLICH ABSETZBAR NACH § 35a EStG)
                    Auftraggeberin: Margarete Wagner, Forstenrieder Str. 89, 81476 München
                    
                    Leistungsbeschreibung:
                    Beseitigung Rohrbruch an der Frischwasserzuleitung Küche
                    - Material (Kupferrohr, Fittinge, Absperrventil): 185,00 €
                    - Arbeitslohn Meister (4,5 Stunden á 80,00 €): 360,00 €
                    - Arbeitslohn Geselle (3,0 Stunden á 50,00 €): 150,00 €
                    - Maschinen- & Fahrtkostenpauschale: 30,00 €
                    Zwischensumme netto: 725,00 €
                    zzgl. 19% MwSt: 115,00 €
                    Rechnungs-Endbetrag: 840,00 €
                    
                    HINWEIS FÜR DAS FINANZAMT:
                    Der enthaltene reine Lohnkostenanteil beträgt netto 510,00 € (brutto 606,90 €)
                    und kann gemäß § 35a Abs. 3 EStG steuermindernd geltend gemacht werden.
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Adler-Apotheke - Quartalsquittung Rezeptzuzahlungen",
                sender = "Adler-Apotheke Forstenried",
                mainCategory = "Gesundheit",
                subCategory = "Apotheke & Medikamente",
                mainCategoryId = "A06",
                subCategoryId = "B6.01",
                docType = "Quittungen",
                tags = "#apotheke, #zuzahlung, #rezeptgebuehr, #krankenkasse, #befreiung",
                amount = 86.40,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "apotheke",
                customIcon = "medication",
                daysAgo = 15,
                ocrText = """
                    Adler-Apotheke - Inh. Dr. Klaus Berger e.K.
                    Forstenrieder Allee 110, 81476 München
                    SAMMELQUITTUNG FÜR GESETZLICHE ZUZAHLUNGEN GEMÄSS § 31 SGB V
                    Kundennummer: ADL-49102 (Wagner, Margarete)
                    Zeitraum: 01.01.2026 - 31.03.2026
                    
                    Zugezahlte Medikamente:
                    - Candesartan 16mg N3: 10,00 €
                    - Torasemid 10mg N3: 7,50 €
                    - Pantoprazol 20mg N3: 5,00 €
                    - Augentropfen Hylo-Commod (Privat): 18,90 €
                    - Kompressionsstrümpfe Maßanfertigung Zuzahlung: 45,00 €
                    Gesamt geleistete Zuzahlungen: 86,40 €
                    Zur Vorlage bei der Krankenkasse zwecks Erreichen der Belastungsgrenze (1%/2%).
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Fielmann AG - Rechnung Gleitsichtbrille Titan & Versicherung",
                sender = "Fielmann AG & Co. oHG",
                mainCategory = "Gesundheit",
                subCategory = "Optiker & Sehhilfen",
                mainCategoryId = "A06",
                subCategoryId = "B6.02",
                docType = "Rechnungen & Belege",
                tags = "#fielmann, #brille, #gleitsicht, #versicherung, #optiker",
                amount = 489.00,
                contractEndDate = 1830211200000L, // 2 Jahre Brillenschutzbrief
                cancellationDeadline = null,
                companyLogo = "fielmann",
                customIcon = "visibility",
                daysAgo = 28,
                ocrText = """
                    Fielmann AG & Co. oHG - Filiale Neuhauser Str. 19, 80331 München
                    RECHNUNG & BRILLENPASS
                    Auftrag-Nr.: 8920412
                    Kundin: Margarete Wagner
                    
                    Brillenfassung: Titan-Flex randlos (Modell TF 402): 149,00 €
                    Gläser: 2x Kunststoff-Gleitsichtglas Premium mit Entspiegelung & Hartschicht: 310,00 €
                    Fielmann Null-Tarif-Brillenschutzbrief (2 Jahre Laufzeit): 30,00 €
                    Gesamtbetrag (inkl. 19% MwSt 78,07 €): 489,00 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Techniker Krankenkasse - Kostenerstattungsbescheid Zahnersatz",
                sender = "Techniker Krankenkasse (TK)",
                mainCategory = "Gesundheit",
                subCategory = "Krankenkasse",
                mainCategoryId = "A06",
                subCategoryId = "B6.03",
                docType = "Bescheide",
                tags = "#tk, #krankenkasse, #zahnersatz, #erstattung, #festzuschuss",
                amount = 240.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "tk",
                customIcon = "healing",
                daysAgo = 36,
                ocrText = """
                    Techniker Krankenkasse - 20905 Hamburg
                    BEWILLIGUNGS- UND ABRECHNUNGSBESCHEID HEIL- UND KOSTENPLAN
                    Versicherte: Margarete Wagner • Vers.-Nr.: X194820194
                    
                    Behandlung: Zahnersatz Kronenerneuerung Zahn 24 & 25
                    Gesamtkosten Zahnarzt: 620,00 €
                    Festgesetzter Festzuschuss mit vollem Bonusheft (10 Jahre): 240,00 €
                    
                    Der Zuschuss in Höhe von 240,00 € wurde direkt mit der Kassenzahnärztlichen Vereinigung verrechnet.
                    Ihr verbleibender Eigenanteil beträgt 380,00 €.
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "ARD ZDF Beitragsservice - Zahlungsbestätigung Rundfunkbeitrag",
                sender = "ARD ZDF Deutschlandradio Beitragsservice",
                mainCategory = "Behörden",
                subCategory = "Rundfunkgebühren",
                mainCategoryId = "A03",
                subCategoryId = "B3.01",
                docType = "Gebührenbescheide",
                tags = "#gez, #rundfunkbeitrag, #ard, #zdf, #beitragsservice",
                amount = 55.08,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "beitragsservice",
                customIcon = "tv",
                daysAgo = 42,
                ocrText = """
                    ARD ZDF Deutschlandradio Beitragsservice - 50656 Köln
                    ZAHLUNGSBESTÄTIGUNG RUNDFUNKBEITRAG
                    Beitragsnummer: 491 820 491
                    Wohnungsinhaberin: Margarete Wagner
                    
                    Zeitraum: 01.01.2026 - 31.03.2026 (Quartal I/2026)
                    Geleisteter Rundfunkbeitrag: 55,08 € (monatlich 18,36 €)
                    Zahlweise: Vierteljährlich im Voraus per SEPA-Lastschrift
                """.trimIndent()
            )
        )
    )

    val PERSONA_STUDENT = UserProfileSimulation(
        id = "student_jonas",
        name = "Jonas Bergmann",
        roleTitle = "Student mit WG & Minijob",
        description = "Informatik-Student an der TU München im 4. Semester. WG-Leben, BAföG, Minijob-Abrechnung, Semesterticket und Ausbildungsbelege.",
        iconType = "student",
        highlightTags = listOf("BAföG", "TUM Semesterbeitrag", "WG-Miete", "Minijob", "Deutschlandticket", "AOK Student", "Fachliteratur"),
        documentSpecs = listOf(
            MockDocumentSpec(
                title = "Amt für Ausbildungsförderung - BAföG-Bewilligungsbescheid 2025/2026",
                sender = "Studentenwerk München - Amt für Ausbildungsförderung",
                mainCategory = "Behörden",
                subCategory = "Studium & BAföG",
                mainCategoryId = "A03",
                subCategoryId = "B3.03",
                docType = "Bescheide",
                tags = "#bafoeg, #studium, #studentenwerk, #foerderung, #bescheid",
                amount = 642.00,
                contractEndDate = 1790726400000L, // 30.09.2026
                cancellationDeadline = null,
                companyLogo = "studentenwerk",
                customIcon = "school",
                daysAgo = 6,
                ocrText = """
                    Studentenwerk München - Anstalt des öffentlichen Rechts
                    Amt für Ausbildungsförderung - Leopoldstraße 15, 80802 München
                    BESCHEID NACH DEM BUNDESAUSBILDUNGSFÖRDERUNGSGESETZ (BAföG)
                    Förderungsnummer: BAF-2024-88412
                    Antragsteller: Jonas Bergmann, geb. 22.08.2003
                    Ausbildungsstätte: Technische Universität München (B.Sc. Informatik)
                    
                    Bewilligungszeitraum: 01.10.2025 bis 30.09.2026 (12 Monate)
                    Monatlicher Gesamtbedarf: 812,00 €
                    Anzurechnendes Einkommen der Eltern: 170,00 €
                    Monatlicher Förderbetrag: 642,00 €
                    (davon 321,00 € als Zuschuss und 321,00 € als zinsloses Staatsdarlehen)
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "TUM Immatrikulationsbescheinigung & Semesterbeitrag SoSe 2026",
                sender = "Technische Universität München (TUM)",
                mainCategory = "Dokumente",
                subCategory = "Hochschule & Universität",
                mainCategoryId = "A05",
                subCategoryId = "B5.02",
                docType = "Bescheinigungen",
                tags = "#tum, #universitaet, #immatrikulation, #semesterbeitrag, #studium",
                amount = 152.30,
                contractEndDate = 1790726400000L, // 30.09.2026
                cancellationDeadline = null,
                companyLogo = "tum",
                customIcon = "badge",
                daysAgo = 11,
                ocrText = """
                    Technische Universität München - Arcisstraße 21, 80333 München
                    IMMATRIKULATIONS- UND STUDIENVERLAUFSBESCHEINIGUNG
                    Matrikelnummer: 03789124
                    Name: Jonas Bergmann
                    Studiengang: Informatik (Bachelor of Science)
                    Fachsemester: 4 • Sommersemester 2026
                    
                    Bestätigung der Beitragszahlung:
                    Der Grundbeitrag zum Studierendenwerk in Höhe von 85,00 € sowie der
                    Solidarbeitrag für das Semesterticket in Höhe von 67,30 €
                    (Gesamt: 152,30 €) wurden für das Sommersemester 2026 vollständig entrichtet.
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "WG-Untermietvertrag Zimmer 18qm Schwanthalerhöhe",
                sender = "Hauptmieter WG Schwanthalerhöhe (Tim Krüger)",
                mainCategory = "Wohnen",
                subCategory = "Mietverträge",
                mainCategoryId = "A04",
                subCategoryId = "B4.01",
                docType = "Verträge",
                tags = "#wg, #miete, #untermiete, #zimmer, #vertrag, #kaution",
                amount = 460.00,
                contractEndDate = null,
                cancellationDeadline = 1774915200000L,
                companyLogo = "wg_zimmer",
                customIcon = "meeting_room",
                daysAgo = 20,
                ocrText = """
                    UNTERMIETVERTRAG FÜR EIN ZIMMER IN EINER WOHNGEMEINSCHAFT
                    Hauptmieter: Tim Krüger
                    Untermieter: Jonas Bergmann
                    Mietobjekt: Schwanthalerstraße 112, 80339 München, WG-Zimmer 2 (ca. 18 qm)
                    
                    Mietzins:
                    - Kaltmiete: 360,00 € monatlich
                    - Pauschale für Nebenkosten, Strom & Internet: 100,00 € monatlich
                    Gesamtmiete (Warmmiete): 460,00 €
                    
                    Kaution: 920,00 € (2 Monatsmieten)
                    Kündigungsfrist: Gesetzliche Kündigungsfrist von 3 Monaten zum Monatsende.
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Rewe Markt GmbH - Minijob Entgeltabrechnung 02/2026",
                sender = "REWE Markt GmbH - Zweigniederlassung Süd",
                mainCategory = "Finanzen",
                subCategory = "Gehalt & Minijob",
                mainCategoryId = "A01",
                subCategoryId = "B1.01",
                docType = "Gehaltsabrechnungen",
                tags = "#rewe, #minijob, #gehalt, #studentenjob, #verdienst",
                amount = 538.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "rewe",
                customIcon = "shopping_bag",
                daysAgo = 25,
                ocrText = """
                    REWE Markt GmbH - Bernhard-Wicki-Straße 5, 80636 München
                    LOHNABRECHNUNG GERINGFÜGIG BESCHÄFTIGTE (MINIJOB)
                    Personal-Nr.: RE-491024
                    Mitarbeiter: Jonas Bergmann • Filiale: Gollierstraße 24
                    Abrechnungsmonat: Februar 2026
                    
                    Arbeitsstunden: 43 Stunden á 12,51 € Mindestlohn = 538,00 €
                    Geringfügige Beschäftigung (§ 8 Abs. 1 Nr. 1 SGB IV):
                    - Befreiung von der Rentenversicherungspflicht beantragt: Ja
                    Gesetzliche Abzüge AN: 0,00 €
                    Auszahlungsbetrag: 538,00 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "MVG / DB Vertrieb - Deutschlandticket Studierende Bestätigung",
                sender = "Münchner Verkehrsgesellschaft (MVG)",
                mainCategory = "Verträge",
                subCategory = "Öffentlicher Nahverkehr",
                mainCategoryId = "A02",
                subCategoryId = "B2.03",
                docType = "Abonnements",
                tags = "#mvg, #deutschlandticket, #oepnv, #student, #semesterticket",
                amount = 29.40,
                contractEndDate = 1790726400000L,
                cancellationDeadline = null,
                companyLogo = "mvg",
                customIcon = "directions_subway",
                daysAgo = 33,
                ocrText = """
                    Münchner Verkehrsgesellschaft mbH - Emmy-Noether-Str. 2, 80992 München
                    BESTÄTIGUNG DEUTSCHLANDTICKET FÜR STUDIERENDE
                    Abo-Nummer: DT-STUD-2025-9941
                    Kunde: Jonas Bergmann • TUM Matrikel-Nr.: 03789124
                    
                    Produkt: Ermäßigtes Deutschlandticket für Studierende in Bayern
                    Gültigkeit: Bundesweit im Nah- und Regionalverkehr in der 2. Klasse
                    Monatlicher Bezugspreis: 29,40 €
                    Zahlart: Monatliche Abbuchung zum 1. des Monats
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Hugendubel - Quittung Fachliteratur Informatik",
                sender = "Buchhandlung Heinrich Hugendubel GmbH & Co. KG",
                mainCategory = "Finanzen",
                subCategory = "Studienausgaben",
                mainCategoryId = "A01",
                subCategoryId = "B1.03",
                docType = "Quittungen",
                tags = "#hugendubel, #buch, #fachliteratur, #informatik, #studium",
                amount = 79.90,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "hugendubel",
                customIcon = "menu_book",
                daysAgo = 40,
                ocrText = """
                    Buchhandlung Heinrich Hugendubel - Marienplatz 22, 80331 München
                    KAUFBELEG / QUITTUNG
                    Beleg-Nr.: HUG-2026-44012
                    Datum: 16.01.2026
                    
                    Artikel:
                    1x 'Algorithmen und Datenstrukturen' (Cormen / Leiserson, MIT Press): 79,90 €
                    Enthaltene Mehrwertsteuer (7%): 5,23 €
                    Zahlart: Apple Pay
                    Als Studienausgabe / Werbungskosten steuerlich anrechenbar.
                """.trimIndent()
            )
        )
    )

    val PERSONA_LANDLORD = UserProfileSimulation(
        id = "landlord_neumann",
        name = "Dr. Thomas Neumann",
        roleTitle = "Immobilienbesitzer & Vermieter",
        description = "Eigentümer eines Mehrfamilienhauses und zweier vermieteter Eigentumswohnungen. Fokus auf Mietverträge, Notarurkunden, Grundsteuer, Hausgeld und Instandhaltung.",
        iconType = "landlord",
        highlightTags = listOf("Mietvertrag", "WEG Hausgeld", "Grundsteuer B", "Dachsanierung", "Darlehenszins", "Gebäudeversicherung", "Notarkosten"),
        documentSpecs = listOf(
            MockDocumentSpec(
                title = "Wohnraum-Mietvertrag Neuvermietung Apartment Maxvorstadt",
                sender = "Dr. Thomas Neumann (Vermieter)",
                mainCategory = "Verträge",
                subCategory = "Immobilien & Vermietung",
                mainCategoryId = "A02",
                subCategoryId = "B2.05",
                docType = "Mietverträge",
                tags = "#mietvertrag, #vermietung, #maxvorstadt, #kaltmiete, #kaution",
                amount = 1450.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "neumann_immobilien",
                customIcon = "real_estate_agent",
                daysAgo = 5,
                ocrText = """
                    MIETVERTRAG FÜR WOHNRAUM
                    Vermieter: Dr. Thomas Neumann, Brienner Str. 44, 80333 München
                    Mieter: Dr. med. Christian Brandl
                    Mietobjekt: Schellingstraße 78, 80799 München, 2. OG (74 qm, 3 Zimmer)
                    
                    Mietzins:
                    - Monatliche Grundmiete (Kaltmiete): 1.450,00 €
                    - Vorauszahlung Betriebskosten: 180,00 €
                    - Vorauszahlung Heizung / Warmwasser: 100,00 €
                    Gesamtmiete monatlich: 1.730,00 €
                    
                    Mietsicherheit (Kaution): 4.350,00 € (3 Monatskaltmieten)
                    Mietbeginn: 01.03.2026 auf unbestimmte Zeit
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "WEG Hausverwaltung Scholl - Jahresabrechnung Wohngeld 2025",
                sender = "Scholl Immobilien & Hausverwaltung GmbH",
                mainCategory = "Wohnen",
                subCategory = "WEG & Hausgeld",
                mainCategoryId = "A04",
                subCategoryId = "B4.02",
                docType = "Abrechnungen",
                tags = "#hausgeld, #weg, #instandhaltungsruecklage, #eigentum, #hausverwaltung",
                amount = 3240.00,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "scholl_hausverwaltung",
                customIcon = "apartment",
                daysAgo = 13,
                ocrText = """
                    Scholl Immobilien & Hausverwaltungs GmbH - Barer Str. 50, 80799 München
                    JAHRESABRECHNUNG 2025 DER WOHNUNGSEIGENTÜMERGEMEINSCHAFT
                    Liegenschaft: Schellingstraße 78, 80799 München (WE 04, Miteigentumsanteil: 125/1.000)
                    Eigentümer: Dr. Thomas Neumann
                    
                    Aufstellung Gesamtausgaben:
                    - Laufende Bewirtschaftung & Betriebskosten: 2.390,00 €
                    - Zuführung zur gesetzlichen Instandhaltungsrücklage: 850,00 €
                    Gesamtes Hausgeld: 3.240,00 €
                    
                    Geleistete Vorauszahlungen (12 x 265,00 €): 3.180,00 €
                    Verbleibende Nachzahlung: 60,00 €
                    Stand Instandhaltungsrücklage der WEG per 31.12.2025: 48.900,00 €
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Stadtkämmerei München - Grundsteuerbescheid Grundsteuer B",
                sender = "Landeshauptstadt München - Stadtkämmerei",
                mainCategory = "Finanzen",
                subCategory = "Steuern & Abgaben",
                mainCategoryId = "A01",
                subCategoryId = "B1.04",
                docType = "Steuerbescheide",
                tags = "#grundsteuer, #stadtkaemmerei, #muenchen, #grundsteuerb, #bescheid",
                amount = 612.40,
                contractEndDate = null,
                cancellationDeadline = null,
                companyLogo = "stadt_muenchen",
                customIcon = "account_balance",
                daysAgo = 21,
                ocrText = """
                    Landeshauptstadt München - Stadtkämmerei - Herzog-Wilhelm-Str. 11, 80331 München
                    GRUNDSTEUERBESCHEID FÜR DAS KALENDERJAHR 2026
                    Kassenzeichen: 491-002-9481-0
                    Steuerschuldner: Dr. Thomas Neumann
                    Steuergegenstand: Grundbesitz Schellingstr. 78, Flurnummer 1042/1
                    
                    Berechnung:
                    - Grundsteuermessbetrag: 114,25 €
                    - Hebesatz der Landeshauptstadt München (536%): x 5,36
                    Jahressteuer 2026: 612,40 €
                    
                    Fälligkeit in vierteljährlichen Teilbeträgen zu je 153,10 € am:
                    15.02., 15.05., 15.08. und 15.11.2026
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Dachdecker Meisterbetrieb Kreitmeier - Rechnung Dachrinnensanierung",
                sender = "Kreitmeier Bedachungen & Spenglerei Meisterbetrieb",
                mainCategory = "Wohnen",
                subCategory = "Handwerker & Sanierung",
                mainCategoryId = "A04",
                subCategoryId = "B4.03",
                docType = "Rechnungen & Belege",
                tags = "#dachdecker, #sanierung, #spenglerei, #gewaehrleistung, #handwerker",
                amount = 2890.00,
                contractEndDate = 1924905600000L, // 5 Jahre VOB Gewährleistung
                cancellationDeadline = null,
                companyLogo = "dachdecker",
                customIcon = "roofing",
                daysAgo = 31,
                ocrText = """
                    Kreitmeier Bedachungen GmbH - Schleißheimer Str. 200, 80797 München
                    SCHLUSSRECHNUNG NACH VOB / B
                    Rechnungs-Nr.: KREIT-2026-88
                    Auftraggeber: Dr. Thomas Neumann, Liegenschaft Schellingstr. 78
                    
                    Leistung: Teilsanierung Titanzink-Dachrinne & Kehlbleche
                    - Gerüstbau & Sicherung: 420,00 €
                    - Material Titanzink Rinnen & Fallrohre: 830,00 €
                    - Spengler-Arbeitsleistung Meister & Gesellen: 1.180,00 €
                    Nettobetrag: 2.430,00 €
                    zzgl. 19% MwSt: 460,00 €
                    Rechnungs-Endbetrag: 2.890,00 €
                    Gewährleistungsfrist: 5 Jahre nach VOB/B bis 2031
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Stadtsparkasse München - Zins- und Tilgungsplan Immobilienkredit",
                sender = "Stadtsparkasse München",
                mainCategory = "Finanzen",
                subCategory = "Kredite & Darlehen",
                mainCategoryId = "A01",
                subCategoryId = "B1.05",
                docType = "Kreditbescheinigungen",
                tags = "#sparkasse, #kredit, #darlehen, #zinsen, #tilgung, #finanzamt",
                amount = 4280.00,
                contractEndDate = 1861920000000L, // Ende Zinsbindung 2028
                cancellationDeadline = null,
                companyLogo = "sparkasse",
                customIcon = "account_balance",
                daysAgo = 44,
                ocrText = """
                    Stadtsparkasse München - Sparkassenstraße 2, 80331 München
                    JAHRESBESCHEINIGUNG FÜR DAS FINANZAMT / ZINS- UND TILGUNGSNACHWEIS
                    Darlehenskonto: 700 192 481 (Immobiliendarlehen Schellingstr.)
                    Darlehensnehmer: Dr. Thomas Neumann
                    
                    Zahlen für das Kalenderjahr 2025:
                    - Geleistete Zinszahlungen: 4.280,00 € (steuerlich als Werbungskosten V+V ansetzbar)
                    - Geleistete Tilgungsleistungen: 5.720,00 €
                    - Gesamte Annuität: 10.000,00 €
                    - Restschuld per 31.12.2025: 112.500,00 €
                    Zinsbindung fest bis: 30.11.2028
                """.trimIndent()
            ),
            MockDocumentSpec(
                title = "Provinzial - Wohngebäude- & Elementarschaden-Police",
                sender = "Provinzial Versicherung AG",
                mainCategory = "Verträge",
                subCategory = "Versicherungen",
                mainCategoryId = "A02",
                subCategoryId = "B2.02",
                docType = "Versicherungspolicen",
                tags = "#provinzial, #gebaeudeversicherung, #elementarschaden, #police",
                amount = 520.00,
                contractEndDate = 1803859200000L,
                cancellationDeadline = 1798761600000L,
                companyLogo = "provinzial",
                customIcon = "shield",
                daysAgo = 52,
                ocrText = """
                    Provinzial Versicherung AG - Provinzial-Platz 1, 40591 Düsseldorf
                    VERSICHERUNGSSCHEIN WOHNGEBÄUDEVERSICHERUNG PREMIUM
                    Police-Nr.: WG-2018-994102
                    Versichertes Objekt: Schellingstr. 78, 80799 München
                    
                    Versicherungsumfang:
                    Feuer, Leitungswasser, Sturm, Hagel sowie erweiterte Elementarschäden
                    (Überschwemmung, Rückstau, Starkregen, Schneedruck)
                    
                    Jahresbeitrag (inkl. 19% VersSt): 520,00 €
                    Fälligkeit: 01.03. jährlich per Lastschrift
                    Kündigungsfrist: 3 Monate vor Ablauf des Versicherungsjahres
                """.trimIndent()
            )
        )
    )

    val ALL_PERSONAS = listOf(
        PERSONA_FAMILY,
        PERSONA_FREELANCER,
        PERSONA_SENIOR,
        PERSONA_STUDENT,
        PERSONA_LANDLORD
    )
}
