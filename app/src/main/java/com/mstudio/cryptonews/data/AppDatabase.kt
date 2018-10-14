/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package com.mstudio.cryptonews.data

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import android.arch.persistence.room.migration.Migration
import android.content.Context
import com.mstudio.cryptonews.data.dao.EntryDao
import com.mstudio.cryptonews.data.dao.FeedDao
import com.mstudio.cryptonews.data.dao.TaskDao
import com.mstudio.cryptonews.data.entities.Entry
import com.mstudio.cryptonews.data.entities.Feed
import com.mstudio.cryptonews.data.entities.Task
import org.jetbrains.anko.doAsync


@Database(entities = [Feed::class, Entry::class, Task::class], version = 3)
@TypeConverters(com.mstudio.cryptonews.data.converters.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        private const val DATABASE_NAME = "db"

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.run {
                    execSQL("DELETE FROM entries WHERE rowid NOT IN (SELECT MIN(rowid) FROM entries GROUP BY link)")
                    execSQL("CREATE UNIQUE INDEX index_entries_link ON entries(link)")
                }
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.run {
                    execSQL("DROP TRIGGER feed_update_decrease_priority")
                    execSQL("DROP TRIGGER feed_update_increase_priority")
                    execSQL("DROP TRIGGER feed_update_decrease_priority_same_group")
                    execSQL("DROP TRIGGER feed_update_increase_priority_same_group")
                    execSQL("""
                                CREATE TRIGGER feed_update_priority_group
                                    AFTER UPDATE
                                    ON feeds
                                    WHEN NOT(OLD.groupId IS NEW.groupId) OR NEW.displayPriority != OLD.displayPriority
                                BEGIN
                                    UPDATE feeds SET displayPriority = (SELECT COUNT() FROM feeds f JOIN feeds fl ON fl.displayPriority <= f.displayPriority AND fl.groupId IS f.groupId WHERE f.feedId = feeds.feedId GROUP BY f.feedId) WHERE feedId != NEW.feedId;
                                    UPDATE feeds SET displayPriority = (SELECT COUNT() + 1 FROM feeds f WHERE f.displayPriority < NEW.displayPriority AND f.groupId IS NEW.groupId ) WHERE feedId = NEW.feedId;
                                END;
                                """)
                }
            }
        }

        fun createDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)

                            doAsync {
                                // insert => add max priority for the group
                                db.execSQL("""
                                    CREATE TRIGGER feed_insert_priority
                                        AFTER INSERT
                                        ON feeds
                                    BEGIN
                                       UPDATE feeds SET displayPriority = IFNULL((SELECT MAX(displayPriority) FROM feeds WHERE groupId IS NEW.groupId), 0) + 1 WHERE feedId = NEW.feedId;
                                    END;
                                    """)

                                // update priority of some group's feeds
                                db.execSQL("""
                                    CREATE TRIGGER feed_update_priority_group
                                        AFTER UPDATE
                                        ON feeds
                                        WHEN NOT(OLD.groupId IS NEW.groupId) OR NEW.displayPriority != OLD.displayPriority
                                    BEGIN
                                        UPDATE feeds SET displayPriority = (SELECT COUNT() FROM feeds f JOIN feeds fl ON fl.displayPriority <= f.displayPriority AND fl.groupId IS f.groupId WHERE f.feedId = feeds.feedId GROUP BY f.feedId) WHERE feedId != NEW.feedId;
                                        UPDATE feeds SET displayPriority = (SELECT COUNT() + 1 FROM feeds f WHERE f.displayPriority < NEW.displayPriority AND f.groupId IS NEW.groupId ) WHERE feedId = NEW.feedId;
                                    END;
                                    """)
                            }
                        }
                    })
                    .build()
        }
    }

    abstract fun feedDao(): FeedDao
    abstract fun entryDao(): EntryDao
    abstract fun taskDao(): TaskDao
}