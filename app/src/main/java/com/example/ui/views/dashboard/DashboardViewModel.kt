package com.example.ui.views.dashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DashboardStats(val totalDocuments: Int, val recentActions: Int, val chartData: List<Float>)
data class TodoItem(val id: String, val title: String, val docId: String?, val isCompleted: Boolean)
data class Appointment(val id: String, val title: String, val date: String, val hasReminder: Boolean)

class DashboardViewModel : ViewModel() {
    private val _stats = MutableStateFlow(DashboardStats(120, 5, listOf(10f, 25f, 15f, 40f, 30f)))
    val stats: StateFlow<DashboardStats> = _stats

    private val _todos = MutableStateFlow(listOf(
        TodoItem("1", "Steuererklärung prüfen", "doc_123", false),
        TodoItem("2", "Versicherung kündigen", null, false)
    ))
    val todos: StateFlow<List<TodoItem>> = _todos

    private val _appointments = MutableStateFlow(listOf(
        Appointment("1", "Termin beim Steuerberater", "2026-10-20", true),
        Appointment("2", "Dokumenten-Check", "2026-10-25", false)
    ))
    val appointments: StateFlow<List<Appointment>> = _appointments

    // LLM-Friendly Tool Actions
    fun addTask(title: String, docId: String?) {
        val newTodo = TodoItem(System.currentTimeMillis().toString(), title, docId, false)
        _todos.value = _todos.value + newTodo
    }

    fun completeTask(todoId: String) {
        _todos.value = _todos.value.map { if (it.id == todoId) it.copy(isCompleted = true) else it }
    }

    fun addAppointment(title: String, date: String, hasReminder: Boolean) {
        val newAppt = Appointment(System.currentTimeMillis().toString(), title, date, hasReminder)
        _appointments.value = _appointments.value + newAppt
    }
}
