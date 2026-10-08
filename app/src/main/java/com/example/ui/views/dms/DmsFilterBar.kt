package com.example.ui.views.dms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.ActiveFilterSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmsFilterBar(
    viewModel: DocAnizerViewModel,
    activeFilterCount: Int,
    onOpenFilterSheet: (ActiveFilterSheet) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterDocType by viewModel.filterDocType.collectAsStateWithLifecycle()
    val filterMainCategory by viewModel.filterMainCategory.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Dokumente durchsuchen...", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Suche löschen")
                        }
                    }
                },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 56.dp)
                    .testTag("dms_search_input")
            )

            BadgedBox(
                badge = {
                    if (activeFilterCount > 0) {
                        Badge { Text("$activeFilterCount") }
                    }
                }
            ) {
                FilledTonalButton(
                    onClick = { onOpenFilterSheet(ActiveFilterSheet.SELECT_DIMENSION) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("dms_filter_button")
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Filter")
                }
            }
        }

        if (filterDocType != null || filterMainCategory != null || activeFilterCount > 0) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (filterMainCategory != null) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setFilterMainCategory(null) },
                            label = { Text("Kategorie: $filterMainCategory") },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                if (filterDocType != null) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.setFilterDocType(null) },
                            label = { Text("Typ: $filterDocType") },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                if (activeFilterCount > 0) {
                    item {
                        TextButton(onClick = {
                            viewModel.setFilterDocType(null)
                            viewModel.setFilterMainCategory(null)
                            viewModel.setFilterSubCategory(null)
                            viewModel.setFilterTag(null)
                            viewModel.setSearchQuery("")
                        }) {
                            Text("Alle zurücksetzen", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
