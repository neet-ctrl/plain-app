package com.neet.tracker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.neet.tracker.data.models.*

@Database(
    entities = [
        StudentProfile::class,
        Notebook::class, NotebookChapter::class,
        Book::class,
        PYQSource::class, PYQChapter::class, PYQYear::class,
        TestPaper::class, SamplePaper::class,
        PWBatch::class, PWTest::class,
        DayPlannerEntry::class, WeekPlannerEntry::class,
        MonthPlannerEntry::class, YearPlannerEntry::class,
        DailyDiary::class,
        DateEvent::class,
        DictionaryNeet::class, DictionaryNonNeet::class,
        Mnemonic::class,
        Diagram::class,
        ChapterShortNote::class,
        DayWaste::class,
        NeetSequence::class,
        SubjectShortNote::class,
        LackPoint::class,
        NEETSyllabus::class,
        NeetSequencePdf::class,
        Reminder::class,
        ErrorEntry::class,
        RevisionItem::class,
        FlashcardProgress::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NEETDatabase : RoomDatabase() {
    abstract fun dao(): NEETDao
}
