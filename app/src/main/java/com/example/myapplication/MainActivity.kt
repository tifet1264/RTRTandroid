package com.example.myapplication


import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.forEach
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import androidx.lifecycle.map
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings // 딕셔너리 편집 아이콘
import androidx.compose.material.icons.filled.BrightnessMedium // 테마 변경 아이콘
import androidx.compose.material.icons.filled.Translate // 언어 변경 아이콘
import androidx.lifecycle.AndroidViewModel // ViewModel 대신 이걸로 변경
import android.app.Application
import androidx.core.app.ActivityCompat.recreate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken



// --- 데이터 클래스 및 모델 ---

// 화면 종류를 나타내는 enum
enum class Screen {
    MAIN, RECEIPTS, DICTIONARY_EDITOR
}

// 수입/지출 유형
enum class TransactionType {
    INCOME, EXPENSE
}

// 거래 내역 데이터 클래스
data class Transaction(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val amount: Long,
    val type: TransactionType,
    val timestamp: Long = System.currentTimeMillis()
)

// 항목 추천 딕셔너리 데이터 클래스
data class RecommendationItem(
    val id: UUID = UUID.randomUUID(),
    var name: String,
    var minPrice: Long,
    var maxPrice: Long
)

// --- ViewModel: 앱의 모든 상태와 로직을 관리 ---

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository(application)

    // 현재 화면 상태
    private val _currentScreen = MutableLiveData(Screen.MAIN)
    val currentScreen: LiveData<Screen> = _currentScreen

    // 테마 상태 (true: 다크 모드)
    private val _isDarkTheme = MutableLiveData(false)
    val isDarkTheme: LiveData<Boolean> = _isDarkTheme

    // 언어 상태 (true: 한국어)
    private val _isKorean = MutableLiveData(true)
    val isKorean: LiveData<Boolean> = _isKorean

    // 키패드 입력 값
    private val _inputValue = MutableLiveData("0")
    val inputValue: LiveData<String> = _inputValue

    // 수입/지출 타입
    private val _transactionType = MutableLiveData(TransactionType.EXPENSE)
    val transactionType: LiveData<TransactionType> = _transactionType

    // 전체 거래 내역 리스트
    private val _transactions = MutableLiveData<List<Transaction>>(repository.loadTransactions())
    val transactions: LiveData<List<Transaction>> = _transactions

    private val _recommendationDictionary = MutableLiveData(repository.loadRecommendationItems())
    val recommendationDictionary: LiveData<List<RecommendationItem>> = _recommendationDictionary


    val recommendedItems: LiveData<List<RecommendationItem>> = _inputValue.map { value ->
        val amount = value.toLongOrNull() ?: 0
        if (amount == 0L) return@map emptyList()

        _recommendationDictionary.value
            ?.filter { item -> amount in item.minPrice..item.maxPrice }
            ?.sortedBy { item -> abs(amount - (item.minPrice + item.maxPrice) / 2) }
            ?.take(3) ?: emptyList()
    }


    // 화면 변경
    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // 테마 변경
    fun toggleTheme() {
        _isDarkTheme.value = !(_isDarkTheme.value ?: false)
    }

    // 언어 변경
    fun toggleLanguage(context: Context) {
        val newIsKorean = !(_isKorean.value ?: false)
        _isKorean.value = newIsKorean
        val locale = if (newIsKorean) Locale.KOREAN else Locale.ENGLISH
        updateLocale(context, locale)
    }

    private fun updateLocale(context: Context, locale: Locale) {
        Locale.setDefault(locale)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }


    // 키패드 입력 처리
    fun onKeypadClick(key: String) {
        val current = _inputValue.value ?: "0"
        when (key) {
            "C" -> _inputValue.value = "0"
            "+/-" -> _transactionType.value =
                if (_transactionType.value == TransactionType.EXPENSE) TransactionType.INCOME else TransactionType.EXPENSE

            else -> {
                val newValue = if (current == "0") key else current + key
                if (newValue.length <= 12) { // 최대 12자리
                    _inputValue.value = newValue
                }
            }
        }
    }

    // 거래 내역 추가
    fun addTransaction(name: String) {
        val amount = _inputValue.value?.toLongOrNull() ?: 0
        if (amount > 0) {
            val newTransaction = Transaction(
                id = UUID.randomUUID(), // ID는 여기서 생성
                name = name,
                amount = amount,
                type = _transactionType.value ?: TransactionType.EXPENSE,
                timestamp = System.currentTimeMillis() // 타임스탬프는 여기서 생성
            )
            val updatedList = _transactions.value.orEmpty() + newTransaction
            _transactions.value = updatedList.sortedByDescending { it.timestamp }

            // 3. 변경된 최신 목록을 저장소에 저장합니다.
            repository.saveTransactions(updatedList)

            _inputValue.value = "0"
            _transactionType.value = TransactionType.EXPENSE
        }
    }

    fun addDictionaryItem(item: RecommendationItem) {
        // ID가 없는 새 아이템이 들어오면 여기서 ID를 부여합니다.
        val itemWithId = if (item.id.toString().startsWith("0000")) item.copy(id = UUID.randomUUID()) else item
        val updatedList = _recommendationDictionary.value.orEmpty() + itemWithId
        _recommendationDictionary.value = updatedList

        repository.saveRecommendationItems(updatedList)
    }

    fun updateDictionaryItem(item: RecommendationItem) {
        val updatedList = _recommendationDictionary.value.orEmpty().map {
            if (it.id == item.id) item else it
        }
        _recommendationDictionary.value = updatedList
        repository.saveRecommendationItems(updatedList)
    }

    fun deleteDictionaryItem(item: RecommendationItem) {
        val updatedList = _recommendationDictionary.value.orEmpty().filterNot { it.id == item.id }
        _recommendationDictionary.value = updatedList
        repository.saveRecommendationItems(updatedList)
    }
}

// --- 메인 액티비티 ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AppViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.observeAsState(false)
            val isKorean by viewModel.isKorean.observeAsState(true) // Recompose when language changes

            // Force recomposition on language change
            LaunchedEffect(isKorean) {
                // This block is just to trigger recomposition.
                // The actual locale update is handled in the ViewModel.
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel)
                }
            }
        }
    }
}

// --- 컴포저블 UI ---

@Composable
fun AppContent(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.observeAsState(Screen.MAIN)

    Column(Modifier.fillMaxSize()) {
        TopBar(viewModel = viewModel)
        Box(Modifier.weight(1f)) {
            when (currentScreen) {
                Screen.MAIN -> MainInputScreen(viewModel)
                Screen.RECEIPTS -> ReceiptsScreen(viewModel)
                Screen.DICTIONARY_EDITOR -> DictionaryEditorScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(viewModel: AppViewModel) {
    val context = LocalContext.current
    val isDarkTheme by viewModel.isDarkTheme.observeAsState(false)
    val isKorean by viewModel.isKorean.observeAsState(true)
    val currentScreen by viewModel.currentScreen.observeAsState(Screen.MAIN)

    TopAppBar(
        title = { Text(stringResource(id = R.string.app_name)) },
        navigationIcon = {
            // 이 부분은 그대로 둡니다.
            if (currentScreen != Screen.MAIN) {
                IconButton(onClick = { viewModel.navigateTo(Screen.MAIN) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_to_main)
                    )
                }
            } else {
                IconButton(onClick = { viewModel.navigateTo(Screen.RECEIPTS) }) {
                    Icon(Icons.Default.List, contentDescription = stringResource(R.string.receipts_page))
                }
            }
        },
        // --- 이 부분을 추가하거나 복원합니다 ---
        actions = {
            // 딕셔너리 편집 버튼
            IconButton(onClick = { viewModel.navigateTo(Screen.DICTIONARY_EDITOR) }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.dictionary_editor))
            }
            // 테마 변경 버튼
            IconButton(onClick = { viewModel.toggleTheme() }) {
                Icon(Icons.Default.BrightnessMedium, contentDescription = stringResource(R.string.toggle_theme))
            }
            // 언어 변경 버튼
            IconButton(onClick = {
                viewModel.toggleLanguage(context)
                val activity = context as? Activity

// 3. 만약 Activity가 맞다면, recreate() 함수를 호출합니다.
                activity?.recreate()

            })
            {
                Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.toggle_language))
            }
        },
        // ------------------------------------
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun MainInputScreen(viewModel: AppViewModel) {
    var showManualAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InputDisplay(viewModel)
        Spacer(modifier = Modifier.height(16.dp))
        RecommendationArea(viewModel) { showManualAddDialog = true }
        Spacer(modifier = Modifier.weight(1f))
        Keypad(viewModel)
        AdPlaceholder()
    }

    if (showManualAddDialog) {
        ManualAddDialog(
            onDismiss = { showManualAddDialog = false },
            onConfirm = { itemName ->
                viewModel.addTransaction(itemName)
                showManualAddDialog = false
            }
        )
    }
}

@Composable
fun InputDisplay(viewModel: AppViewModel) {
    val inputValue by viewModel.inputValue.observeAsState("0")
    val transactionType by viewModel.transactionType.observeAsState(TransactionType.EXPENSE)
    val amount = inputValue.toLongOrNull() ?: 0
    val formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(amount)

    val color = when (transactionType) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.primary
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
    }
    val prefix = if (transactionType == TransactionType.INCOME) "+" else "-"

    Text(
        text = "$prefix $formattedAmount",
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End
    )
}

@Composable
fun RecommendationArea(viewModel: AppViewModel, onManualAddClick: () -> Unit) {
    val recommendedItems by viewModel.recommendedItems.observeAsState(emptyList())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        recommendedItems.forEach { item ->
            Button(
                onClick = { viewModel.addTransaction(item.name) },
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(item.name)
            }
        }
        Button(
            onClick = onManualAddClick,
            modifier = Modifier.padding(horizontal = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(stringResource(R.string.manual_add))
        }
    }
}

@Composable
fun Keypad(viewModel: AppViewModel) {
    val keys = listOf(
        "1", "2", "3",
        "4", "5", "6",
        "7", "8", "9",
        "+/-", "0", "C",
        "00", "000"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        keys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowKeys.forEach { key ->
                    KeypadButton(
                        text = key,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.onKeypadClick(key) }
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_item_name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.item_name)) }
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AdPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.ad_placeholder),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReceiptsScreen(viewModel: AppViewModel) {
    val transactions by viewModel.transactions.observeAsState(emptyList())
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    Column(Modifier.fillMaxSize()) {
        SummaryBar(totalIncome, totalExpense)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions, key = { it.id }) { transaction ->
                TransactionItem(transaction)
            }
        }
    }
}

@Composable
fun SummaryBar(income: Long, expense: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.total_income), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatCurrency(income),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.total_expense), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatCurrency(expense),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction) {
    val color = if (transaction.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val sign = if (transaction.type == TransactionType.INCOME) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(transaction.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "$sign${formatCurrency(transaction.amount)}",
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatCurrency(amount: Long): String {
    return NumberFormat.getCurrencyInstance(Locale.KOREA).format(amount).replace("₩", "")
}

@Composable
fun DictionaryEditorScreen(viewModel: AppViewModel) {
    val dictionary by viewModel.recommendationDictionary.observeAsState(emptyList())
    var showEditDialog by remember { mutableStateOf<RecommendationItem?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { showEditDialog = RecommendationItem(name = "", minPrice = 0, maxPrice = 0) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_new_item))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dictionary, key = { it.id }) { item ->
                DictionaryItem(
                    item = item,
                    onEdit = { showEditDialog = item },
                    onDelete = { viewModel.deleteDictionaryItem(item) }
                )
            }
        }
    }

    showEditDialog?.let { item ->
        EditDictionaryItemDialog(
            item = item,
            onDismiss = { showEditDialog = null },
            onSave = { updatedItem ->
                if (dictionary.any { it.id == updatedItem.id }) {
                    viewModel.updateDictionaryItem(updatedItem)
                } else {
                    viewModel.addDictionaryItem(updatedItem)
                }
                showEditDialog = null
            }
        )
    }
}


@Composable
fun DictionaryItem(item: RecommendationItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("${formatCurrency(item.minPrice)} ~ ${formatCurrency(item.maxPrice)}", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}


@Composable
fun EditDictionaryItemDialog(
    item: RecommendationItem,
    onDismiss: () -> Unit,
    onSave: (RecommendationItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var minPrice by remember { mutableStateOf(item.minPrice.toString()) }
    var maxPrice by remember { mutableStateOf(item.maxPrice.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.add_new_item), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.item_name)) }
                )
                OutlinedTextField(
                    value = minPrice,
                    onValueChange = { minPrice = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.min_price)) }
                )
                OutlinedTextField(
                    value = maxPrice,
                    onValueChange = { maxPrice = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.max_price)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(
                            item.copy(
                                name = name,
                                minPrice = minPrice.toLongOrNull() ?: 0,
                                maxPrice = maxPrice.toLongOrNull() ?: 0
                            )
                        )
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

// --- Preview ---
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        AppContent(viewModel())
    }
}


// --- 데이터 저장을 전문적으로 담당하는 클래스 ---
// (이전 설명에서는 별도 파일이었지만, 이해하기 쉽게 한 파일에 합쳤습니다.)
class DataRepository(context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("my_app_preferences", Context.MODE_PRIVATE)

    // --- 거래 내역(Transaction) 관련 ---

    fun saveTransactions(transactions: List<Transaction>) {
        val jsonString = gson.toJson(transactions)
        prefs.edit().putString("KEY_TRANSACTIONS", jsonString).apply()
    }

    fun loadTransactions(): List<Transaction> {
        val jsonString = prefs.getString("KEY_TRANSACTIONS", null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<Transaction>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            emptyList() // 저장된 데이터가 없으면 빈 목록 반환
        }
    }

    // --- 추천 항목(RecommendationItem) 관련 ---

    fun saveRecommendationItems(items: List<RecommendationItem>) {
        val jsonString = gson.toJson(items)
        prefs.edit().putString("KEY_RECOMMENDATIONS", jsonString).apply()
    }

    fun loadRecommendationItems(): List<RecommendationItem> {
        val jsonString = prefs.getString("KEY_RECOMMENDATIONS", null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<RecommendationItem>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            // 저장된 데이터가 없을 때만 기본 샘플 데이터를 제공
            listOf(
                RecommendationItem(name = "커피", minPrice = 3000, maxPrice = 6000),
                RecommendationItem(name = "점심", minPrice = 8000, maxPrice = 15000),
                RecommendationItem(name = "교통비", minPrice = 1500, maxPrice = 3000),
                RecommendationItem(name = "영화 티켓", minPrice = 15000, maxPrice = 20000),
                RecommendationItem(name = "편의점", minPrice = 1000, maxPrice = 10000),
                RecommendationItem(name = "저녁 식사", minPrice = 15000, maxPrice = 30000),
                RecommendationItem(name = "책", minPrice = 10000, maxPrice = 25000),
                RecommendationItem(name = "음료수", minPrice = 1000, maxPrice = 2500),
                RecommendationItem(name = "택시", minPrice = 4800, maxPrice = 50000),
                RecommendationItem(name = "월급", minPrice = 2000000, maxPrice = 5000000)
            )
        }
    }
}