import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.time.Instant
import java.util.*

interface UserRepository {
    suspend fun getUser(): UserData
}

interface NewsRepository {
    suspend fun getNews(): List<News>
}

data class UserData(val name: String)
data class News(val date: Date)

interface LiveData<T> {
    val value: T?
}

class MutableLiveData<T> : LiveData<T> {
    override var value: T? = null
}

abstract class ViewModel()

class MainViewModel(
    private val userRepo: UserRepository,
    private val newsRepo: NewsRepository,
) : BaseViewModel() {

    private val _userName: MutableLiveData<String> =
        MutableLiveData()
    val userName: LiveData<String> = _userName

    private val _news: MutableLiveData<List<News>> =
        MutableLiveData()
    val news: LiveData<List<News>> = _news

    private val _progressVisible: MutableLiveData<Boolean> =
        MutableLiveData()
    val progressVisible: LiveData<Boolean> =
        _progressVisible

    fun onCreate() {
        scope.launch {
            val user = userRepo.getUser()
            _userName.value = user.name
        }
        scope.launch {
            _progressVisible.value = true
            val news = newsRepo.getNews()
                .sortedByDescending { it.date }
            _news.value = news
            _progressVisible.value = false
        }
    }
}

abstract class BaseViewModel : ViewModel() {
    private val context = Dispatchers.Main + SupervisorJob()
    val scope = CoroutineScope(context)

    fun onDestroy() {
        context.cancelChildren()
    }
}

private val date1 = Date
    .from(Instant.now().minusSeconds(10))
private val date2 = Date
    .from(Instant.now().minusSeconds(20))
private val date3 = Date
    .from(Instant.now().minusSeconds(30))
private val testDispatcher = TestCoroutineDispatcher()
private val aName = "Some name"
private val someNews =
    listOf(News(date3), News(date1), News(date2))

class MainViewModelTests {
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(
            userRepo = FakeUserRepository(aName),
            newsRepo = FakeNewsRepository(someNews)
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        viewModel.onDestroy()
    }

    @Test
    fun `should show user name and sorted news`() {
        // when
        viewModel.onCreate()
        testDispatcher.advanceUntilIdle()

        // then
        assertEquals(aName, viewModel.userName.value)
        val someNewsSorted =
            listOf(News(date1), News(date2), News(date3))
        assertEquals(someNewsSorted, viewModel.news.value)
    }

    @Test
    fun `should show progress bar when loading news`() {
        // given
        assertEquals(null, viewModel.progressVisible.value)

        // when
        viewModel.onCreate()

        // then
        assertEquals(true, viewModel.progressVisible.value)

        // when
        testDispatcher.advanceTimeBy(200)

        // then
        assertEquals(false, viewModel.progressVisible.value)
    }

    @Test
    fun `user and news are called concurrently`() {
        // when
        viewModel.onCreate()

        testDispatcher.advanceUntilIdle()

        // then
        assertEquals(300, testDispatcher.currentTime)
    }

    class FakeUserRepository(
        private val name: String
    ) : UserRepository {
        override suspend fun getUser(): UserData {
            delay(300)
            return UserData(name)
        }
    }

    class FakeNewsRepository(
        private val news: List<News>
    ) : NewsRepository {
        override suspend fun getNews(): List<News> {
            delay(200)
            return news
        }
    }
}

@ExperimentalCoroutinesApi
class CoroutinesTestExtension(
    val scheduler: TestCoroutineScheduler =
        TestCoroutineScheduler(),
    val dispatcher: TestDispatcher =
        StandardTestDispatcher(scheduler),
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}