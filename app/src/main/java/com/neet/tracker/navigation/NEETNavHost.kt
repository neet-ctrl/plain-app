package com.neet.tracker.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neet.tracker.ui.screens.*
import java.net.URLDecoder

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NEETNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        }
    ) {
        composable(Routes.HOME) { HomeScreen(navController) }
        composable(Routes.PROFILE) { ProfileScreen(navController) }

        // Assets
        composable(Routes.ASSETS) { AssetsScreen(navController) }
        composable(Routes.NOTEBOOKS) { NotebooksScreen(navController) }
        composable(
            Routes.NOTEBOOK_CHAPTERS,
            arguments = listOf(
                navArgument("notebookId") { type = NavType.StringType },
                navArgument("notebookNo") { type = NavType.StringType }
            )
        ) { backStack ->
            val nbId = backStack.arguments?.getString("notebookId") ?: ""
            val nbNo = backStack.arguments?.getString("notebookNo") ?: ""
            NotebookChaptersScreen(navController, nbId, nbNo)
        }
        composable(Routes.BOOKS) { BooksScreen(navController) }
        composable(Routes.PYQ) { PYQScreen(navController) }
        composable(Routes.PYQ_CHAPTERWISE) { PYQChapterwiseScreen(navController) }
        composable(
            Routes.PYQ_CHAPTERWISE_DETAIL,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("sourceName") { type = NavType.StringType }
            )
        ) { backStack ->
            val srcId = backStack.arguments?.getString("sourceId") ?: ""
            val srcName = backStack.arguments?.getString("sourceName") ?: ""
            PYQChapterwiseDetailScreen(navController, srcId, srcName)
        }
        composable(Routes.PYQ_YEARWISE) { PYQYearwiseScreen(navController) }
        composable(
            Routes.PYQ_YEARWISE_DETAIL,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookName") { type = NavType.StringType }
            )
        ) { backStack ->
            val bookId = backStack.arguments?.getString("bookId") ?: ""
            val bookName = backStack.arguments?.getString("bookName") ?: ""
            PYQYearwiseDetailScreen(navController, bookId, bookName)
        }
        composable(Routes.TEST_PAPERS) { TestPapersScreen(navController) }
        composable(Routes.ONLINE_TESTS) { OnlineTestsScreen(navController) }
        composable(Routes.OFFLINE_TESTS) { OfflineTestsScreen(navController) }
        composable(Routes.SAMPLE_PAPERS) { SamplePapersScreen(navController) }
        composable(Routes.PW_BATCHES) { PWBatchesScreen(navController) }
        composable(
            Routes.PW_BATCH_TESTS,
            arguments = listOf(
                navArgument("batchId") { type = NavType.StringType },
                navArgument("batchName") { type = NavType.StringType }
            )
        ) { backStack ->
            val batchId = backStack.arguments?.getString("batchId") ?: ""
            val batchName = backStack.arguments?.getString("batchName") ?: ""
            PWBatchTestsScreen(navController, batchId, batchName)
        }

        // Planner
        composable(Routes.PLANNER) { PlannerScreen(navController) }
        composable(Routes.DAY_PLANNER) { DayPlannerScreen(navController) }
        composable(
            Routes.DAY_PLANNER_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStack ->
            DayPlannerDetailScreen(navController, backStack.arguments?.getString("date") ?: "")
        }
        composable(Routes.WEEK_PLANNER) { WeekPlannerScreen(navController) }
        composable(
            Routes.WEEK_PLANNER_DETAIL,
            arguments = listOf(navArgument("weekId") { type = NavType.StringType })
        ) { backStack ->
            WeekPlannerDetailScreen(navController, backStack.arguments?.getString("weekId") ?: "")
        }
        composable(Routes.MONTH_PLANNER) { MonthPlannerScreen(navController) }
        composable(
            Routes.MONTH_PLANNER_DETAIL,
            arguments = listOf(navArgument("monthId") { type = NavType.StringType })
        ) { backStack ->
            MonthPlannerDetailScreen(navController, backStack.arguments?.getString("monthId") ?: "")
        }
        composable(Routes.YEAR_PLANNER) { YearPlannerScreen(navController) }
        composable(
            Routes.YEAR_PLANNER_DETAIL,
            arguments = listOf(navArgument("yearId") { type = NavType.StringType })
        ) { backStack ->
            YearPlannerDetailScreen(navController, backStack.arguments?.getString("yearId") ?: "")
        }

        // Other
        composable(Routes.DAILY_DIARY) { DailyDiaryScreen(navController) }
        composable(
            Routes.DIARY_ENTRY,
            arguments = listOf(navArgument("diaryId") { type = NavType.StringType })
        ) { backStack ->
            DiaryEntryScreen(navController, backStack.arguments?.getString("diaryId") ?: "")
        }
        composable(Routes.DATE_EVENTS) { DateEventsScreen(navController) }
        composable(
            Routes.DATE_EVENT_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStack ->
            val date = URLDecoder.decode(backStack.arguments?.getString("date") ?: "", "UTF-8")
            DateEventDetailScreen(navController, date)
        }
        composable(Routes.NEET_SYLLABUS) { NEETSyllabusScreen(navController) }
        composable(Routes.DICTIONARY) { DictionaryScreen(navController) }
        composable(Routes.DICTIONARY_NEET) { DictionaryNeetScreen(navController) }
        composable(Routes.DICTIONARY_NON_NEET) { DictionaryNonNeetScreen(navController) }
        composable(Routes.MNEMONICS) { MnemonicsScreen(navController) }
        composable(Routes.UNIVERSAL_CALENDAR) { UniversalCalendarScreen(navController) }
        composable(Routes.DIAGRAMS) { DiagramsScreen(navController) }
        composable(
            Routes.DIAGRAMS_SUBJECT,
            arguments = listOf(navArgument("subject") { type = NavType.StringType })
        ) { backStack ->
            DiagramsSubjectScreen(navController, backStack.arguments?.getString("subject") ?: "BOTANY")
        }
        composable(Routes.CHAPTER_SHORT_NOTES) { ChapterShortNotesScreen(navController) }
        composable(
            Routes.CHAPTER_SHORT_NOTES_SUBJECT,
            arguments = listOf(navArgument("subject") { type = NavType.StringType })
        ) { backStack ->
            ChapterShortNotesSubjectScreen(navController, backStack.arguments?.getString("subject") ?: "")
        }
        composable(Routes.DAY_WASTE) { DayWasteScreen(navController) }
        composable(Routes.NEET_SEQUENCE) { NeetSequenceScreen(navController) }
        composable(Routes.SUBJECT_SHORT_NOTES) { SubjectShortNotesScreen(navController) }
        composable(Routes.LACK_POINTS) { LackPointsScreen(navController) }

        composable(
            Routes.FILE_VIEWER,
            arguments = listOf(
                navArgument("encodedUri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStack ->
            val uri = URLDecoder.decode(backStack.arguments?.getString("encodedUri") ?: "", "UTF-8")
            val title = URLDecoder.decode(backStack.arguments?.getString("title") ?: "", "UTF-8")
            FileViewerScreen(navController, uri, title)
        }

        // All specialized viewers route through the single universal FileViewerScreen
        composable(
            Routes.DIAGRAM_VIEWER,
            arguments = listOf(
                navArgument("subject") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("encodedUri") { type = NavType.StringType }
            )
        ) { backStack ->
            val title   = URLDecoder.decode(backStack.arguments?.getString("title") ?: "", "UTF-8")
            val fileUri = URLDecoder.decode(backStack.arguments?.getString("encodedUri") ?: "", "UTF-8")
            FileViewerScreen(navController, fileUri, title)
        }

        composable(
            Routes.SHORT_NOTE_VIEWER,
            arguments = listOf(
                navArgument("subject") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("encodedUri") { type = NavType.StringType }
            )
        ) { backStack ->
            val title   = URLDecoder.decode(backStack.arguments?.getString("title") ?: "", "UTF-8")
            val fileUri = URLDecoder.decode(backStack.arguments?.getString("encodedUri") ?: "", "UTF-8")
            FileViewerScreen(navController, fileUri, title)
        }

        composable(
            Routes.SUBJECT_NOTE_VIEWER,
            arguments = listOf(
                navArgument("subject") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("encodedUri") { type = NavType.StringType }
            )
        ) { backStack ->
            val title   = URLDecoder.decode(backStack.arguments?.getString("title") ?: "", "UTF-8")
            val fileUri = URLDecoder.decode(backStack.arguments?.getString("encodedUri") ?: "", "UTF-8")
            FileViewerScreen(navController, fileUri, title)
        }
    }
}
