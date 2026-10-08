package com.example.ui

import com.example.data.FinanceDao
import com.example.model.BankStatementEntryEntity
import com.example.model.CashTransactionEntity
import com.example.service.BankStatementParserService
import com.example.service.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinanceManager(
    private val financeDao: FinanceDao,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope,
    private val triggerRatingPrompt: () -> Unit
) {
    val cashTransactions: StateFlow<List<CashTransactionEntity>> = financeDao.getAllCashTransactions()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bankStatementEntries: StateFlow<List<BankStatementEntryEntity>> = financeDao.getAllBankStatementEntries()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCashTransaction(tx: CashTransactionEntity) {
        scope.launch(Dispatchers.IO) {
            financeDao.insertCashTransaction(tx)
            withContext(Dispatchers.Main) {
                triggerRatingPrompt()
            }
        }
    }

    fun updateCashTransaction(tx: CashTransactionEntity) {
        scope.launch(Dispatchers.IO) {
            financeDao.updateCashTransaction(tx)
        }
    }

    fun deleteCashTransaction(id: String) {
        scope.launch(Dispatchers.IO) {
            financeDao.deleteCashTransactionById(id)
        }
    }

    suspend fun importBankStatementEntriesSync(entries: List<BankStatementEntryEntity>): Int {
        val existing = financeDao.getAllBankStatementEntriesList()
        val existingKeys = existing.map { "${it.date}_${it.amount}_${it.bookingText.trim().lowercase()}" }.toSet()
        val newEntries = entries.filter {
            val key = "${it.date}_${it.amount}_${it.bookingText.trim().lowercase()}"
            !existingKeys.contains(key)
        }
        if (newEntries.isNotEmpty()) {
            financeDao.insertBankStatementEntries(newEntries)
        }
        return newEntries.size
    }

    fun importBankStatementEntries(entries: List<BankStatementEntryEntity>, onComplete: ((Int) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val insertedCount = importBankStatementEntriesSync(entries)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(insertedCount)
            }
        }
    }

    fun importDemoBankStatement(monthKey: String, onComplete: ((Int) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val demoEntries = BankStatementParserService.generateDemoStatement(monthKey)
            val insertedCount = importBankStatementEntriesSync(demoEntries)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(insertedCount)
            }
        }
    }

    fun deleteBankStatementEntry(id: String) {
        scope.launch(Dispatchers.IO) {
            financeDao.deleteBankStatementEntryById(id)
        }
    }

    fun clearBankStatementEntries() {
        scope.launch(Dispatchers.IO) {
            financeDao.clearAllBankStatementEntries()
        }
    }
}
